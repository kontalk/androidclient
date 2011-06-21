package org.nuntius.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.nuntius.R;
import org.nuntius.authenticator.Authenticator;
import org.nuntius.client.AbstractMessage;
import org.nuntius.client.EndpointServer;
import org.nuntius.client.ImageMessage;
import org.nuntius.client.MessageSender;
import org.nuntius.client.PlainTextMessage;
import org.nuntius.client.ReceiptMessage;
import org.nuntius.client.StatusResponse;
import org.nuntius.provider.MessagesProvider;
import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.ui.ConversationList;
import org.nuntius.ui.MessagingNotification;
import org.nuntius.ui.MessagingPreferences;
import org.nuntius.util.MediaStorage;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;


/**
 * The Message Center Service.
 * This service manages the polling of incoming messages - broadcasting them
 * using broadcast intents - and also the outgoing messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageCenterService extends Service
        implements MessageListener, RequestListener {

    private static final String TAG = MessageCenterService.class.getSimpleName();
    private static final int NOTIFICATION_ID = 102;

    public static final String MESSAGE_RECEIVED = "org.nuntius.MESSAGE_RECEIVED";
    private static final String ACTION_PAUSE = "org.nuntius.PAUSE_MESSAGE_CENTER";

    private Notification mCurrentNotification;
    private long mTotalBytes;

    private PollingThread mPollingThread;
    private RequestWorker mRequestWorker;
    private Account mAccount;

    /**
     * This list will contain the received messages - avoiding multiple
     * received jobs.
     */
    private List<String> mReceived = new ArrayList<String>();

    private AccountManager mAccountManager;
    private final OnAccountsUpdateListener mAccountsListener = new OnAccountsUpdateListener() {
        @Override
        public void onAccountsUpdated(Account[] accounts) {
            Log.w(TAG, "accounts have been changed, checking");

            // restart workers
            Account my = null;
            for (int i = 0; i < accounts.length; i++) {
                if (accounts[i].type.equals(Authenticator.ACCOUNT_TYPE)) {
                    my = accounts[i];
                    break;
                }
            }

            // account removed!!! Shutdown everything.
            if (my == null) {
                Log.e(TAG, "my account has been removed, shutting down");
                // delete all messages
                MessagesProvider.deleteDatabase(MessageCenterService.this);
                stopSelf();
            }
        }
    };

    private MessageRequestListener mMessageRequestListener; // created in onCreate

    private final IBinder mBinder = new MessageCenterInterface();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Compatibility with Android 1.6
     */
    @Override
    public void onStart(Intent intent, int startId) {
        onStartCommand(intent, 0, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.w(TAG, "Message Center starting - " + intent);

        if (intent != null) {
            // pause
            if (ACTION_PAUSE.equals(intent.getAction())) {
                pause();
            }

            // normal start
            else {
                Bundle extras = intent.getExtras();
                String serverUrl = (String) extras.get(EndpointServer.class.getName());
                Log.i(TAG, "using server uri: " + serverUrl);
                EndpointServer server = new EndpointServer(serverUrl);

                mAccount = Authenticator.getDefaultAccount(this);
                if (mAccount == null) {
                    stopSelf();
                }
                else {
                    // check changing accounts
                    if (mAccountManager == null) {
                        mAccountManager = AccountManager.get(this);
                        mAccountManager.addOnAccountsUpdatedListener(mAccountsListener, null, true);
                    }

                    // activate request worker if necessary
                    if (mRequestWorker == null) {
                        mRequestWorker = new RequestWorker(this, server);
                        mRequestWorker.addListener(this);
                        mRequestWorker.start();

                        // lookup for messages with error status and try to re-send them
                        requeuePendingMessages();
                        // lookup for incoming messages not confirmed yet
                        requeuePendingReceipts();
                    }
                    else {
                        mRequestWorker.resume2();
                    }

                    // start polling thread if needed
                    if (mPollingThread == null) {
                        mPollingThread = new PollingThread(this, server);
                        mPollingThread.setMessageListener(this);
                        mPollingThread.start();
                    }
                }
            }
        }

        return START_STICKY;
    }

    /**
     * Shuts down the polling thread.
     * @return true if the thread has been stopped, false if it wasn't running.
     */
    private boolean shutdownPollingThread() {
        if (mPollingThread != null) {
            PollingThread tmp = mPollingThread;
            // discard the reference to the thread immediately
            mPollingThread = null;
            tmp.shutdown();
            return true;
        }
        return false;
    }

    /**
     * Shuts down the request worker.
     * @return true if the thread has been stopped, false if it wasn't running.
     */
    private boolean shutdownRequestWorker() {
        // Be sure to clear the pending jobs queue.
        // Since we are stopping the message center, any pending request would
        // be lost anyway.
        RequestWorker.pendingJobs.clear();

        if (mRequestWorker != null) {
            RequestWorker tmp = mRequestWorker;
            // discard the reference to the thread immediately
            mRequestWorker = null;
            tmp.shutdown();
            return true;
        }
        return false;
    }

    /**
     * Searches for messages with error or pending status and pushes them
     * through the request queue to re-send them.
     */
    private void requeuePendingMessages() {
        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
                new String[] {
                    Messages._ID,
                    Messages.PEER,
                    Messages.CONTENT,
                    Messages.MIME
                },
                Messages.DIRECTION + " = " + Messages.DIRECTION_OUT + " AND " +
                Messages.STATUS + " <> " + Messages.STATUS_SENT + " AND " +
                Messages.STATUS + " <> " + Messages.STATUS_RECEIVED,
                null, null);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String userId = c.getString(1);
            String text = c.getString(2);
            String mime = c.getString(3);
            Uri uri = ContentUris.withAppendedId(Messages.CONTENT_URI, id);

            MessageSender m = new MessageSender(userId, text.getBytes(), mime, uri);
            m.setListener(mMessageRequestListener);
            Log.i(TAG, "resending failed message " + id);
            sendMessage(m);
        }

        c.close();
    }

    /**
     * Searches for incoming messages not yet confirmed and send a received
     * notification through the request queue.
     */
    private void requeuePendingReceipts() {
        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
                new String[] { Messages.MESSAGE_ID },
                Messages.DIRECTION + " = " + Messages.DIRECTION_IN + " AND " +
                Messages.STATUS + " IS NULL",
                null, null);

        List<NameValuePair> list = new ArrayList<NameValuePair>();
        while (c.moveToNext()) {
            String msgId = c.getString(0);
            Log.i(TAG, "sending received notification for message " + msgId);
            list.add(new BasicNameValuePair("i[]", msgId));
        }
        c.close();

        if (list.size() > 0) {
            // here we send the received notification
            RequestJob job = new RequestJob("received", list);
            pushRequest(job);
        }
    }

    @Override
    public void onCreate() {
        mMessageRequestListener = new MessageRequestListener(this);
    }

    @Override
    public void onDestroy() {
        if (mAccountManager != null) {
            mAccountManager.removeOnAccountsUpdatedListener(mAccountsListener);
            mAccountManager = null;
        }

        // stop polling thread
        shutdownPollingThread();

        // stop request worker
        shutdownRequestWorker();
    }

    @Override
    public void incoming(List<AbstractMessage<?>> messages) {
        List<NameValuePair> list = new ArrayList<NameValuePair>();

        // access to mReceived list is protected
        synchronized (mReceived) {
            for (AbstractMessage<?> msg : messages) {
                if (!mReceived.contains(msg.getId())) {
                    // the message need to be confirmed
                    list.add(new BasicNameValuePair("i[]", msg.getId()));
                    mReceived.add(msg.getId());

                    // do not store receipts...
                    if (!(msg instanceof ReceiptMessage)) {
                        // store to file if it's an image message
                        String content;
                        if (msg instanceof ImageMessage) {
                            ImageMessage imgMsg = (ImageMessage) msg;
                            String filename = ImageMessage.buildMediaFilename(msg.getId(), msg.getMime());
                            File file = null;
                            try {
                                file = MediaStorage.writeMedia(filename, imgMsg.getDecodedContent());
                            }
                            catch (IOException e) {
                                Log.e(TAG, "unable to write to media storage", e);
                            }
                            // update uri
                            Uri uri = Uri.fromFile(file);
                            msg.setLocalUri(uri);
                            content = uri.toString();
                        }
                        else {
                            content = msg.getTextContent();
                        }

                        // save to local storage
                        ContentValues values = new ContentValues();
                        values.put(Messages.MESSAGE_ID, msg.getId());
                        values.put(Messages.PEER, msg.getSender());
                        values.put(Messages.MIME, msg.getMime());
                        values.put(Messages.CONTENT, content);
                        values.put(Messages.FETCH_URL, msg.getFetchUrl());
                        values.put(Messages.UNREAD, true);
                        values.put(Messages.DIRECTION, Messages.DIRECTION_IN);
                        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                        getContentResolver().insert(Messages.CONTENT_URI, values);
                    }

                    // we have a receipt, update the corresponding message
                    else {
                        ReceiptMessage msg2 = (ReceiptMessage) msg;
                        Log.w(TAG, "receipt for message " + msg2.getMessageId());

                        MessagesProvider.changeMessageStatus(this,
                                msg2.getMessageId(), Messages.STATUS_RECEIVED,
                                msg.getServerTimestamp().getTime());
                    }

                    // broadcast message
                    broadcastMessage(msg);
                    // update notifications
                    MessagingNotification.updateMessagesNotification(getApplicationContext(), true);
                }
            }
        }

        if (list.size() > 0) {
            Log.w(TAG, "pushing receive confirmation");
            RequestJob job = new RequestJob("received", list);
            pushRequest(job);
        }
    }

    private synchronized void pushRequest(final RequestJob job) {
        if (mRequestWorker != null)
            mRequestWorker.push(job);
        else {
            Log.w(TAG, "request worker is down, queueing job");
            RequestWorker.pendingJobs.add(job);
        }
    }

    /** Sends a message using the request worker. */
    public void sendMessage(final MessageSender job) {
        // global listener
        job.setListener(mMessageRequestListener);

        // not a simple text message - use progress notification
        if (!PlainTextMessage.MIME_TYPE.equals(job.getMime())) {
            try {
                startForeground(job.getContentLength(this));
            }
            catch (IOException e) {
                Log.e(TAG, "error reading message to send", e);
                // FIXME just don't send for now
                return;
            }
        }

        pushRequest(job);
    }

    public void startForeground(long totalBytes) {
        Log.w(TAG, "starting foreground progress notification");
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ConversationList.class);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), NOTIFICATION_ID, ni, Intent.FLAG_ACTIVITY_NEW_TASK);

        mCurrentNotification = new Notification(R.drawable.icon, "Sending message...", System.currentTimeMillis());
        mCurrentNotification.contentIntent = pi;
        mCurrentNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        foregroundNotification(0);
        startForeground(NOTIFICATION_ID, mCurrentNotification);
    }

    private void foregroundNotification(int progress) {
        mCurrentNotification.contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.progress_notification);
        mCurrentNotification.contentView.setImageViewResource(R.id.status_icon, R.drawable.icon);
        mCurrentNotification.contentView.setTextViewText(R.id.status_text, "Sending message...");
        mCurrentNotification.contentView.setProgressBar(R.id.status_progress, 100, progress, false);
    }

    public void publishProgress(long bytes) {
        if (mCurrentNotification != null) {
            int progress = (int)((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, mCurrentNotification);
        }
    }

    public void stopForeground() {
        stopForeground(true);
        mCurrentNotification = null;
        mTotalBytes = 0;
    }

    @Override
    public void response(RequestJob job, List<StatusResponse> statuses) {
        Log.w(TAG, "job=" + job + ", statuses=" + statuses);
        // stop foreground if any
        stopForeground();

        // received command
        if (statuses != null && "received".equals(job.getCommand())) {
            // access to mReceived list is protected
            synchronized (mReceived) {
                // single status - retrieve message id from request
                if (statuses.size() == 1) {
                    List<NameValuePair> params = job.getParams();
                    for (NameValuePair par : params) {
                        if ("i".equals(par.getName()) || "i[]".equals(par.getName())) {
                            mReceived.remove(par.getValue());
                            MessagesProvider.changeMessageStatus(this,
                                    par.getValue(), Messages.STATUS_CONFIRMED);
                        }
                    }
                }
                // multiple statuses - each status has its own message id
                else {
                    for (StatusResponse st : statuses) {
                        if (st.extra != null) {
                            String idToRemove = (String) st.extra.get("i");
                            if (idToRemove != null) {
                                mReceived.remove(idToRemove);
                                MessagesProvider.changeMessageStatus(this,
                                        idToRemove, Messages.STATUS_CONFIRMED);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean error(RequestJob job, Throwable e) {
        // TODO ehm :)
        Log.e(TAG, "request error", e);
        // stop foreground if any
        stopForeground();

        return true;
    }

    @Override
    public void uploadProgress(long bytes) {
        Log.i(TAG, "bytes sent: " + bytes);
        publishProgress(bytes);
    }

    @Override
    public void downloadProgress(long bytes) {
        // TODO ehm :)
        Log.i(TAG, "bytes received: " + bytes);
    }

    private void broadcastMessage(AbstractMessage<?> message) {
        Intent msg = new Intent(MESSAGE_RECEIVED);
        msg.putExtras(message.toBundle());
        sendBroadcast(msg);
    }

    /** Starts the message center. */
    public static void startMessageCenter(final Context context) {
        // check for network state
        final ConnectivityManager cm = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm.getBackgroundDataSetting()) {
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.getState() == NetworkInfo.State.CONNECTED) {
                Log.i(TAG, "starting message center");
                final Intent intent = new Intent(context, MessageCenterService.class);

                // get the URI from the preferences
                String uri = MessagingPreferences.getServerURI(context);
                intent.putExtra(EndpointServer.class.getName(), uri);
                context.startService(intent);
            }
            else
                Log.w(TAG, "network not available - abort service start");
        }
        else
            Log.w(TAG, "background data disabled - abort service start");
    }

    /** Stops the message center. */
    public static void stopMessageCenter(final Context context) {
        Log.i(TAG, "stopping message center");
        context.stopService(new Intent(context, MessageCenterService.class));
    }

    /** Shutdown the polling thread and pause the request worker. */
    public static void pauseMessageCenter(final Context context) {
        Log.i(TAG, "pausing message center");
        final Intent intent = new Intent(context, MessageCenterService.class);
        intent.setAction(ACTION_PAUSE);
        context.startService(intent);
    }

    public final class MessageCenterInterface extends Binder {
        public MessageCenterService getService() {
            return MessageCenterService.this;
        }
    }

    /** Pauses the message center. */
    public void pause() {
        if (mRequestWorker != null) {
            // Clear the pending jobs queue.
            // This way messages with error status will not be sent twice.
            RequestWorker.pendingJobs.clear();
            // pause the request worker
            mRequestWorker.pause();
        }

        // polling thread should be restarted, so we destroy it
        shutdownPollingThread();
    }

}

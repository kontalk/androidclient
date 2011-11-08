package org.kontalk.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.AbstractMessage;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ImageMessage;
import org.kontalk.client.MessageSender;
import org.kontalk.client.Protocol;
import org.kontalk.client.ReceiptMessage;
import org.kontalk.client.ReceivedJob;
import org.kontalk.client.RequestClient;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MediaStorage;

import com.google.protobuf.MessageLite;

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

    public static final String C2DM_REGISTERED = "org.kontalk.C2DM_REGISTERED";
    public static final String MESSAGE_RECEIVED = "org.kontalk.MESSAGE_RECEIVED";

    public static final String C2DM_REGISTRATION_ID = "org.kontalk.C2DM_REGISTRATION_ID";

    private Notification mCurrentNotification;
    private long mTotalBytes;

    private PollingThread mPollingThread;
    private RequestWorker mRequestWorker;
    private Account mAccount;

    private boolean mPushNotifications;
    private String mPushRegistrationId;

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
     * FIXME this should probably go away since we are using 2.2 features...
     */
    @Override
    public void onStart(Intent intent, int startId) {
        onStartCommand(intent, 0, startId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.w(TAG, "Message Center starting - " + intent);

        if (intent != null) {
            String action = intent.getAction();

            // C2DM hash registered!
            if (C2DM_REGISTERED.equals(action)) {
                setPushRegistrationId(intent.getStringExtra(C2DM_REGISTRATION_ID));
            }

            // normal start
            else {

                Bundle extras = intent.getExtras();
                String serverUrl = (String) extras.get(EndpointServer.class.getName());
                Log.i(TAG, "using server uri: " + serverUrl);
                EndpointServer server = new EndpointServer(serverUrl);

                mPushNotifications = MessagingPreferences.getPushNotificationsEnabled(this);
                mAccount = Authenticator.getDefaultAccount(this);
                if (mAccount == null) {
                    stopSelf();
                }
                else {

                    // ensure application-wide lock
                    // WARNING!!! DANGEROUS HACK
                    synchronized (getApplicationContext()) {

                        // check changing accounts
                        if (mAccountManager == null) {
                            mAccountManager = AccountManager.get(this);
                            mAccountManager.addOnAccountsUpdatedListener(mAccountsListener, null, true);
                        }

                        // activate request worker
                        if (mRequestWorker == null) {
                            mRequestWorker = new RequestWorker(this, server);
                            mRequestWorker.addListener(this);
                            mRequestWorker.start();

                            // lookup for messages with error status and try to re-send them
                            requeuePendingMessages();
                            // lookup for incoming messages not confirmed yet
                            requeuePendingReceipts();
                        }

                        // start polling thread
                        if (mPollingThread == null) {
                            mPollingThread = new PollingThread(this, server);
                            mPollingThread.setMessageListener(this);
                            mPollingThread.setPushRegistrationId(mPushRegistrationId);
                            mPollingThread.start();
                        }

                        // register to push notifications
                        if (mPushNotifications) {
                            if (mPushRegistrationId != null)
                                Log.w(TAG, "already registered to C2DM");
                            else
                                c2dmRegister();
                        }
                        /*
                         * FIXME c2dm stays on since in OnDestroy() we commented
                         * the unregistration call, and here we do nothing about it
                         */
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
                    Messages.MIME,
                    Messages.LOCAL_URI,
                    Messages.ENCRYPT_KEY
                },
                Messages.DIRECTION + " = " + Messages.DIRECTION_OUT + " AND " +
                Messages.STATUS + " <> " + Messages.STATUS_SENT + " AND " +
                Messages.STATUS + " <> " + Messages.STATUS_RECEIVED,
                null, null);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String userId = c.getString(1);
            byte[] text = c.getBlob(2);
            String mime = c.getString(3);
            String _fileUri = c.getString(4);
            String key = c.getString(5);
            Uri uri = ContentUris.withAppendedId(Messages.CONTENT_URI, id);

            MessageSender m;

            // check if the message contains some large file to be sent
            if (_fileUri != null) {
                Uri fileUri = Uri.parse(_fileUri);
                // FIXME do not encrypt binary messages for now
                m = new MessageSender(userId, fileUri, mime, uri, null);
            }
            // we have a simple boring plain text message :(
            else {
                m = new MessageSender(userId, text, mime, uri, key);
            }

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
                new String[] { Messages.REAL_ID },
                Messages.DIRECTION + " = " + Messages.DIRECTION_IN + " AND " +
                Messages.STATUS + " IS NULL",
                null, null);

        List<String> list = new ArrayList<String>();
        while (c.moveToNext()) {
            String msgId = c.getString(0);
            Log.i(TAG, "sending received notification for real message " + msgId);
            list.add(msgId);
        }
        c.close();

        if (list.size() > 0) {
            // here we send the received notification
            RequestJob job = new ReceivedJob(list);
            pushRequest(job);
        }
    }

    @Override
    public void onCreate() {
        mMessageRequestListener = new MessageRequestListener(this);
    }

    @Override
    public void onDestroy() {
        // unregister push notifications
        // TEST do not unregister
        //setPushNotifications(false);

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
        List<String> list = new ArrayList<String>();

        // access to mReceived list is protected
        synchronized (mReceived) {
            boolean notify = false;

            for (AbstractMessage<?> msg : messages) {
                if (!mReceived.contains(msg.getId())) {
                    // the message need to be confirmed
                    list.add(msg.getRealId());
                    mReceived.add(msg.getId());

                    // do not store receipts...
                    if (!(msg instanceof ReceiptMessage)) {
                        // store to file if it's an image message
                        // FIXME this should be abstracted somehow
                        byte[] content = msg.getBinaryContent();

                        if (msg instanceof ImageMessage) {
                            String filename = ImageMessage.buildMediaFilename(msg.getId(), msg.getMime());
                            File file = null;
                            try {
                                file = MediaStorage.writeInternalMedia(this, filename, content);
                            }
                            catch (IOException e) {
                                Log.e(TAG, "unable to write to media storage", e);
                            }
                            // update uri
                            Uri uri = Uri.fromFile(file);
                            msg.setLocalUri(uri);

                            // use text content for database table
                            content = msg.getTextContent().getBytes();
                        }

                        // save to local storage
                        ContentValues values = new ContentValues();
                        values.put(Messages.MESSAGE_ID, msg.getId());
                        values.put(Messages.REAL_ID, msg.getRealId());
                        values.put(Messages.PEER, msg.getSender(true));
                        values.put(Messages.MIME, msg.getMime());
                        values.put(Messages.CONTENT, content);
                        values.put(Messages.ENCRYPTED, msg.isEncrypted());
                        values.put(Messages.ENCRYPT_KEY, (msg.wasEncrypted()) ? "" : null);
                        values.put(Messages.FETCH_URL, msg.getFetchUrl());
                        Uri localUri = msg.getLocalUri();
                        if (localUri != null)
                            values.put(Messages.LOCAL_URI, localUri.toString());
                        values.put(Messages.UNREAD, true);
                        values.put(Messages.DIRECTION, Messages.DIRECTION_IN);
                        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                        Uri newMsg = getContentResolver().insert(Messages.CONTENT_URI, values);
                        msg.setDatabaseId(ContentUris.parseId(newMsg));

                        // we will have to notify the user
                        notify = true;
                    }

                    // we have a receipt, update the corresponding message
                    else {
                        ReceiptMessage msg2 = (ReceiptMessage) msg;
                        Log.w(TAG, "receipt for message " + msg2.getMessageId());
                        // TODO handle error receipts

                        MessagesProvider.changeMessageStatus(this,
                                msg2.getMessageId(), false, Messages.STATUS_RECEIVED,
                                -1, msg.getServerTimestamp().getTime());
                    }

                    // broadcast message
                    broadcastMessage(msg);
                }
            }

            if (notify)
                // update notifications (delayed)
                MessagingNotification.delayedUpdateMessagesNotification(getApplicationContext(), true);
        }

        if (list.size() > 0) {
            Log.w(TAG, "pushing receive confirmation");
            RequestJob job = new ReceivedJob(list);
            pushRequest(job);
        }
    }

    private synchronized void pushRequest(final RequestJob job) {
        if (mRequestWorker != null && (mRequestWorker.isRunning() || mRequestWorker.isAlive()))
            mRequestWorker.push(job);
        else {
            if (job instanceof ReceivedJob || job instanceof MessageSender) {
                Log.w(TAG, "not queueing message job");
            }
            else {
                Log.w(TAG, "request worker is down, queueing job");
                RequestWorker.pendingJobs.add(job);
            }

            Log.w(TAG, "trying to start message center");
            startMessageCenter(getApplicationContext());
        }
    }

    /** Sends a message using the request worker. */
    public void sendMessage(final MessageSender job) {
        // global listener
        job.setListener(mMessageRequestListener);

        // not a plain text message - use progress notification
        if (job.getSourceUri() != null) {
            try {
                startForeground(job.getUserId(), job.getContentLength(this));
            }
            catch (IOException e) {
                Log.e(TAG, "error reading message data to send", e);
                // FIXME just don't send for now
                return;
            }
        }

        pushRequest(job);
    }

    public void startForeground(String userId, long totalBytes) {
        Log.w(TAG, "starting foreground progress notification");
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ComposeMessage.class);
        ni.setAction(ComposeMessage.ACTION_VIEW_USERID);
        ni.setData(Threads.getUri(userId));
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), NOTIFICATION_ID, ni, Intent.FLAG_ACTIVITY_NEW_TASK);

        mCurrentNotification = new Notification(R.drawable.icon_stat,
                getResources().getString(R.string.sending_message),
                System.currentTimeMillis());
        mCurrentNotification.contentIntent = pi;
        mCurrentNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        foregroundNotification(0);
        startForeground(NOTIFICATION_ID, mCurrentNotification);
    }

    private void foregroundNotification(int progress) {
        mCurrentNotification.contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.progress_notification);
        mCurrentNotification.contentView.setTextViewText(R.id.title, getResources().getString(R.string.sending_message));
        mCurrentNotification.contentView.setTextViewText(R.id.progress_text, String.format("%d%%", progress));
        mCurrentNotification.contentView.setProgressBar(R.id.progress_bar, 100, progress, false);
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
    public void response(RequestJob job, MessageLite response) {
        Log.w(TAG, "job=" + job + ", response=" + response);
        // stop foreground if any
        stopForeground();

        // received command
        if (response != null && response instanceof Protocol.Received) {
            // access to mReceived list is protected
            synchronized (mReceived) {
                Protocol.Received response2 = (Protocol.Received) response;
                List<Protocol.ReceivedEntry> list = response2.getEntryList();
                for (Protocol.ReceivedEntry entry : list) {
                    if (entry.getStatus() == Protocol.Status.STATUS_SUCCESS) {
                        String id = entry.getMessageId();
                        mReceived.remove(id);
                        MessagesProvider.changeMessageStatus(this,
                                id, true, Messages.STATUS_CONFIRMED);
                    }
                }
            }
        }
    }

    @Override
    public boolean error(RequestJob job, Throwable e) {
        // TODO some error notifications
        Log.e(TAG, "request error", e);
        // stop any foreground if the job is a message
        if (job instanceof MessageSender)
            stopForeground();

        return true;
    }

    @Override
    public void uploadProgress(RequestJob job, long bytes) {
        Log.i(TAG, "bytes sent: " + bytes);
        if (job instanceof MessageSender) {
            boolean cancel = ((MessageSender)job).isCanceled();
            if (cancel)
                throw new CancellationException("job has been canceled.");
        }
        publishProgress(bytes);
    }

    @Override
    public void downloadProgress(RequestJob job, long bytes) {
        // TODO ehm :)
        Log.i(TAG, "bytes received: " + bytes);
    }

    private void broadcastMessage(AbstractMessage<?> message) {
        // TODO this will work when AbstractMessage will become Parcelable
        /*
        Intent msg = new Intent(MESSAGE_RECEIVED);
        msg.putExtras(message.toBundle());
        sendBroadcast(msg);
        */
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
                EndpointServer server = MessagingPreferences.getEndpointServer(context);
                intent.putExtra(EndpointServer.class.getName(), server.toString());
                context.startService(intent);
            }
            else
                Log.w(TAG, "network not available - abort service start");
        }
        else
            Log.w(TAG, "background data disabled - abort service start");
    }

    public void setPushNotifications(boolean enabled) {
        mPushNotifications = enabled;
        if (mPushNotifications)
            c2dmRegister();
        else
            c2dmUnregister();
    }

    private void c2dmRegister() {
        // TODO e-mail of sender will be given by serverinfo if any
        String emailOfSender = "kontalkpush@gmail.com";
        Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
        registrationIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
        registrationIntent.putExtra("sender", emailOfSender);
        startService(registrationIntent);
    }

    private void c2dmUnregister() {
        Intent unregIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
        startService(unregIntent);

        setPushRegistrationId(null);
    }

    private void setPushRegistrationId(final String regId) {
        mPushRegistrationId = regId;
        if (mPollingThread != null)
            mPollingThread.setPushRegistrationId(regId);

        // TEST let's see if this works...
        pushRequest(new RequestJob() {
            @Override
            public MessageLite call(RequestClient client, RequestListener listener,
                    Context context) throws IOException {
                return client.update(null, regId);
            }
        });
    }

    /** Stops the message center. */
    public static void stopMessageCenter(final Context context) {
        Log.i(TAG, "stopping message center");
        context.stopService(new Intent(context, MessageCenterService.class));
    }

    public final class MessageCenterInterface extends Binder {
        public MessageCenterService getService() {
            return MessageCenterService.this;
        }
    }

}

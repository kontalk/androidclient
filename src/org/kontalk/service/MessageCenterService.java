/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_UPLOADING;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_UPLOAD_ERROR;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.ClientConnection;
import org.kontalk.client.ClientListener;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.MessageSender;
import org.kontalk.client.Protocol;
import org.kontalk.client.Protocol.AuthenticateResponse;
import org.kontalk.client.Protocol.MessageAckResponse;
import org.kontalk.client.Protocol.ServerInfoResponse;
import org.kontalk.client.Protocol.UserInfoUpdateRequest;
import org.kontalk.client.ReceivedJob;
import org.kontalk.client.ServerinfoJob;
import org.kontalk.client.TxListener;
import org.kontalk.client.UserPresenceRequestJob;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.ReceiptEntry;
import org.kontalk.message.ReceiptEntry.ReceiptEntryList;
import org.kontalk.message.ReceiptMessage;
import org.kontalk.message.UserPresenceData;
import org.kontalk.message.UserPresenceMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.UsersProvider;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.ui.ComposeMessageFragment;
import org.kontalk.ui.ConversationList;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MediaStorage;

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
import android.database.sqlite.SQLiteConstraintException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;


/**
 * The Message Center Service.
 * This service manages the polling of incoming messages - broadcasting them
 * using broadcast intents - and also the outgoing messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageCenterService extends Service
        implements MessageListener, TxListener, RequestListener, ClientListener {

    private static final String TAG = MessageCenterService.class.getSimpleName();

    public static final String ACTION_RESTART = "org.kontalk.RESTART";
    public static final String ACTION_IDLE = "org.kontalk.IDLE";
    public static final String ACTION_HOLD = "org.kontalk.HOLD";
    public static final String ACTION_RELEASE = "org.kontalk.RELEASE";
    public static final String ACTION_C2DM_START = "org.kontalk.CD2M_START";
    public static final String ACTION_C2DM_STOP = "org.kontalk.CD2M_STOP";
    public static final String ACTION_C2DM_REGISTERED = "org.kontalk.C2DM_REGISTERED";
    public static final String ACTION_UPDATE_STATUS = "org.kontalk.UPDATE_STATUS";

    // broadcasted intents
    public static final String ACTION_CONNECTED = "org.kontalk.connected";
    public static final String ACTION_USER_PRESENCE = "org.kontalk.USER_PRESENCE";

    public static final String MESSAGE_RECEIVED = "org.kontalk.MESSAGE_RECEIVED";

    public static final String C2DM_REGISTRATION_ID = "org.kontalk.C2DM_REGISTRATION_ID";

    private Notification mCurrentNotification;
    private long mTotalBytes;

    private RequestWorker mRequestWorker;
    private Account mAccount;

    private Map<String, Byte> mPresenceListeners = new HashMap<String, Byte>();

    private boolean mPushNotifications;
    private String mPushEmail;
    private String mPushRegistrationId;

    /** Used in case ClientThread is down. */
    private int mRefCount;

    /** Private received job instance for message confirmation queueing. */
    private ReceivedJob mReceivedJob;

    private AccountManager mAccountManager;
    private final OnAccountsUpdateListener mAccountsListener = new OnAccountsUpdateListener() {
        @Override
        public void onAccountsUpdated(Account[] accounts) {
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
                Log.w(TAG, "my account has been removed, shutting down");
                // delete all messages
                MessagesProvider.deleteDatabase(MessageCenterService.this);
                stopSelf();
            }
        }
    };

    private LocalBroadcastManager mLocalBroadcastManager;   // created in onCreate
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
        //Log.d(TAG, "Message Center starting - " + intent);
        boolean execStart = false;

        if (intent != null) {
            String action = intent.getAction();

            // C2DM hash registered!
            if (ACTION_C2DM_REGISTERED.equals(action)) {
                setPushRegistrationId(intent.getStringExtra(C2DM_REGISTRATION_ID));
            }

            // start C2DM registration
            else if (ACTION_C2DM_START.equals(action)) {
                setPushNotifications(true);
            }

            // unregister from C2DM
            else if (ACTION_C2DM_STOP.equals(action)) {
                setPushNotifications(false);
            }

            // idle - schedule shutdown
            else if (ACTION_IDLE.equals(action)) {
                // send idle signals to worker threads
                if (mRequestWorker != null)
                    mRequestWorker.idle();
            }

            // hold - increment reference count
            else if (ACTION_HOLD.equals(action)) {
                mRefCount++;
                if (mRequestWorker != null)
                    mRequestWorker.hold();

                // proceed to start only if network is available
                execStart = isNetworkConnectionAvailable(this);
            }

            // release - decrement reference count
            else if (ACTION_RELEASE.equals(action)) {
                mRefCount--;
                if (mRequestWorker != null)
                    mRequestWorker.release();
            }

            // normal start
            else {
                execStart = true;
            }

            // normal start
            if (execStart) {
                Bundle extras = intent.getExtras();
                String serverUrl = (String) extras.get(EndpointServer.class.getName());

                mPushNotifications = MessagingPreferences.getPushNotificationsEnabled(this);
                mAccount = Authenticator.getDefaultAccount(this);
                if (mAccount == null) {
                    stopSelf();
                }
                else {
                    // stop first
                    if (ACTION_RESTART.equals(action)) {
                        stop();
                    }
                    else if (ACTION_UPDATE_STATUS.equals(action)) {
                        if (mRequestWorker != null && mRequestWorker.getClient() != null && mRequestWorker.getClient().isConnected())
                            updateStatusMessage();
                    }

                    // check changing accounts
                    if (mAccountManager == null) {
                        mAccountManager = AccountManager.get(this);
                        mAccountManager.addOnAccountsUpdatedListener(mAccountsListener, null, true);
                    }

                    // activate request worker
                    if (mRequestWorker == null || mRequestWorker.isInterrupted()) {
                        EndpointServer server = new EndpointServer(serverUrl);
                        mRequestWorker = new RequestWorker(this, server, mRefCount);
                        // we must be in control! SHUASHUASHUASHAUSHAUSHA!
                        mRequestWorker.addListener(this, true);
                        mRequestWorker.addListener(this, false);

                        ClientThread client = mRequestWorker.getClient();
                        client.setClientListener(this);
                        client.setDefaultTxListener(this);
                        client.setMessageListener(this);
                        client.setHandler(AuthenticateResponse.class, new AuthenticateListener());
                        client.setHandler(ServerInfoResponse.class, new ServerinfoListener());

                        mRequestWorker.start();
                        // rest will be done in connected()
                    }

                    /*
                     * FIXME c2dm stays on since in onDestroy() we commented
                     * the unregistration call, and here we do nothing about it
                     */
                }
            }
        }

        return START_STICKY;
    }

    /**
     * Shuts down the request worker.
     * @return true if the thread has been stopped, false if it wasn't running.
     */
    private synchronized boolean shutdownRequestWorker() {
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

    private void requestServerinfo() {
        pushRequest(new ServerinfoJob());
    }

    /**
     * Requests subscription to presence notification, looking into the map of
     * listeners.
     */
    private void restorePresenceSubscriptions() {
        Set<String> keys = mPresenceListeners.keySet();
        for (String userId : keys) {
            Byte _eventMask = mPresenceListeners.get(userId);
            pushRequest(new UserPresenceRequestJob(userId, _eventMask.intValue()));
        }
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
                Messages.STATUS + " <> " + Messages.STATUS_RECEIVED + " AND " +
                Messages.STATUS + " <> " + Messages.STATUS_NOTDELIVERED,
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
                m = new MessageSender(userId, text, mime, uri, key, false);
            }

            m.setListener(mMessageRequestListener);
            Log.d(TAG, "resending failed message " + id);
            sendMessage(m);
        }

        c.close();
    }

    private void updateStatusMessage() {
        pushRequest(new RequestJob() {
            @Override
            public String execute(ClientThread client, RequestListener listener, Context context)
                    throws IOException {
                String status = MessagingPreferences.getStatusMessage(MessageCenterService.this);
                UserInfoUpdateRequest.Builder b = UserInfoUpdateRequest.newBuilder();
                b.setStatusMessage(status != null ? status : "");
                return client.getConnection().send(b.build());
            }
        });
    }

    @Override
    public void onCreate() {
        mMessageRequestListener = new MessageRequestListener(this, this);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
    }

    private void stop() {
        // unregister push notifications
        // TEST do not unregister
        //setPushNotifications(false);

        if (mAccountManager != null) {
            mAccountManager.removeOnAccountsUpdatedListener(mAccountsListener);
            mAccountManager = null;
        }

        // stop request worker
        shutdownRequestWorker();
    }

    @Override
    public void onDestroy() {
        stop();
    }

    @Override
    public synchronized void connected(ClientThread client) {
        // reset received messages accumulator
        mReceivedJob = null;
        // request serverinfo
        requestServerinfo();
        // update status message
        updateStatusMessage();
    }

    @Override
    public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
        // TODO default tx listener
        Log.v(TAG, "tx=" + txId + ", pack=" + pack);
        // TEST
        if (pack instanceof MessageAckResponse) {
            MessageAckResponse res = (MessageAckResponse) pack;
            int c = res.getEntryCount();
            for (int i = 0; i < c; i++) {
                MessageAckResponse.Entry e = res.getEntry(i);
                Log.v(TAG, "ack[msgid=" + e.getMessageId() + ", status=" + e.getStatus().getNumber() + "]");
            }
        }
        return true;
    }

    private final class AuthenticateListener implements TxListener {
        @Override
        public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
            // TODO
            AuthenticateResponse res = (AuthenticateResponse) pack;
            if (res.getValid()) {
                authenticated();
            }
            else {
                // TODO WTF ??
                Log.w(TAG, "authentication failed!");
            }

            return true;
        }
    }

    private final class ServerinfoListener implements TxListener {
        @Override
        public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
            ServerInfoResponse res = (ServerInfoResponse) pack;
            for (int i = 0; i < res.getSupportsCount(); i++) {
                String data = res.getSupports(i);
                if (data.startsWith("google_gcm=")) {
                    mPushEmail = data.substring("google_gcm=".length());
                    if (mPushNotifications)
                        gcmRegister();
                }
            }

            return true;
        }
    }

    /** Called when authentication is successful. */
    private void authenticated() {
        // update status message
        updateStatusMessage();
        // subscribe to presence notifications
        restorePresenceSubscriptions();
        // lookup for messages with error status and try to re-send them
        requeuePendingMessages();
        // receipts will be sent while consuming

        // broadcast connected event
        mLocalBroadcastManager.sendBroadcast(new Intent(ACTION_CONNECTED));
    }

    @Override
    public void incoming(AbstractMessage<?> msg) {
        String confirmId = null;
        boolean notify = false;

        // TODO check for null (unsupported) messages to be notified

        // check if the message needs to be confirmed
        if (msg.isNeedAck())
            confirmId = msg.getRealId();

        if (msg instanceof UserPresenceMessage) {
            UserPresenceMessage pres = (UserPresenceMessage) msg;

            // broadcast :)
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
            Intent i = new Intent(ACTION_USER_PRESENCE);
            Uri.Builder b = new Uri.Builder();
            b.scheme("user");
            b.authority(UsersProvider.AUTHORITY);
            b.path(pres.getSender(true));
            i.setDataAndType(b.build(), "internal/presence");
            UserPresenceData data = pres.getContent();
            i.putExtra("org.kontalk.presence.event", data.event);
            i.putExtra("org.kontalk.presence.status", data.statusMessage);
            lbm.sendBroadcast(i);
        }

        // do not store receipts...
        else if (!(msg instanceof ReceiptMessage)) {
            // store to file if it's an image message
            byte[] content = msg.getBinaryContent();

            // FIXME this should be abstracted somehow (e.g. MediaMessage supertype)
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
                msg.setPreviewFile(file);

                // use text content for database table
                content = msg.getTextContent().getBytes();
            }
            else if (msg instanceof VCardMessage) {
                String filename = VCardMessage.buildMediaFilename(msg.getId(), msg.getMime());
                File file = null;
                try {
                    file = MediaStorage.writeInternalMedia(this, filename, content);
                }
                catch (IOException e) {
                    Log.e(TAG, "unable to write to media storage", e);
                }
                // update uri
                if (file != null)
                	msg.setLocalUri(Uri.fromFile(file));

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
            File previewFile = msg.getPreviewFile();
            if (previewFile != null)
                values.put(Messages.PREVIEW_PATH, previewFile.getAbsolutePath());
            values.put(Messages.UNREAD, true);
            values.put(Messages.DIRECTION, Messages.DIRECTION_IN);
            values.put(Messages.TIMESTAMP, msg.getTimestamp());
            values.put(Messages.SERVER_TIMESTAMP, msg.getRawServerTimestamp());
            values.put(Messages.LENGTH, msg.getLength());
            try {
                Uri newMsg = getContentResolver().insert(Messages.CONTENT_URI, values);
                msg.setDatabaseId(ContentUris.parseId(newMsg));

                // we will have to notify the user
                notify = true;
            }
            catch (SQLiteConstraintException econstr) {
                // duplicated message, skip it
            }
        }

        // we have a receipt, update the corresponding message
        else {
            ReceiptMessage msg2 = (ReceiptMessage) msg;
            ReceiptEntryList rlist = msg2.getContent();
            for (ReceiptEntry rentry : rlist) {
                int status = rentry.status;
                int code = (status == Protocol.ReceiptMessage.Entry.ReceiptStatus.STATUS_SUCCESS_VALUE) ?
                        Messages.STATUS_RECEIVED : Messages.STATUS_NOTDELIVERED;

                Date ts;
                try {
                    ts = rentry.getTimestamp();
                    //Log.v(TAG, "using receipt timestamp: " + ts);
                }
                catch (Exception e) {
                    ts = msg.getServerTimestamp();
                    //Log.v(TAG, "using message timestamp: " + ts);
                }

                MessagesProvider.changeMessageStatusWhere(this,
                        true, Messages.STATUS_RECEIVED,
                        rentry.messageId, false, code,
                        -1, ts.getTime());
            }
        }

        // mark sender as registered in the users database
        final String userId = msg.getSender(true);
        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                UsersProvider.markRegistered(context, userId);
            }
        }).start();

        // broadcast message
        broadcastMessage(msg);

        if (notify)
            // update notifications (delayed)
            MessagingNotification.delayedUpdateMessagesNotification(getApplicationContext(), true);

        if (confirmId != null)
            pushReceived(confirmId);
    }

    /**
     * Holds a received command for a while to let the message center process
     * multiple incoming messages.
     */
    private synchronized void pushReceived(String msgId) {
        if (mReceivedJob == null || mReceivedJob.isDone()) {
            mReceivedJob = new ReceivedJob(msgId);
            // delay message so we give time to the next message
            pushRequest(mReceivedJob, 500);
        }
        else {
            mReceivedJob.add(msgId);
        }
    }

    private synchronized void pushRequest(final RequestJob job) {
        pushRequest(job, 0);
    }

    private synchronized void pushRequest(final RequestJob job, long delayMillis) {
        if (mRequestWorker != null && (mRequestWorker.isRunning() || mRequestWorker.isAlive()))
            mRequestWorker.push(job, delayMillis);
        else {
            if (job instanceof ReceivedJob || job instanceof MessageSender) {
                Log.d(TAG, "not queueing message job");
            }
            else {
                Log.d(TAG, "request worker is down, queueing job");
                RequestWorker.pendingJobs.add(job);
            }

            Log.d(TAG, "trying to start message center");
            startMessageCenter(getApplicationContext());
        }
    }

    /** Sends a message using the request worker. */
    public void sendMessage(final MessageSender job) {
        // global listener
        job.setListener(mMessageRequestListener);
        pushRequest(job);
    }

    public void subscribePresence(String userId, int events) {
        mPresenceListeners.put(userId, Byte.valueOf((byte) events));
        pushRequest(new UserPresenceRequestJob(userId, events));
    }

    public void unsubscribePresence(String userId) {
        mPresenceListeners.remove(userId);
        pushRequest(new UserPresenceRequestJob(userId, 0));
    }

    public void startForeground(String userId, long totalBytes) {
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ComposeMessage.class);
        ni.setAction(ComposeMessage.ACTION_VIEW_USERID);
        ni.setData(Threads.getUri(userId));
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_UPLOADING, ni, Intent.FLAG_ACTIVITY_NEW_TASK);

        mCurrentNotification = new Notification(R.drawable.icon_stat,
                getResources().getString(R.string.sending_message),
                System.currentTimeMillis());
        mCurrentNotification.contentIntent = pi;
        mCurrentNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        foregroundNotification(0);
        startForeground(NOTIFICATION_ID_UPLOADING, mCurrentNotification);
    }

    private void foregroundNotification(int progress) {
        mCurrentNotification.contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.progress_notification);
        mCurrentNotification.contentView.setTextViewText(R.id.title, getResources().getString(R.string.sending_message));
        mCurrentNotification.contentView.setTextViewText(R.id.progress_text, String.format("%d%%", progress));
        mCurrentNotification.contentView.setProgressBar(R.id.progress_bar, 100, progress, false);
    }

    public void publishProgress(long bytes) {
        if (mCurrentNotification != null) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFICATION_ID_UPLOAD_ERROR);

            int progress = (int)((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            nm.notify(NOTIFICATION_ID_UPLOADING, mCurrentNotification);
        }
    }

    public void stopForeground() {
        stopForeground(true);
        mCurrentNotification = null;
        mTotalBytes = 0;
    }

    /** Used by the {@link SyncAdapter}. */
    public UserLookupJob lookupUsers(List<String> hashList) {
        UserLookupJob job = new UserLookupJob(hashList);
        pushRequest(job);
        return job;
    }

    /** Used by the {@link ComposeMessageFragment}. */
    public UserLookupJob lookupUser(String userId) {
        UserLookupJob job = new UserLookupJob(userId);
        pushRequest(job);
        return job;
    }

    private void broadcastMessage(AbstractMessage<?> message) {
        // TODO this will work when AbstractMessage will become Parcelable
        /*
        Intent msg = new Intent(MESSAGE_RECEIVED);
        msg.putExtras(message.toBundle());
        sendBroadcast(msg);
        */
    }

    /** Checks for network availability. */
    private static boolean isNetworkConnectionAvailable(Context context) {
        final ConnectivityManager cm = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm.getBackgroundDataSetting()) {
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.getState() == NetworkInfo.State.CONNECTED)
                return true;
        }

        return false;
    }

    /** Starts the message center. */
    public static void startMessageCenter(final Context context) {
        // check for network state
        if (isNetworkConnectionAvailable(context)) {
            Log.d(TAG, "starting message center");
            final Intent intent = new Intent(context, MessageCenterService.class);

            // get the URI from the preferences
            EndpointServer server = MessagingPreferences.getEndpointServer(context);
            intent.putExtra(EndpointServer.class.getName(), server.toString());
            context.startService(intent);
        }
        else
            Log.d(TAG, "network not available or background data disabled - abort service start");
    }

    public static void updateStatus(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        EndpointServer server = MessagingPreferences.getEndpointServer(context);
        i.putExtra(EndpointServer.class.getName(), server.toString());
        i.setAction(MessageCenterService.ACTION_UPDATE_STATUS);
        context.startService(i);
    }

    /** Stops the message center. */
    public static void stopMessageCenter(final Context context) {
        Log.d(TAG, "shutting down message center");
        context.stopService(new Intent(context, MessageCenterService.class));
    }

    /** Triggers a managed message center restart. */
    public static void restartMessageCenter(final Context context) {
        Log.d(TAG, "restarting message center");
        Intent i = new Intent(context, MessageCenterService.class);
        EndpointServer server = MessagingPreferences.getEndpointServer(context);
        i.putExtra(EndpointServer.class.getName(), server.toString());
        i.setAction(MessageCenterService.ACTION_RESTART);
        context.startService(i);
    }

    /** Tells the message center we are idle, taking necessary actions. */
    public static void idleMessageCenter(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_IDLE);
        context.startService(i);
    }

    /**
     * Tells the message center we are holding on to it, preventing any
     * shutdown for inactivity.
     */
    public static void holdMessageCenter(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_HOLD);
        // include server uri if server needs to be started
        EndpointServer server = MessagingPreferences.getEndpointServer(context);
        i.putExtra(EndpointServer.class.getName(), server.toString());
        context.startService(i);
    }

    /**
     * Tells the message center we are releasing it, allowing any shutdown
     * for inactivity.
     */
    public static void releaseMessageCenter(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_RELEASE);
        context.startService(i);
    }

    /** Starts the push notifications registration process. */
    public static void enablePushNotifications(Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_C2DM_START);
        context.startService(i);
    }

    /** Starts the push notifications unregistration process. */
    public static void disablePushNotifications(Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_C2DM_STOP);
        context.startService(i);
    }

    /** Caches the given registration Id for use with push notifications. */
    public static void registerPushNotifications(Context context, String registrationId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_C2DM_REGISTERED);
        i.putExtra(MessageCenterService.C2DM_REGISTRATION_ID, registrationId);
        context.startService(i);
    }

    public void setPushNotifications(boolean enabled) {
        mPushNotifications = enabled;
        if (mPushNotifications) {
            if (mPushRegistrationId == null)
                gcmRegister();
        }
        else {
            gcmUnregister();
        }
    }

    private void gcmRegister() {
        if (mPushEmail != null) {
            // e-mail of sender will be given by serverinfo if any
            Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
            registrationIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
            registrationIntent.putExtra("sender", mPushEmail);
            startService(registrationIntent);
        }
    }

    private void gcmUnregister() {
        Intent unregIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
        startService(unregIntent);

        setPushRegistrationId(null);
    }

    private void setPushRegistrationId(String regId) {
        mPushRegistrationId = regId;
        if (mRequestWorker != null)
            mRequestWorker.setPushRegistrationId(regId);

        // notify the server about the change
        pushRequest(new RequestJob() {
            @Override
            public String execute(ClientThread client, RequestListener listener, Context context)
                    throws IOException {
                UserInfoUpdateRequest.Builder b = UserInfoUpdateRequest.newBuilder();
                b.setGoogleRegistrationId(mPushRegistrationId != null ? mPushRegistrationId: "");
                return client.getConnection().send(b.build());
            }
        });
    }

    public final class MessageCenterInterface extends Binder {
        public MessageCenterService getService() {
            return MessageCenterService.this;
        }
    }

    @Override
    public void starting(ClientThread client, RequestJob job) {
        // not a plain text message - use progress notification
        if (job instanceof MessageSender) {
            MessageSender msg = (MessageSender) job;
            if (msg.getSourceUri() != null) {
                try {
                    startForeground(msg.getUserId(), msg.getContentLength(this));
                }
                catch (IOException e) {
                    Log.e(TAG, "error reading message data to send", e);
                    MessagesProvider.changeMessageStatus(this,
                            msg.getMessageUri(), Messages.DIRECTION_OUT, Messages.STATUS_ERROR,
                            -1, System.currentTimeMillis());
                    // just don't send for now
                    return;
                }
            }
        }
    }

    @Override
    public void uploadProgress(ClientThread client, RequestJob job, long bytes) {
        //Log.v(TAG, "bytes sent: " + bytes);
        if (job instanceof MessageSender) {
            boolean cancel = ((MessageSender)job).isCanceled(this);
            if (cancel)
                throw new CancellationException("job has been canceled.");
        }
        publishProgress(bytes);
        Thread.yield();
    }

    @Override
    public void downloadProgress(ClientThread client, RequestJob job, long bytes) {
        // TODO
    }

    @Override
    public void done(ClientThread client, RequestJob job, String txId) {
        if (job instanceof MessageSender) {
            // we are sending a message, check if it's a binary content
            MessageSender msg = (MessageSender) job;
            if (msg.getSourceUri() != null) {
                // stop any foreground notification
                stopForeground();
                // queue an attachment MessageSender (txId is the fileid)
                ByteString bf = ByteString.copyFromUtf8(txId);
                MessageSender inc = new MessageSender(msg.getUserId(), bf.toByteArray(),
                        msg.getMime(), msg.getMessageUri(), msg.getEncryptKey(), true);
                sendMessage(inc);
            }
        }
    }

    @Override
    public boolean error(ClientThread client, RequestJob job, Throwable exc) {
        // stop any foreground if the job is a message
        if (job instanceof MessageSender) {
            stopForeground();

            if (job.isCanceled(this))
                return false;

            MessageSender job2 = (MessageSender) job;
            if (job2.getSourceUri() != null) {
                // create intent for upload error notification
                // TODO this Intent should bring the user to the actual conversation
                Intent i = new Intent(this, ConversationList.class);
                PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                        NOTIFICATION_ID_UPLOAD_ERROR, i, Intent.FLAG_ACTIVITY_NEW_TASK);

                // create notification
                Notification no = new Notification(R.drawable.icon_stat,
                        getString(R.string.notify_ticker_upload_error),
                        System.currentTimeMillis());
                no.setLatestEventInfo(getApplicationContext(),
                        getString(R.string.notify_title_upload_error),
                        getString(R.string.notify_text_upload_error), pi);
                no.flags |= Notification.FLAG_AUTO_CANCEL;

                // notify!!
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(NOTIFICATION_ID_UPLOAD_ERROR, no);
            }
        }

        return true;
    }

}

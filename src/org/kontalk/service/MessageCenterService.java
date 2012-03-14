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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.ClientConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.Protocol;
import org.kontalk.client.Protocol.AuthenticateResponse;
import org.kontalk.client.TxListener;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.ReceiptMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MediaStorage;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.google.protobuf.MessageLite;


/**
 * The Message Center Service.
 * This service manages the polling of incoming messages - broadcasting them
 * using broadcast intents - and also the outgoing messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageCenterService extends Service
        implements MessageListener, TxListener {

    private static final String TAG = MessageCenterService.class.getSimpleName();

    public static final String ACTION_RESTART = "org.kontalk.RESTART";
    public static final String ACTION_IDLE = "org.kontalk.IDLE";
    public static final String ACTION_HOLD = "org.kontalk.HOLD";
    public static final String ACTION_RELEASE = "org.kontalk.RELEASE";
    public static final String ACTION_C2DM_START = "org.kontalk.CD2M_START";
    public static final String ACTION_C2DM_STOP = "org.kontalk.CD2M_STOP";
    public static final String ACTION_C2DM_REGISTERED = "org.kontalk.C2DM_REGISTERED";
    public static final String MESSAGE_RECEIVED = "org.kontalk.MESSAGE_RECEIVED";

    public static final String C2DM_REGISTRATION_ID = "org.kontalk.C2DM_REGISTRATION_ID";

    private ClientThread mClientThread;
    private Account mAccount;

    private boolean mPushNotifications;
    private String mPushEmail;
    private String mPushRegistrationId;

    /** Used in case ClientThread is down. */
    private int mRefCount;

    /**
     * This list will contain the received messages - avoiding multiple
     * received jobs.
     */
    private List<String> mReceived = new ArrayList<String>();

    private AccountManager mAccountManager;
    private final OnAccountsUpdateListener mAccountsListener = new OnAccountsUpdateListener() {
        @Override
        public void onAccountsUpdated(Account[] accounts) {
            Log.i(TAG, "accounts have been changed, checking");

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
        Log.i(TAG, "Message Center starting - " + intent);
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
                // TODO send idle signals to worker threads
            }

            // hold - increment reference count
            else if (ACTION_HOLD.equals(action)) {
                mRefCount++;
                // TODO mClientThread.hold()
                // proceed to start only if network is available
                execStart = isNetworkConnectionAvailable(this);
            }

            // release - decrement reference count
            else if (ACTION_RELEASE.equals(action)) {
                mRefCount--;
                // TODO mClientThread.release()
            }

            // normal start
            else {
                execStart = true;
            }

            // normal start
            if (execStart) {
                Bundle extras = intent.getExtras();
                String serverUrl = (String) extras.get(EndpointServer.class.getName());
                Log.d(TAG, "using server uri: " + serverUrl);

                mPushNotifications = MessagingPreferences.getPushNotificationsEnabled(this);
                mAccount = Authenticator.getDefaultAccount(this);
                if (mAccount == null) {
                    // onDestroy will unlock()
                    stopSelf();
                }
                else {
                    // stop first
                    if (ACTION_RESTART.equals(action)) {
                        Log.d(TAG, "restart requested");
                        stop();
                    }

                    // check changing accounts
                    if (mAccountManager == null) {
                        mAccountManager = AccountManager.get(this);
                        mAccountManager.addOnAccountsUpdatedListener(mAccountsListener, null, true);
                    }

                    // start client thread
                    if (mClientThread == null ||
                            mClientThread.isInterrupted()) {
                        // TODO from intent please :)
                        EndpointServer server = new EndpointServer(serverUrl);
                        mClientThread = new ClientThread(this, server);
                        mClientThread.setDefaultTxListener(this);
                        mClientThread.setMessageListener(this);
                        mClientThread.setHandler(AuthenticateResponse.class, new AuthenticateListener());
                        mClientThread.start();
                    }

                    // TODO update status message

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
     * Shuts down the client thread.
     * @return true if the thread has been stopped, false if it wasn't running.
     */
    private synchronized boolean shutdownClientThread() {
        if (mClientThread != null) {
            ClientThread tmp = mClientThread;
            // discard the reference to the thread immediately
            mClientThread = null;
            tmp.shutdown();
            return true;
        }
        return false;
    }

    private void stop() {
        // unregister push notifications
        // TEST do not unregister
        //setPushNotifications(false);

        if (mAccountManager != null) {
            mAccountManager.removeOnAccountsUpdatedListener(mAccountsListener);
            mAccountManager = null;
        }

        // stop client thread
        shutdownClientThread();
    }

    @Override
    public void onDestroy() {
        stop();
    }

    @Override
    public void tx(ClientConnection connection, String txId, MessageLite pack) {
        // TODO default tx listener
        Log.d(TAG, "tx=" + txId + ", pack=" + pack);
    }

    private final class AuthenticateListener implements TxListener {
        @Override
        public void tx(ClientConnection connection, String txId, MessageLite pack) {
            // TODO
            AuthenticateResponse res = (AuthenticateResponse) pack;
            Log.d(TAG, "authentication result=" + res.getValid());
            if (res.getValid()) {
                authenticated();
            }
            else {
                // TODO WTF ??
            }
        }
    }

    /** Called when authentication is successful. */
    private void authenticated() {
        // TODO requeue pending messages
    }

    @Override
    public void incoming(AbstractMessage<?> msg) {
        List<String> list = new ArrayList<String>();

        // access to mReceived list is protected
        synchronized (mReceived) {
            boolean notify = false;

            // TODO check for null (unsupported) messages to be notified

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
                    values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                    Uri newMsg = getContentResolver().insert(Messages.CONTENT_URI, values);
                    msg.setDatabaseId(ContentUris.parseId(newMsg));

                    // we will have to notify the user
                    notify = true;
                }

                // we have a receipt, update the corresponding message
                else {
                    ReceiptMessage msg2 = (ReceiptMessage) msg;
                    Log.d(TAG, "receipt for message " + msg2.getMessageId());

                    int status = msg2.getStatus();
                    int code = (status == Protocol.ReceiptMessage.Entry.ReceiptStatus.STATUS_SUCCESS_VALUE) ?
                            Messages.STATUS_RECEIVED : Messages.STATUS_NOTDELIVERED;

                    MessagesProvider.changeMessageStatusWhere(this,
                            true, Messages.STATUS_RECEIVED,
                            msg2.getMessageId(), false, code,
                            -1, msg.getServerTimestamp().getTime());
                }

                // broadcast message
                broadcastMessage(msg);
            }

            if (notify)
                // update notifications (delayed)
                MessagingNotification.delayedUpdateMessagesNotification(getApplicationContext(), true);
        }

        if (list.size() > 0) {
            Log.d(TAG, "pushing receive confirmation");
            //RequestJob job = new ReceivedJob(list);
            // TODO pushRequest(job);
        }
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
                c2dmRegister();
        }
        else {
            c2dmUnregister();
        }
    }

    private void c2dmRegister() {
        if (mPushEmail != null) {
            // e-mail of sender will be given by serverinfo if any
            Intent registrationIntent = new Intent("com.google.android.c2dm.intent.REGISTER");
            registrationIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
            registrationIntent.putExtra("sender", mPushEmail);
            startService(registrationIntent);
        }
    }

    private void c2dmUnregister() {
        Intent unregIntent = new Intent("com.google.android.c2dm.intent.UNREGISTER");
        unregIntent.putExtra("app", PendingIntent.getBroadcast(this, 0, new Intent(), 0));
        startService(unregIntent);

        setPushRegistrationId(null);
    }

    private void setPushRegistrationId(String regId) {
        mPushRegistrationId = regId;
        // TODO setPushRegistrationId
        /*
        if (mPollingThread != null)
            mPollingThread.setPushRegistrationId(regId);
         */

        // TODO notify the server about the change
    }

    public final class MessageCenterInterface extends Binder {
        public MessageCenterService getService() {
            return MessageCenterService.this;
        }
    }

}

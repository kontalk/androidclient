package org.nuntius.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.nuntius.authenticator.Authenticator;
import org.nuntius.client.AbstractMessage;
import org.nuntius.client.EndpointServer;
import org.nuntius.client.MessageSender;
import org.nuntius.client.ReceiptMessage;
import org.nuntius.client.StatusResponse;
import org.nuntius.provider.MessagesProvider;
import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.ui.MessagingPreferences;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;


/**
 * The Message Center Service.
 * This service manages the polling of incoming messages - broadcasting them
 * using broadcast intents - and also the outgoing messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageCenterService extends Service
        implements MessageListener, ResponseListener {

    private static final String TAG = MessageCenterService.class.getSimpleName();

    public static final String MESSAGE_RECEIVED = "org.nuntius.MESSAGE_RECEIVED";

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

    private Handler mHandler;
    private final IBinder mBinder = new MessageCenterInterface();

    /**
     * Not used.
     */
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
        if (intent != null) {
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
                    mAccountManager.addOnAccountsUpdatedListener(mAccountsListener, mHandler, true);
                }

                // activate request worker if necessary
                if (mRequestWorker == null) {
                    mRequestWorker = new RequestWorker(this, server);
                    mRequestWorker.setResponseListener(this);
                    mRequestWorker.start();
                }

                // start polling thread if needed
                if (mPollingThread == null) {
                    mPollingThread = new PollingThread(this, server);
                    mPollingThread.setMessageListener(this);
                    mPollingThread.start();
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (mAccountManager != null) {
            mAccountManager.removeOnAccountsUpdatedListener(mAccountsListener);
            mAccountManager = null;
        }

        // stop polling thread
        if (mPollingThread != null) {
            mPollingThread.shutdown();
            mPollingThread = null;
        }

        // stop request worker
        if (mRequestWorker != null) {
            mRequestWorker.shutdown();
            mRequestWorker = null;
        }
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

                        // save to local storage
                        ContentValues values = new ContentValues();
                        values.put(Messages.MESSAGE_ID, msg.getId());
                        values.put(Messages.PEER, msg.getSender());
                        values.put(Messages.MIME, msg.getMime());
                        values.put(Messages.CONTENT, msg.getTextContent());
                        values.put(Messages.UNREAD, true);
                        values.put(Messages.DIRECTION, Messages.DIRECTION_IN);
                        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
                        getContentResolver().insert(Messages.CONTENT_URI, values);
                    }

                    // we have a receipt, update the corresponding message
                    else {
                        ReceiptMessage msg2 = (ReceiptMessage) msg;
                        Log.w(TAG, "receipt for message " + msg2.getMessageId());

                        ContentValues values = new ContentValues();
                        values.put(Messages.STATUS, Messages.STATUS_RECEIVED);
                        values.put(Messages.TIMESTAMP, msg.getServerTimestamp().getTime());
                        getContentResolver().update(Messages.CONTENT_URI, values,
                                Messages.MESSAGE_ID + " = ?",
                                new String[] { msg2.getMessageId() });
                    }

                    // broadcast message
                    broadcastMessage(msg);
                    // update notifications
                    MessagingNotification.updateMessagesNotification(this, true);
                }
            }
        }

        if (list.size() > 0) {
            Log.w(TAG, "pushing receive confirmation");
            RequestJob job = new RequestJob("received", list);
            mRequestWorker.push(job);
        }
    }

    /** Sends a message using the request worker. */
    public void sendMessage(MessageSender job) {
        mRequestWorker.push(job);
    }

    @Override
    public synchronized void response(RequestJob job, List<StatusResponse> statuses) {
        Log.w(TAG, "statuses: " + statuses);

        // received command
        if ("received".equals(job.getCommand())) {
            // access to mReceived list is protected
            synchronized (mReceived) {
                // single status - retrieve message id from request
                if (statuses.size() == 1) {
                    List<NameValuePair> params = job.getParams();
                    for (NameValuePair par : params) {
                        if ("i".equals(par.getName()))
                            mReceived.remove(par.getValue());
                    }
                }
                // multiple statuses - each status has its own message id
                else {
                    for (StatusResponse st : statuses) {
                        if (st.extra != null) {
                            String idToRemove = (String) st.extra.get("i");
                            if (idToRemove != null)
                                mReceived.remove(idToRemove);
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
        return false;
    }


    private void broadcastMessage(AbstractMessage<?> message) {
        Intent msg = new Intent(MESSAGE_RECEIVED);
        msg.putExtras(message.toBundle());
        sendBroadcast(msg);
    }

    /** Starts the message center. */
    public static void startMessageCenter(Context context) {
        // TODO should we check for network state??
        Log.i(TAG, "starting message center");
        final Intent intent = new Intent(context, MessageCenterService.class);

        // get the URI from the preferences
        String uri = MessagingPreferences.getServerURI(context);
        intent.putExtra(EndpointServer.class.getName(), uri);
        context.startService(intent);
    }

    /** Stops the message center. */
    public static void stopMessageCenter(Context context) {
        Log.i(TAG, "stopping message center");
        context.stopService(new Intent(context, MessageCenterService.class));
    }

    public final class MessageCenterInterface extends Binder {
        public MessageCenterService getService() {
            return MessageCenterService.this;
        }
    }

}

package org.nuntius.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.nuntius.client.AbstractMessage;
import org.nuntius.client.EndpointServer;
import org.nuntius.client.ReceiptMessage;
import org.nuntius.client.StatusResponse;
import org.nuntius.provider.MyMessages.Messages;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.os.Bundle;
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

    /**
     * This list will contain the received messages - avoiding multiple
     * received jobs.
     */
    private List<String> mReceived = new ArrayList<String>();

    /**
     * Not used.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
            String token = (String) extras.get(EndpointServer.HEADER_AUTH_TOKEN);
            String serverUrl = (String) extras.get(EndpointServer.class.getName());
            EndpointServer server = new EndpointServer(serverUrl);

            // activate request worker if necessary
            if (mRequestWorker == null) {
                mRequestWorker = new RequestWorker(server);
                mRequestWorker.setResponseListener(this);
                mRequestWorker.setAuthToken(token);
                mRequestWorker.start();
            }

            // start polling thread if needed
            if (mPollingThread == null) {
                mPollingThread = new PollingThread(server);
                mPollingThread.setMessageListener(this);
                mPollingThread.setAuthToken(token);
                mPollingThread.start();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
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
                }
            }
        }

        if (list.size() > 0) {
            Log.w(TAG, "pushing receive confirmation");
            RequestJob job = new RequestJob("received", list);
            mRequestWorker.push(job);
        }
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
                            String idToRemove = st.extra.get("i");
                            if (idToRemove != null)
                                mReceived.remove(idToRemove);
                        }
                    }
                }
            }
        }
    }

    private void broadcastMessage(AbstractMessage<?> message) {
        Intent msg = new Intent(MESSAGE_RECEIVED);
        msg.putExtras(message.toBundle());
        sendBroadcast(msg);
    }
}

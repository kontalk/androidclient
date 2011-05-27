package org.nuntius.android.service;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.nuntius.android.client.AbstractMessage;
import org.nuntius.android.client.EndpointServer;
import org.nuntius.android.client.StatusResponse;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;


/**
 * The Message Center Service.
 * This service manages the polling of incoming messages - broadcasting them
 * using broadcast intents - and also the outgoing messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageCenterService extends Service
        implements MessageListener, ResponseListener {

    private PollingThread mPollingThread;
    private RequestWorker mRequestWorker;

    /**
     * This list will contain the received messages - avoiding multiple
     * received jobs.
     */
    private List<String> mReceived;

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
        Bundle extras = intent.getExtras();
        String token = (String) extras.get(EndpointServer.HEADER_AUTH_TOKEN);
        String serverUrl = (String) extras.get(EndpointServer.class.getName());
        EndpointServer server = new EndpointServer(serverUrl);

        mReceived = new ArrayList<String>();

        // activate request worker if necessary
        if (mRequestWorker == null) {
            mRequestWorker = new RequestWorker(this, server);
            mRequestWorker.setResponseListener(this);
            mRequestWorker.setAuthToken(token);
            mRequestWorker.start();
        }

        // start polling thread if needed
        if (mPollingThread == null) {
            mPollingThread = new PollingThread(this, server);
            mPollingThread.setMessageListener(this);
            mPollingThread.setAuthToken(token);
            mPollingThread.start();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // stop polling thread
        if (mPollingThread != null) {
            mPollingThread.shutdown();
        }

        // stop request worker
        if (mRequestWorker != null) {
            mRequestWorker.shutdown();
        }
    }

    @Override
    public void incoming(List<AbstractMessage> messages) {
        List<NameValuePair> list = new ArrayList<NameValuePair>();

        for (AbstractMessage msg : messages) {
            if (!mReceived.contains(msg.getId())) {
                list.add(new BasicNameValuePair("i[]", msg.getId()));
                mReceived.add(msg.getId());
            }
        }

        if (list.size() > 0) {
            RequestJob job = new RequestJob("received", list);
            mRequestWorker.push(job);
        }
    }

    @Override
    public void response(List<StatusResponse> statuses) {
        // TODO manage response statuses
    }
}

package org.nuntius.android.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;


/**
 * The Message Center Service.
 * This service manages the polling of incoming messages - broadcasting them
 * using broadcast intents - and also the outgoing messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageCenterService extends Service {

    private PollingThread mPollingThread;
    //private RequestWorker mRequestWorker;

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
        // TODO start polling thread
        // TODO activate request worker if necessary
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
         // TODO stop polling thread and request worker
    }
}

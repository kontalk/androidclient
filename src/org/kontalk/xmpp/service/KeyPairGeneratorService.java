package org.kontalk.xmpp.service;

import java.io.IOException;
import java.lang.ref.WeakReference;

import org.kontalk.xmpp.crypto.PersonalKey;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


/** Generates a key pair in the background. */
public class KeyPairGeneratorService extends Service {

    public static final String ACTION_GENERATE = "org.kontalk.keypair.GENERATE";

    public static final String EXTRA_KEY = "org.kontalk.keypair.KEY";

    private GeneratorThread mThread;
    private volatile PersonalKey mKey;

    private LocalBroadcastManager lbm;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (lbm == null)
            lbm = LocalBroadcastManager.getInstance(getApplicationContext());

        String action = intent.getAction();
        if (ACTION_GENERATE.equals(action)) {
            // start the keypair generator
            if (mThread == null) {
                mThread = new GeneratorThread(this);
                mThread.start();
            }
            else {
                if (mKey != null) {
                    broadcastKey();
                }
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void broadcastKey() {
        Intent i = new Intent(ACTION_GENERATE);
        i.putExtra(EXTRA_KEY, mKey);
        lbm.sendBroadcast(i);
    }

    private void keypairGenerated(PersonalKey key) {
        mKey = key;
        broadcastKey();
    }

    private static final class GeneratorThread extends Thread {
        private WeakReference<KeyPairGeneratorService> s;

        public GeneratorThread(KeyPairGeneratorService service) {
            s = new WeakReference<KeyPairGeneratorService>(service);
        }

        @Override
        public void run() {
            KeyPairGeneratorService service = s.get();
            if (service != null) {
                try {
                    // TODO 512 bits? Are you kidding me?
                    PersonalKey key = PersonalKey.create(512);
                    Log.v("KeyPair", "key pair generated: " + key);
                    service.keypairGenerated(key);
                }
                catch (IOException e) {
                    Log.v("KeyPair", "keypair generation failed", e);
                    // TODO notify user
                }
            }
        }
    }

}

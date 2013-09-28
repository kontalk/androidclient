package org.kontalk.xmpp.service;

import java.io.IOException;
import java.lang.ref.WeakReference;

import org.kontalk.xmpp.crypto.PersonalKey;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


/** Generates a key pair in the background. */
public class KeyPairGeneratorService extends Service {

    // TODO of course this is just to be quick in the emulator
    private static final int DEFAULT_KEY_SIZE = 1024;

    public static final String ACTION_GENERATE = "org.kontalk.keypair.GENERATE";

    public static final String EXTRA_KEY = "org.kontalk.keypair.KEY";
    public static final String EXTRA_FOREGROUND = "org.kontalk.keypair.FOREGROUND";

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
                if (intent.getBooleanExtra(EXTRA_FOREGROUND, false))
                    startForeground();

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

    private void startForeground() {
        // TODO startForeground(...)
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
                    PersonalKey key = PersonalKey.create(DEFAULT_KEY_SIZE);
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

    public interface PersonalKeyRunnable {
        public void run(PersonalKey key);
    }

    public final static class KeyGeneratedReceiver extends BroadcastReceiver {
        private final Handler handler;
        private final PersonalKeyRunnable action;

        public KeyGeneratedReceiver(Handler handler, PersonalKeyRunnable action) {
            this.handler = handler;
            this.action = action;
        }

        @Override
        public void onReceive(Context context, final Intent intent) {
            if (KeyPairGeneratorService.ACTION_GENERATE.equals(intent.getAction())) {
                // we can stop the service now
                context.stopService(new Intent(context, KeyPairGeneratorService.class));

                handler.post(new Runnable() {
                    public void run() {
                        PersonalKey key = intent.getParcelableExtra(KeyPairGeneratorService.EXTRA_KEY);
                        action.run(key);
                    }
                });
            }
        }

    }

}

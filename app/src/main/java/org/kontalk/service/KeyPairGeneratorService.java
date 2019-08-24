/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Date;

import com.instacart.library.truetime.TrueTime;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.ui.ConversationsActivity;
import org.kontalk.ui.MessagingNotification;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_KEYPAIR_GEN;


/** Generates a key pair in the background. */
public class KeyPairGeneratorService extends Service {

    /**
     * Broadcasted when key pair generation has finished.
     * Send this intent to start generating a new key pair or broadcast back
     * the one just created.
     */
    public static final String ACTION_GENERATE = "org.kontalk.keypair.GENERATE";
    /**
     * Broadcasted if, after sending an {@link #ACTION_GENERATE}, the key pair
     * generator thread has started.
     */
    public static final String ACTION_STARTED = "org.kontalk.keypair.STARTED";

    public static final String EXTRA_KEY = "org.kontalk.keypair.KEY";
    public static final String EXTRA_FOREGROUND = "org.kontalk.keypair.FOREGROUND";

    private static final String NTP_DEFAULT_SERVER = "time.google.com";
    private static final int NTP_MAX_RETRIES = 3;

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

                broadcastStarted();
            }
            else {
                if (mKey != null) {
                    broadcastKey();
                }
            }
        }

        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForeground() {
        Intent ni = new Intent(getApplicationContext(), ConversationsActivity.class);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
            NOTIFICATION_ID_KEYPAIR_GEN, ni, 0);

        Notification no = new NotificationCompat
            .Builder(this, MessagingNotification.CHANNEL_OTHER)
            .setOngoing(true)
            .setTicker(getString(R.string.notify_gen_keypair_ticker))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.notify_gen_keypair_title))
            .setContentText(getString(R.string.notify_gen_keypair_text))
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build();

        startForeground(NOTIFICATION_ID_KEYPAIR_GEN, no);
    }

    private void stopForeground() {
        stopForeground(true);
    }

    private void broadcastKey() {
        Intent i = new Intent(ACTION_GENERATE);
        i.putExtra(EXTRA_KEY, mKey);
        lbm.sendBroadcast(i);
    }

    private void broadcastStarted() {
        Intent i = new Intent(ACTION_STARTED);
        lbm.sendBroadcast(i);
    }

    private void keypairGenerated(PersonalKey key) {
        mKey = key;
        broadcastKey();
    }

    private static final class GeneratorThread extends Thread {
        private WeakReference<KeyPairGeneratorService> s;

        GeneratorThread(KeyPairGeneratorService service) {
            s = new WeakReference<>(service);
        }

        @Override
        public void run() {
            // set a low priority
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            KeyPairGeneratorService service = s.get();
            if (service != null) {
                // we need the real time from the Internet
                Date timestamp = getRealtime(service);

                try {
                    PersonalKey key = PersonalKey.create(timestamp);
                    Log.v("KeyPair", "key pair generated: " + key);
                    service.keypairGenerated(key);
                }
                catch (IOException e) {
                    Log.v("KeyPair", "keypair generation failed", e);
                    // TODO notify user
                }

                service.stopForeground();
            }
        }

        private Date getRealtime(Context context) {
            try {
                return TrueTime.now();
            }
            catch (IllegalStateException e) {
                int retryCount = 0;
                while (retryCount < NTP_MAX_RETRIES) {
                    try {
                        TrueTime.build()
                            .withSharedPreferences(context)
                            .withNtpHost(NTP_DEFAULT_SERVER)
                            .initialize();
                        break;
                    }
                    catch (IOException ioe) {
                        retryCount++;
                    }
                }

                try {
                    return TrueTime.now();
                }
                catch (Exception ise) {
                    Log.w("KeyPair", "unable to retrieve real time from network, using system time");
                    return new Date();
                }
            }
        }
    }

    public interface PersonalKeyRunnable {
        void run(PersonalKey key);
    }

    public final static class KeyGeneratorReceiver extends BroadcastReceiver {
        private final Handler handler;
        private final PersonalKeyRunnable action;

        public KeyGeneratorReceiver(Handler handler, PersonalKeyRunnable action) {
            this.handler = handler;
            this.action = action;
        }

        @Override
        public void onReceive(Context context, final Intent intent) {

            // key has been generated
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

            // key generation has started
            else if (KeyPairGeneratorService.ACTION_STARTED.equals(intent.getAction())) {
                // simply run the action with null key
                handler.post(new Runnable() {
                    public void run() {
                        action.run(null);
                    }
                });
            }
        }

    }

}

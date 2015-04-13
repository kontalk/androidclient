/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.service.msgcenter;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

import org.kontalk.util.Preferences;


/**
 * An adaptive ping manager using {@link AlarmManager}.
 * @author Daniele Ricci
 */
public class AdaptiveServerPingManager extends Manager {

    private static final Logger LOGGER = Logger.getLogger(AdaptiveServerPingManager.class.getName());

    private static final String PING_ALARM_ACTION = "org.igniterealtime.smackx.ping.ACTION";

    private static final Map<XMPPConnection, AdaptiveServerPingManager> INSTANCES = new WeakHashMap<XMPPConnection, AdaptiveServerPingManager>();

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener() {
            @Override
            public void connectionCreated(XMPPConnection connection) {
                getInstanceFor(connection);
            }
        });
    }

    private static final class PingFailedNotifier implements PingFailedListener {

        private final WeakReference<AdaptiveServerPingManager> mManager;

        public PingFailedNotifier(AdaptiveServerPingManager manager) {
            mManager = new WeakReference<AdaptiveServerPingManager>(manager);
        }

        @Override
        public void pingFailed() {
            AdaptiveServerPingManager.pingFailed();
        }
    }

    public static synchronized AdaptiveServerPingManager getInstanceFor(XMPPConnection connection) {
        AdaptiveServerPingManager serverPingWithAlarmManager = INSTANCES.get(connection);
        if (serverPingWithAlarmManager == null) {
            serverPingWithAlarmManager = new AdaptiveServerPingManager(connection);
            INSTANCES.put(connection, serverPingWithAlarmManager);

            // register ping failed listener for automatic retry management
            PingManager.getInstanceFor(connection)
                .registerPingFailedListener(new PingFailedNotifier(serverPingWithAlarmManager));
        }
        return serverPingWithAlarmManager;
    }

    private boolean mEnabled = true;

    private AdaptiveServerPingManager(XMPPConnection connection) {
        super(connection);
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    private static final BroadcastReceiver ALARM_BROADCAST_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LOGGER.fine("Ping Alarm broadcast received");
            Iterator<XMPPConnection> it = INSTANCES.keySet().iterator();
            while (it.hasNext()) {
                XMPPConnection connection = it.next();
                if (getInstanceFor(connection).isEnabled()) {
                    LOGGER.fine("Calling pingServerIfNecessary for connection "
                        + connection.getConnectionCounter());
                    final PingManager pingManager = PingManager.getInstanceFor(connection);
                    // Android BroadcastReceivers have a timeout of 60 seconds.
                    // The connections reply timeout may be higher, which causes
                    // timeouts of the broadcast receiver and a subsequent ANR
                    // of the App of the broadcast receiver. We therefore need
                    // to call pingServerIfNecessary() in a new thread to avoid
                    // this. It could happen that the device gets back to sleep
                    // until the Thread runs, but that's a risk we are willing
                    // to take into account as it's unlikely.
                    Async.go(new Runnable() {
                        @Override
                        public void run() {
                            pingManager.pingServerIfNecessary();
                            if (!isLastPingFailed()) {
                                pingSuccess();
                            }
                        }
                    }, "PingServerIfNecessary (" + connection.getConnectionCounter() + ')');
                }
                else {
                    LOGGER.fine("NOT calling pingServerIfNecessary (disabled) on connection "
                        + connection.getConnectionCounter());
                }
            }
        }
    };

    private static final int MIN_ALARM_INTERVAL = 5*60*1000;

    private static Context sContext;
    private static PendingIntent sPendingIntent;
    private static AlarmManager sAlarmManager;
    private static long sIntervalMillis;
    private static boolean sLastPingFailed;
    private static long sLastSuccess;

    /**
     * Register a pending intent with the AlarmManager to be broadcasted every
     * half hour and register the alarm broadcast receiver to receive this
     * intent. The receiver will check all known questions if a ping is
     * Necessary when invoked by the alarm intent.
     *
     * @param context
     */
    public static void onCreate(Context context) {
        sContext = context;
        context.registerReceiver(ALARM_BROADCAST_RECEIVER, new IntentFilter(PING_ALARM_ACTION));
        sAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        sPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(PING_ALARM_ACTION), 0);
        onConnected();
    }

    public static void onConnected() {
        if (sContext != null) {
            // setup first alarm using last value from preference
            setupAlarmManager(Preferences.getPingAlarmInterval(sContext, AlarmManager.INTERVAL_HALF_HOUR));
        }
    }

    private static boolean isLastPingFailed() {
        return sLastPingFailed;
    }

    /**
     * Called by the ping failed listener.
     * It will half the interval for the next alarm.
     */
    public static void pingFailed() {
        sLastPingFailed = true;
        setupAlarmManager(sIntervalMillis / 2);
    }

    /**
     * Called when a ping has succeeded.
     * In order to avoid a too much optimistic approach, we wait for at least
     * the supposed ping interval to pass before incrementing back the interval
     * for the next ping.
     */
    public static void pingSuccess() {
        sLastPingFailed = false;
        long now = System.currentTimeMillis();
        long diff = now - sLastSuccess;
        long nextAlarm;
        if (diff >= sIntervalMillis) {
            sLastSuccess = now;
            nextAlarm = (long) (sIntervalMillis * 1.5);
        }
        else {
            nextAlarm = sIntervalMillis;
        }
        setupAlarmManager(nextAlarm);
    }

    private static void setupAlarmManager(long intervalMillis) {
        if (sPendingIntent != null && sIntervalMillis != intervalMillis) {
            sAlarmManager.cancel(sPendingIntent);
            sIntervalMillis = intervalMillis;

            // do not go beyond 30 minutes...
            if (sIntervalMillis > AlarmManager.INTERVAL_HALF_HOUR) {
                sIntervalMillis = AlarmManager.INTERVAL_HALF_HOUR;
            }
            // ...or less than 5 minutes
            else if (sIntervalMillis < MIN_ALARM_INTERVAL) {
                sIntervalMillis = MIN_ALARM_INTERVAL;
            }

            // save value to preference for later retrieval
            Preferences.setPingAlarmInterval(sContext, sIntervalMillis);

            LOGGER.log(Level.WARNING, "Setting alarm for next ping to " + sIntervalMillis + " ms");
            sAlarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + sIntervalMillis,
                intervalMillis, sPendingIntent);
        }
    }

    /**
     * Unregister the alarm broadcast receiver and cancel the alarm.
     */
    public static void onDestroy() {
        sContext.unregisterReceiver(ALARM_BROADCAST_RECEIVER);
        sAlarmManager.cancel(sPendingIntent);
        sPendingIntent = null;
    }

}

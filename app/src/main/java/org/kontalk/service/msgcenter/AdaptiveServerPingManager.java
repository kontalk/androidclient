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

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.Manager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smackx.ping.PingManager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;

import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;


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

    public static AdaptiveServerPingManager getInstanceFor(XMPPConnection connection) {
        synchronized (INSTANCES) {
            AdaptiveServerPingManager serverPingWithAlarmManager = INSTANCES.get(connection);
            if (serverPingWithAlarmManager == null) {
                serverPingWithAlarmManager = new AdaptiveServerPingManager(connection);
                INSTANCES.put(connection, serverPingWithAlarmManager);
            }
            return serverPingWithAlarmManager;
        }
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
            synchronized (INSTANCES) {
                Iterator<XMPPConnection> it = INSTANCES.keySet().iterator();
                while (it.hasNext()) {
                    final XMPPConnection connection = it.next();
                    if (getInstanceFor(connection).isEnabled()) {
                        LOGGER.fine("Calling pingServerIfNecessary for connection "
                            + connection.getConnectionCounter());
                        final PingManager pingManager = PingManager.getInstanceFor(connection);
                        pingManager.setPingInterval(0);
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
                                try {
                                    if (pingManager.pingMyServer(true)) {
                                        pingSuccess(connection);
                                    }
                                    else {
                                        pingFailed(connection);
                                    }
                                }
                                catch (SmackException.NotConnectedException e) {
                                    // ignored
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
        }
    };

    private static final int MIN_ALARM_INTERVAL = 90 * 1000;

    private static Context sContext;
    private static PendingIntent sPendingIntent;
    private static AlarmManager sAlarmManager;

    // All values are in milliseconds.

    /**
     * Current ping interval.
     */
    private static long sInterval;
    /**
     * Timestamp of last ping success.
     */
    private static long sLastSuccess;
    /**
     * Last successful ping interval.
     */
    private static long sLastSuccessInterval;
    /**
     * Interval for the next increase.
     */
    private static long sNextIncrease;

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
        onConnected(null);
    }

    public static void onConnected(XMPPConnection connection) {
        if (sContext != null) {
            // setup first alarm using last value from preference
            setupAlarmManager(connection, Preferences.getPingAlarmInterval(sContext, AlarmManager.INTERVAL_HALF_HOUR));
            // next increase can happen at least at next interval
            sNextIncrease = Preferences.getPingAlarmBackoff(sContext, sInterval);
            // reset internal variables
            sLastSuccess = 0;
            sLastSuccessInterval = 0;
        }
    }

    /**
     * Called by the ping failed listener.
     * It will half the interval for the next alarm.
     */
    public static void pingFailed(XMPPConnection connection) {
        long interval;

        if (sLastSuccessInterval > 0) {
            // we were trying an increase, go back to previous value
            interval = sLastSuccessInterval;
            // better use the previous value for a longer time :)
            setNextIncreaseInterval((long) (sNextIncrease * 1.5));
        }
        else {
            // half interval
            interval = sInterval / 2;
        }
        setupAlarmManager(connection, interval);
    }

    /**
     * Called when a ping has succeeded.
     * In order to avoid a too much optimistic approach, we wait for at least
     * the supposed ping interval to pass before incrementing back the interval
     * for the next ping.
     */
    public static void pingSuccess(XMPPConnection connection) {
        long nextAlarm = sInterval;
        long now = SystemClock.elapsedRealtime();

        if (sLastSuccessInterval > 0) {
            // interval increase was successful, reset backoff
            setNextIncreaseInterval(sInterval);

            // interval increase was successful
        }
        // try an increase only if we previously had a successful ping
        else if (sLastSuccess > 0) {
            long diff = now - sLastSuccess;
            if (diff >= sNextIncrease) {
                // we are trying an increase, store the last successful interval
                sLastSuccessInterval = sInterval;

                nextAlarm = (long) (sInterval * 1.5);
            }

            // do not increase interval for now
        }

        // remember last success
        sLastSuccess = now;

        setupAlarmManager(connection, nextAlarm);
    }

    private static void setupAlarmManager(XMPPConnection connection, long intervalMillis) {
        if (sPendingIntent != null) {
            sAlarmManager.cancel(sPendingIntent);
            sInterval = intervalMillis;

            // do not go beyond 30 minutes...
            if (sInterval > AlarmManager.INTERVAL_HALF_HOUR) {
                sInterval = AlarmManager.INTERVAL_HALF_HOUR;
            }
            // ...or less than 90 seconds
            else if (sInterval < MIN_ALARM_INTERVAL) {
                sInterval = MIN_ALARM_INTERVAL;
            }

            // save value to preference for later retrieval
            Preferences.setPingAlarmInterval(sContext, sInterval);

            // remove difference from last received stanza
            long interval = sInterval;
            if (connection != null) {
                long now = System.currentTimeMillis();
                long lastStanza = connection.getLastStanzaReceived();
                if (lastStanza > 0)
                    interval -= (now - lastStanza);
            }

            LOGGER.log(Level.WARNING, "Setting alarm for next ping to " + sInterval + " ms (real " + interval + " ms)");

            if (SystemUtils.isOnWifi(sContext)) {
                // when on WiFi we can afford an inexact ping (carrier will not destroy our connection)
                sAlarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + interval,
                    interval, sPendingIntent);
            }
            else {
                // when on mobile network, we need exact ping timings
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                    sAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + interval,
                        sPendingIntent);
                } else {
                    sAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + interval,
                        sPendingIntent);
                }
            }
        }
    }

    private static void setNextIncreaseInterval(long interval) {
        // reset last successful interval
        sLastSuccessInterval = 0;
        // set and save next increase
        sNextIncrease = interval;
        Preferences.setPingAlarmBackoff(sContext, sNextIncrease);
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

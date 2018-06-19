/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import org.kontalk.Log;
import org.kontalk.reporting.ReportingManager;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_CONNECTED;
import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_PRESENCE;
import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_ROSTER_LOADED;


/**
 * A client for the message center.
 * It converts the broadcast receiver pattern into a simpler, easier to use
 * listener pattern. It also takes care of a few things so users of the
 * message center don't have to deal with all the intent extras and everything.
 * @author Daniele Ricci
 */
@SuppressWarnings("UnusedReturnValue")
public class MessageCenterClient {
    public static final String TAG = MessageCenterService.class.getSimpleName();

    private static MessageCenterClient sInstance;

    private List<ConnectionLifecycleListener> mConnectionListeners;
    private List<PresenceListener> mPresenceListeners;
    private Map<String, List<PresenceListener>> mPresenceListenersMap;

    private boolean mRegistered;
    private LocalBroadcastManager mBroadcasts;
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

            switch (action) {
                case ACTION_CONNECTED:
                    notifyConnected();
                    break;
                /*
                case ACTION_DISCONNECTED:
                    notifyDisconnected();
                    break;
                 */
                case ACTION_ROSTER_LOADED:
                    notifyRosterLoaded();
                    break;
                case ACTION_PRESENCE:
                    notifyPresence(intent.getExtras());
            }
        }
    };

    public synchronized static MessageCenterClient getInstance(Context context) {
        if (sInstance == null)
            sInstance = new MessageCenterClient(context);
        return sInstance;
    }

    private MessageCenterClient(Context context) {
        mBroadcasts = LocalBroadcastManager.getInstance(context);
        mConnectionListeners = new LinkedList<>();
        mPresenceListeners = new LinkedList<>();
        mPresenceListenersMap = new HashMap<>();
    }

    private void registerEvents() {
        if (!mRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_PRESENCE);
            filter.addAction(ACTION_CONNECTED);
            //filter.addAction(ACTION_DISCONNECTED);
            filter.addAction(ACTION_ROSTER_LOADED);

            mBroadcasts.registerReceiver(mReceiver, filter);
            mRegistered = true;
        }
    }

    private void unregisterEvents() {
        if (mRegistered) {
            mBroadcasts.unregisterReceiver(mReceiver);
            mRegistered = false;
        }
    }

    private void maybeUnregisterEvents() {
        // just a small optimization for when Kontalk is idle (UI not visible)
        // we don't need any notification in that case, so why remain active?
        if (mRegistered && !hasListeners()) {
            unregisterEvents();
        }
    }

    private boolean hasListeners() {
        return mConnectionListeners.size() > 0 ||
            mPresenceListeners.size() > 0 ||
            mPresenceListenersMap.size() > 0;
    }

    @MainThread
    void notifyConnected() {
        for (ConnectionLifecycleListener l : mConnectionListeners) {
            l.onConnected();
        }
    }

    @MainThread
    void notifyRosterLoaded() {
        for (ConnectionLifecycleListener l : mConnectionListeners) {
            l.onRosterLoaded();
        }
    }

    @MainThread
    void notifyPresence(Bundle data) {
        String from = data.getString(MessageCenterService.EXTRA_FROM);
        String bareFrom = from != null ? XmppStringUtils.parseBareJid(from) : null;

        if (from != null && (mPresenceListeners.size() > 0 || mPresenceListenersMap.containsKey(bareFrom))) {
            // we handle only (un)available presence stanzas
            String type = data.getString(MessageCenterService.EXTRA_TYPE);
            Presence.Type presenceType = (type != null) ? Presence.Type.fromString(type) : null;

            String mode = data.getString(MessageCenterService.EXTRA_SHOW);
            Presence.Mode presenceMode = (mode != null) ? Presence.Mode.fromString(mode) : null;
            int priority = data.getInt(MessageCenterService.EXTRA_PRIORITY, -1);

            String status = data.getString(MessageCenterService.EXTRA_STATUS);
            long _delay = data.getLong(MessageCenterService.EXTRA_STAMP);
            Date delay = _delay > 0 ? new Date(_delay) : null;

            String rosterName = data.getString(MessageCenterService.EXTRA_ROSTER_NAME);
            boolean subscribedFrom = data.getBoolean(MessageCenterService.EXTRA_SUBSCRIBED_FROM, false);
            boolean subscribedTo = data.getBoolean(MessageCenterService.EXTRA_SUBSCRIBED_TO, false);

                String fingerprint = data.getString(MessageCenterService.EXTRA_FINGERPRINT);

            Jid fromJid;
            try {
                fromJid = JidCreate.from(from);
            }
            catch (XmppStringprepException e) {
                Log.w(TAG, "unable to parse JID: " + from);
                // this is serious, report it for analysis
                ReportingManager.logException(e);
                return;
            }

            // notify global listeners first
            for (PresenceListener l : mPresenceListeners) {
                l.onPresence(fromJid,
                    presenceType, presenceMode, priority,
                    status, delay,
                    rosterName, subscribedFrom, subscribedTo,
                    fingerprint
                    );
            }

            // go ahead with selective listeners
            List<PresenceListener> listeners = mPresenceListenersMap.get(bareFrom);
            if (listeners != null) {
                for (PresenceListener l : listeners) {
                    l.onPresence(fromJid,
                        presenceType, presenceMode, priority,
                        status, delay,
                        rosterName, subscribedFrom, subscribedTo,
                        fingerprint
                    );
                }
            }
        }
    }

    public MessageCenterClient addConnectionLifecycleListener(@NonNull ConnectionLifecycleListener l) {
        mConnectionListeners.add(l);
        registerEvents();
        return this;
    }

    public MessageCenterClient removeConnectionLifecycleListener(@NonNull ConnectionLifecycleListener l) {
        mConnectionListeners.remove(l);
        maybeUnregisterEvents();
        return this;
    }

    public MessageCenterClient addGlobalPresenceListener(@NonNull PresenceListener l) {
        mPresenceListeners.add(l);
        registerEvents();
        return this;
    }

    public MessageCenterClient removeRemovePresenceListener(@NonNull PresenceListener l) {
        mPresenceListeners.remove(l);
        maybeUnregisterEvents();
        return this;
    }

    public MessageCenterClient addPresenceListener(@NonNull PresenceListener l, @NonNull String from) {
        List<PresenceListener> listeners = mPresenceListenersMap.get(from);
        if (listeners == null) {
            listeners = new LinkedList<>();
            mPresenceListenersMap.put(from, listeners);
        }
        listeners.add(l);
        registerEvents();
        return this;
    }

    public MessageCenterClient removePresenceListener(@NonNull PresenceListener l, @NonNull String from) {
        List<PresenceListener> listeners = mPresenceListenersMap.get(from);
        if (listeners != null) {
            listeners.remove(l);
            if (listeners.size() == 0)
                mPresenceListenersMap.remove(from);
        }
        maybeUnregisterEvents();
        return this;
    }

    // TODO request[...] via generic one-off listeners, linked with stanza ID

    /** Listener for connection-related events. */
    public interface ConnectionLifecycleListener {
        void onConnected();
        void onDisconnected();
        void onRosterLoaded();
    }

    public interface PresenceListener {
        void onPresence(Jid from,
            // presence data
            Presence.Type type, Presence.Mode mode, int priority,
            // status/delay
            String status, Date delay,
            // roster data
            String rosterName, boolean subscribedFrom, boolean subscribedTo,
            // other
            String fingerprint);
    }

}

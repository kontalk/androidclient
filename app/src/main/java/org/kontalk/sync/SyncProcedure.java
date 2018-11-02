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

package org.kontalk.sync;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.util.StringUtils;
import org.jxmpp.util.XmppStringUtils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.event.ConnectedEvent;


/**
 * The sync procedure.
 * @author Daniele Ricci
 */
public class SyncProcedure extends BroadcastReceiver {
    // using SyncAdapter tag
    private static final String TAG = SyncAdapter.TAG;

    /** Random packet id used for requesting public keys. */
    static final String IQ_KEYS_PACKET_ID = StringUtils.randomString(10);

    /** Random packet id used for requesting the blocklist. */
    static final String IQ_BLOCKLIST_PACKET_ID = StringUtils.randomString(10);

    /** Max number of items in a roster match request. */
    private static final int MAX_ROSTER_MATCH_SIZE = 500;

    private List<PresenceItem> response;
    private final WeakReference<Syncer> notifyTo;

    private final List<String> jidList;
    private int rosterParts = -1;
    private String[] iq;
    private String presenceId;

    private int presenceCount;
    private int pubkeyCount;
    private int rosterCount;
    /** Packet id list for not matched contacts (in roster but not matched on server). */
    private Set<String> notMatched = new HashSet<>();
    private boolean blocklistReceived;

    public final static class PresenceItem {
        public String from;
        public String status;
        public String rosterName;
        public long timestamp;
        public byte[] publicKey;
        public boolean blocked;
        public boolean presence;
        /** True if found during roster match. */
        public boolean matched;
        /** Discard this entry: it has not been found on server. */
        public boolean discarded;
    }

    public SyncProcedure(List<String> jidList, Syncer notifyTo) {
        this.notifyTo = new WeakReference<>(notifyTo);
        this.jidList = jidList;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    public void onConnected(ConnectedEvent event) {
        Syncer w = notifyTo.get();
        if (w != null) {
            // request a roster match
            rosterParts = getRosterParts(jidList);
            iq = new String[rosterParts];
            for (int i = 0; i < rosterParts; i++) {
                int end = (i+1)*MAX_ROSTER_MATCH_SIZE;
                if (end >= jidList.size())
                    end = jidList.size();
                List<String> slice = jidList.subList(i*MAX_ROSTER_MATCH_SIZE, end);

                iq[i] = StringUtils.randomString(6);
                w.requestRosterMatch(iq[i], slice);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (MessageCenterService.ACTION_PRESENCE.equals(action)) {

            // consider only presences received *after* roster response
            if (response != null && presenceId != null) {

                String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                String type = intent.getStringExtra(MessageCenterService.EXTRA_TYPE);
                String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                if (type != null && presenceId.equals(id)) {
                    // update presence item data
                    String bareJid = XmppStringUtils.parseBareJid(jid);
                    PresenceItem item = getPresenceItem(bareJid);
                    item.status = intent.getStringExtra(MessageCenterService.EXTRA_STATUS);
                    item.timestamp = intent.getLongExtra(MessageCenterService.EXTRA_STAMP, -1);
                    item.rosterName = intent.getStringExtra(MessageCenterService.EXTRA_ROSTER_NAME);
                    if (!item.presence) {
                        item.presence = true;
                        // increment presence count
                        presenceCount++;
                        // check user existance (only if subscription is "both")
                        if (!item.matched && intent.getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_FROM, false) &&
                            intent.getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_TO, false)) {
                            // verify actual user existance through last activity
                            String lastActivityId = StringUtils.randomString(6);
                            MessageCenterService.requestLastActivity(context, item.from, lastActivityId);
                            notMatched.add(lastActivityId);
                        }
                    }
                }
            }
        }

        // roster match result received
        else if (MessageCenterService.ACTION_ROSTER_MATCH.equals(action)) {
            String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
            for (String iqId : iq) {
                if (iqId.equals(id)) {
                    // decrease roster parts counter
                    rosterParts--;

                    String[] list = intent.getStringArrayExtra(MessageCenterService.EXTRA_JIDLIST);
                    if (list != null) {
                        rosterCount += list.length;
                        if (response == null) {
                            // prepare list to be filled in with presence data
                            response = new ArrayList<>(rosterCount);
                        }
                        for (String jid : list) {
                            PresenceItem p = new PresenceItem();
                            p.from = jid;
                            p.matched = true;
                            response.add(p);
                        }
                    }

                    if (rosterParts <= 0) {
                        // all roster parts received

                        if (rosterCount == 0 && blocklistReceived) {
                            // no roster elements
                            finish();
                        }
                        else {
                            Syncer w = notifyTo.get();
                            if (w != null) {
                                // request presence data for the whole roster
                                presenceId = StringUtils.randomString(6);
                                w.requestPresenceData(presenceId);
                                // request public keys for the whole roster
                                w.requestPublicKeys();
                                // request block list
                                w.requestBlocklist();
                            }
                        }
                    }

                    // no need to continue
                    break;
                }
            }
        }

        else if (MessageCenterService.ACTION_PUBLICKEY.equals(action)) {
            if (response != null) {
                String requestId = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                if (IQ_KEYS_PACKET_ID.equals(requestId)) {
                    String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                    // see if bare JID is present in roster response
                    String compare = XmppStringUtils.parseBareJid(jid);
                    for (PresenceItem item : response) {
                        if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(compare)) {
                            item.publicKey = intent.getByteArrayExtra(MessageCenterService.EXTRA_PUBLIC_KEY);

                            // increment vcard count
                            pubkeyCount++;
                            break;
                        }
                    }

                    // done with presence data and blocklist
                    if (pubkeyCount == presenceCount && blocklistReceived && notMatched.size() == 0)
                        finish();
                }
            }

        }

        else if (MessageCenterService.ACTION_BLOCKLIST.equals(action)) {
            String requestId = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
            if (IQ_BLOCKLIST_PACKET_ID.equals(requestId)) {
                blocklistReceived = true;

                String[] list = intent.getStringArrayExtra(MessageCenterService.EXTRA_BLOCKLIST);
                if (list != null) {

                    for (String jid : list) {
                        // see if bare JID is present in roster response
                        String compare = XmppStringUtils.parseBareJid(jid);
                        for (PresenceItem item : response) {
                            if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(compare)) {
                                item.blocked = true;

                                break;
                            }
                        }
                    }

                }

                // done with presence data and blocklist
                if (pubkeyCount >= presenceCount && notMatched.size() == 0)
                    finish();
            }
        }

        // last activity (for user existance verification)
        else if (MessageCenterService.ACTION_LAST_ACTIVITY.equals(action)) {
            String requestId = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
            if (notMatched.contains(requestId)) {
                notMatched.remove(requestId);

                String type = intent.getStringExtra(MessageCenterService.EXTRA_TYPE);
                // consider only item-not-found (404) errors
                if (type != null && type.equalsIgnoreCase(IQ.Type.error.toString()) &&
                        StanzaError.Condition.item_not_found.toString().equals(intent
                            .getStringExtra(MessageCenterService.EXTRA_ERROR_CONDITION))) {
                    // user does not exist!
                    String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                    // discard entry
                    discardPresenceItem(jid);
                    // unsubscribe!
                    unsubscribe(context, jid);

                    if (pubkeyCount >= presenceCount && blocklistReceived && notMatched.size() == 0)
                        finish();
                }
            }
        }
    }

    private void discardPresenceItem(String jid) {
        for (PresenceItem item : response) {
            if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(jid)) {
                item.discarded = true;
                return;
            }
        }
    }

    private PresenceItem getPresenceItem(String jid) {
        for (PresenceItem item : response) {
            if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(jid))
                return item;
        }

        // add item if not found
        PresenceItem item = new PresenceItem();
        item.from = jid;
        response.add(item);
        return item;
    }

    private void unsubscribe(Context context, String jid) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_PRESENCE);
        i.putExtra(MessageCenterService.EXTRA_TO, jid);
        i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.unsubscribe.name());
        MessageCenterService.startService(context, i);
    }

    private int getRosterParts(List<String> jidList) {
        return (int) Math.ceil((double) jidList.size() / MAX_ROSTER_MATCH_SIZE);
    }

    public List<PresenceItem> getResponse() {
        return (rosterCount >= 0) ? response : null;
    }

    private void finish() {
        Syncer w = notifyTo.get();
        if (w != null) {
            synchronized (w) {
                w.notifyAll();
            }
        }
    }

}

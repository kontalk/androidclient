/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.util.StringUtils;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.event.BlocklistEvent;
import org.kontalk.service.msgcenter.event.BlocklistRequest;
import org.kontalk.service.msgcenter.event.ConnectedEvent;
import org.kontalk.service.msgcenter.event.DisconnectedEvent;
import org.kontalk.service.msgcenter.event.LastActivityEvent;
import org.kontalk.service.msgcenter.event.LastActivityRequest;
import org.kontalk.service.msgcenter.event.PresenceEvent;
import org.kontalk.service.msgcenter.event.PresenceRequest;
import org.kontalk.service.msgcenter.event.PublicKeyEvent;
import org.kontalk.service.msgcenter.event.PublicKeyRequest;
import org.kontalk.service.msgcenter.event.RosterMatchEvent;
import org.kontalk.service.msgcenter.event.RosterMatchRequest;
import org.kontalk.service.msgcenter.event.UnsubscribeRequest;


/**
 * The sync procedure.
 * @author Daniele Ricci
 */
public class SyncProcedure {
    // using SyncAdapter tag
    private static final String TAG = SyncAdapter.TAG;

    /** Random packet id used for requesting public keys. */
    static final String IQ_KEYS_PACKET_ID = StringUtils.randomString(10);

    /** Random packet id used for requesting the blocklist. */
    @VisibleForTesting
    static final String IQ_BLOCKLIST_PACKET_ID = StringUtils.randomString(10);

    /** Max number of items in a roster match request. */
    private static final int MAX_ROSTER_MATCH_SIZE = 500;

    private List<PresenceItem> mResponse;
    private final WeakReference<Object> mNotifyTo;

    private final List<String> mJidList;
    private int mRosterParts = -1;
    private String[] mIqIds;
    private String mPresenceId;

    private int mPresenceCount;
    private int mPubkeyCount;
    private int mRosterCount;
    /** Packet id list for not matched contacts (in roster but not matched on server). */
    private Set<String> mNotMatched = new HashSet<>();
    private boolean mNlocklistReceived;

    private EventBus mServiceBus = MessageCenterService.bus();

    public final static class PresenceItem {
        public BareJid from;
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

        /** Mainly for tests. */
        @Override
        public boolean equals(@Nullable Object obj) {
            return (obj instanceof PresenceItem) &&
                ((PresenceItem) obj).from.equals(from) &&
                StringUtils.nullSafeCharSequenceEquals(((PresenceItem) obj).status, status) &&
                StringUtils.nullSafeCharSequenceEquals(((PresenceItem) obj).rosterName, rosterName) &&
                ((PresenceItem) obj).timestamp == timestamp &&
                // too much? -- ((PresenceItem) obj).publicKey...
                ((PresenceItem) obj).blocked == blocked &&
                ((PresenceItem) obj).presence == presence &&
                ((PresenceItem) obj).matched == matched &&
                ((PresenceItem) obj).discarded == discarded;
        }
    }

    SyncProcedure(List<String> jidList, Object notifyTo) {
        mNotifyTo = new WeakReference<>(notifyTo);
        mJidList = jidList;
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    public synchronized void onConnected(ConnectedEvent event) {
        Object w = mNotifyTo.get();
        if (w != null) {
            // request a roster match
            mRosterParts = getRosterParts(mJidList);
            mIqIds = new String[mRosterParts];
            for (int i = 0; i < mRosterParts; i++) {
                int end = (i+1)*MAX_ROSTER_MATCH_SIZE;
                if (end >= mJidList.size())
                    end = mJidList.size();
                List<String> slice = mJidList.subList(i*MAX_ROSTER_MATCH_SIZE, end);

                mIqIds[i] = StringUtils.randomString(6);

                mServiceBus.post(new RosterMatchRequest(mIqIds[i], slice.toArray(new String[0])));
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    public synchronized void onDisconnected(DisconnectedEvent event) {
        mResponse = null;
        finish();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public synchronized void onPresence(PresenceEvent event) {
        // consider only presences received *after* roster response
        if (mResponse != null && mPresenceId != null) {
            if (event.type != null && mPresenceId.equals(event.id)) {
                // update presence item data
                BareJid bareJid = event.jid.asBareJid();
                PresenceItem item = getPresenceItem(bareJid);
                item.status = event.status;
                item.timestamp = event.delay != null ? event.delay.getTime() : -1;
                item.rosterName = event.rosterName;
                if (!item.presence) {
                    item.presence = true;
                    // increment presence count
                    mPresenceCount++;
                    // check user existance (only if subscription is "both")
                    if (!item.matched && event.subscribedFrom && event.subscribedTo) {
                        // verify actual user existance through last activity
                        String lastActivityId = StringUtils.randomString(6);
                        requestLastActivity(item.from, lastActivityId);
                        mNotMatched.add(lastActivityId);
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public synchronized void onRosterMatch(RosterMatchEvent event) {
        // TODO handle errors
        for (String iqId : mIqIds) {
            if (iqId.equals(event.id)) {
                // decrease roster parts counter
                mRosterParts--;

                if (event.jids != null) {
                    mRosterCount += event.jids.length;
                    if (mResponse == null) {
                        // prepare list to be filled in with presence data
                        mResponse = new ArrayList<>(mRosterCount);
                    }
                    for (Jid jid : event.jids) {
                        PresenceItem p = new PresenceItem();
                        p.from = jid.asBareJid();
                        p.matched = true;
                        mResponse.add(p);
                    }
                }

                if (mRosterParts <= 0) {
                    // all roster parts received

                    if (mRosterCount == 0 && mNlocklistReceived) {
                        // no roster elements
                        finish();
                    }
                    else {
                        Object w = mNotifyTo.get();
                        if (w != null) {
                            // request presence data for the whole roster
                            mPresenceId = StringUtils.randomString(6);
                            requestPresenceData(mPresenceId);
                            // request public keys for the whole roster
                            requestPublicKeys(IQ_KEYS_PACKET_ID);
                            // request block list
                            requestBlocklist(IQ_BLOCKLIST_PACKET_ID);
                        }
                    }
                }

                // no need to continue
                break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public synchronized void onBlocklist(BlocklistEvent event) {
        if (IQ_BLOCKLIST_PACKET_ID.equals(event.id)) {
            mNlocklistReceived = true;

            if (event.jids != null) {
                for (Jid jid : event.jids) {
                    // see if bare JID is present in roster response
                    BareJid compare = jid.asBareJid();
                    for (PresenceItem item : mResponse) {
                        if (item.from.equals(compare)) {
                            item.blocked = true;
                            break;
                        }
                    }
                }
            }

            // done with presence data and blocklist
            if (mPubkeyCount >= mPresenceCount && mNotMatched.size() == 0)
                finish();
        }
    }

    /** Last activity (for user existance verification). */
    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public synchronized void onLastActivity(LastActivityEvent event) {
        if (mNotMatched.contains(event.id)) {
            mNotMatched.remove(event.id);

            // consider only item-not-found (404) errors
            if (event.getStanzaErrorCondition() == StanzaError.Condition.item_not_found) {
                // user does not exist!
                // discard entry
                discardPresenceItem(event.jid.asBareJid());
                // unsubscribe!
                unsubscribe(event.jid.asBareJid());

                if (mPubkeyCount >= mPresenceCount && mNlocklistReceived && mNotMatched.size() == 0)
                    finish();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public synchronized void onPublicKey(PublicKeyEvent event) {
        if (mResponse != null) {
            if (IQ_KEYS_PACKET_ID.equals(event.id)) {
                // see if bare JID is present in roster response
                BareJid compare = event.jid.asBareJid();
                for (PresenceItem item : mResponse) {
                    if (item.from.equals(compare)) {
                        item.publicKey = event.publicKey;

                        // increment vcard count
                        mPubkeyCount++;
                        break;
                    }
                }

                // done with presence data and blocklist
                if (mPubkeyCount == mPresenceCount && mNlocklistReceived && mNotMatched.size() == 0)
                    finish();
            }
        }
    }

    private void discardPresenceItem(BareJid jid) {
        for (PresenceItem item : mResponse) {
            if (item.from.equals(jid)) {
                item.discarded = true;
                return;
            }
        }
    }

    private PresenceItem getPresenceItem(BareJid jid) {
        for (PresenceItem item : mResponse) {
            if (item.from.equals(jid))
                return item;
        }

        // add item if not found
        PresenceItem item = new PresenceItem();
        item.from = jid;
        mResponse.add(item);
        return item;
    }

    private void requestPresenceData(String id) {
        mServiceBus.post(new PresenceRequest(id, null));
    }

    private void requestLastActivity(BareJid jid, String id) {
        mServiceBus.post(new LastActivityRequest(id, jid));
    }

    private void requestPublicKeys(String id) {
        mServiceBus.post(new PublicKeyRequest(id, null));
    }

    private void requestBlocklist(String id) {
        mServiceBus.post(new BlocklistRequest(id));
    }

    private void unsubscribe(BareJid jid) {
        mServiceBus.post(new UnsubscribeRequest(jid));
    }

    private int getRosterParts(List<String> jidList) {
        return (int) Math.ceil((double) jidList.size() / MAX_ROSTER_MATCH_SIZE);
    }

    public List<PresenceItem> getResponse() {
        return (mRosterCount >= 0) ? mResponse : null;
    }

    private void finish() {
        Object w = mNotifyTo.get();
        if (w != null) {
            synchronized (w) {
                w.notifyAll();
            }
        }
    }

}

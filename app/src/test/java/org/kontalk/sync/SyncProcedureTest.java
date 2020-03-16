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

import java.util.Arrays;
import java.util.List;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.packet.Presence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.event.BlocklistEvent;
import org.kontalk.service.msgcenter.event.BlocklistRequest;
import org.kontalk.service.msgcenter.event.ConnectedEvent;
import org.kontalk.service.msgcenter.event.DisconnectedEvent;
import org.kontalk.service.msgcenter.event.PresenceEvent;
import org.kontalk.service.msgcenter.event.PresenceRequest;
import org.kontalk.service.msgcenter.event.PublicKeyEvent;
import org.kontalk.service.msgcenter.event.PublicKeyRequest;
import org.kontalk.service.msgcenter.event.RosterMatchEvent;
import org.kontalk.service.msgcenter.event.RosterMatchRequest;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;


public class SyncProcedureTest {

    private SyncProcedure mSync;
    private EventBus mBus = MessageCenterService.bus();

    @Before
    public void setUp() {
        List<String> jidList = Arrays.asList(
            "alice@prime.kontalk.net",
            "bob@prime.kontalk.net",
            "charlie@prime.kontalk.net",
            "dave@prime.kontalk.net"
        );
        mSync = new SyncProcedure(jidList, this);
        mBus.register(mSync);
    }

    @After
    public void tearDown() {
        mBus.unregister(mSync);
    }

    @Test
    public void testNiceSync() throws InterruptedException {
        TestNiceSyncListener listener = new TestNiceSyncListener();
        mBus.register(listener);

        mBus.postSticky(new ConnectedEvent());

        synchronized (this) {
            wait(3000);
        }
        mBus.unregister(listener);

        SyncProcedure.PresenceItem alice = new SyncProcedure.PresenceItem();
        alice.from = JidCreate.bareFromOrThrowUnchecked("alice@prime.kontalk.net");
        alice.matched = true;
        alice.presence = true;
        alice.blocked = false;
        alice.discarded = false;
        alice.rosterName = "alice";
        alice.status = null;
        alice.timestamp = -1;

        SyncProcedure.PresenceItem bob = new SyncProcedure.PresenceItem();
        bob.from = JidCreate.bareFromOrThrowUnchecked("bob@prime.kontalk.net");
        bob.matched = true;
        bob.presence = true;
        bob.blocked = false;
        bob.discarded = false;
        bob.rosterName = "bob";
        bob.status = null;
        bob.timestamp = -1;

        assertThat(mSync.getResponse(),
            hasItems(
                alice,
                bob
            ));
    }

    public class TestNiceSyncListener {

        @Subscribe(threadMode = ThreadMode.BACKGROUND)
        public void onRosterMatchRequest(RosterMatchRequest request) {
            mBus.post(new RosterMatchEvent(new Jid[]{
                JidCreate.fromOrThrowUnchecked("alice@prime.kontalk.net"),
                JidCreate.fromOrThrowUnchecked("bob@prime.kontalk.net")
            }, request.id));
        }

        @Subscribe(threadMode = ThreadMode.BACKGROUND)
        public void onPresenceRequest(PresenceRequest request) {
            mBus.post(new PresenceEvent(JidCreate.fromOrThrowUnchecked("alice@prime.kontalk.net"),
                Presence.Type.available, Presence.Mode.available, 0, null, null, "alice", true, true,
                "AABBCCDDEEFF", request.id));
            mBus.post(new PresenceEvent(JidCreate.fromOrThrowUnchecked("bob@prime.kontalk.net"),
                Presence.Type.unavailable, null, 0, null, null, "bob", true, true,
                "FFEEDDCCBBAA", request.id));
        }

        @Subscribe(threadMode = ThreadMode.BACKGROUND)
        public void onPublicKeyRequest(PublicKeyRequest request) {
            mBus.post(new PublicKeyEvent(JidCreate.fromOrThrowUnchecked("alice@prime.kontalk.net"),
                new byte[] { 12, 14, 23, 55, 80 }, request.id));
            mBus.post(new PublicKeyEvent(JidCreate.fromOrThrowUnchecked("alice@prime.kontalk.net"),
                new byte[] { 45, 89, 4, 76, 12}, request.id));
        }

        @Subscribe(threadMode = ThreadMode.BACKGROUND)
        public void onBlocklistRequest(BlocklistRequest request) {
            mBus.post(new BlocklistEvent(new Jid[0], request.id));
        }

    }

    @Test
    public void testConnectionBombing() throws InterruptedException {
        TestNiceSyncListener listener = new TestNiceSyncListener();
        mBus.register(listener);

        for (int i = 0; i < 10; i++) {
            mBus.postSticky(new ConnectedEvent());
            Thread.sleep(50);
        }

        mBus.post(new DisconnectedEvent());

        synchronized (this) {
            wait(3000);
        }
        mBus.unregister(listener);

        assertNull(mSync.getResponse());
    }

}

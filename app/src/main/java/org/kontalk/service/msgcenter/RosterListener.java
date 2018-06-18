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

import java.lang.ref.WeakReference;
import java.util.Collection;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jxmpp.jid.Jid;

import android.content.Intent;
import android.os.Handler;

import org.kontalk.Log;
import org.kontalk.client.PublicKeyPublish;
import org.kontalk.data.Contact;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MyUsers;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_SUBSCRIBED;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TYPE;


/**
 * The roster listener.
 * @author Daniele Ricci
 */
public class RosterListener implements RosterLoadedListener, org.jivesoftware.smack.roster.RosterListener {
    private static final String TAG = RosterListener.class.getSimpleName();

    private WeakReference<MessageCenterService> mService;
    private WeakReference<PresenceListener> mPresenceListener;

    RosterListener(MessageCenterService service, PresenceListener presenceListener) {
        mService = new WeakReference<>(service);
        mPresenceListener = new WeakReference<>(presenceListener);
    }

    @Override
    public void onRosterLoaded(Roster roster) {
        final MessageCenterService service = mService.get();
        if (service == null)
            return;

        final Handler handler = service.mHandler;
        if (handler != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    // send pending subscription replies
                    service.sendPendingSubscriptionReplies();
                    // resend failed and pending messages
                    service.resendPendingMessages(false, false);
                    // resend failed and pending received receipts
                    service.resendPendingReceipts();
                    // roster has been loaded
                    service.broadcast(MessageCenterService.ACTION_ROSTER_LOADED);
                }
            });
        }
    }

    @Override
    public void onRosterLoadingFailed(Exception exception) {
        // ignored for know
        Log.d(TAG, "error loading roster", exception);
    }

    @Override
    public void entriesAdded(Collection<Jid> addresses) {
        // TODO something to do here?
    }

    @Override
    public void entriesUpdated(Collection<Jid> addresses) {
        final MessageCenterService service = mService.get();
        final PresenceListener presenceListener = mPresenceListener.get();
        if (service == null || presenceListener == null)
            return;

        // we got an updated roster entry
        // check if it's a subscription "both"
        for (Jid jid : addresses) {
            RosterEntry e = service.getRosterEntry(jid.asBareJid());
            if (e != null && e.canSeeHisPresence()) {
                userSubscribed(service, presenceListener, jid);
            }
        }
    }

    @Override
    public void entriesDeleted(Collection<Jid> addresses) {
        // TODO something to do here?
    }

    @Override
    public void presenceChanged(Presence presence) {
    }

    private void userSubscribed(MessageCenterService service, PresenceListener presenceListener, Jid jid) {
        String from = jid.asBareJid().toString();

        if (Keyring.getPublicKey(service, from, MyUsers.Keys.TRUST_UNKNOWN) == null) {
            // autotrust the key we are about to request
            // but set the trust level to ignored because we didn't really verify it
            Keyring.setAutoTrustLevel(service, from, MyUsers.Keys.TRUST_IGNORED);

            // public key not found
            // assuming the user has allowed us, request it

            PublicKeyPublish pkey = new PublicKeyPublish();
            pkey.setStanzaId();
            pkey.setTo(jid.asBareJid());

            PublicKeyListener listener = new PublicKeyListener(service, pkey);
            service.sendIqWithReply(pkey, true, listener, listener);
        }

        // invalidate cached contact
        Contact.invalidate(from);

        // send a broadcast
        Intent i = new Intent(ACTION_SUBSCRIBED);
        i.putExtra(EXTRA_TYPE, Presence.Type.subscribed.name());
        i.putExtra(EXTRA_FROM, jid.toString());

        service.sendBroadcast(i);

        // send any pending messages now
        presenceListener.resendPending(false, true, from);
    }

}

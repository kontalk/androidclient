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

package org.kontalk.service.msgcenter;

import java.lang.ref.WeakReference;
import java.util.Collection;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jxmpp.jid.Jid;

import android.os.Handler;

import org.kontalk.Log;
import org.kontalk.data.Contact;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MyUsers;
import org.kontalk.service.msgcenter.event.RosterLoadedEvent;
import org.kontalk.service.msgcenter.event.UserSubscribedEvent;


/**
 * The roster listener.
 * @author Daniele Ricci
 */
public class RosterListener implements RosterLoadedListener, org.jivesoftware.smack.roster.RosterListener {
    private static final String TAG = RosterListener.class.getSimpleName();

    private WeakReference<MessageCenterService> mService;

    RosterListener(MessageCenterService service) {
        mService = new WeakReference<>(service);
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
                    // roster has been loaded
                    MessageCenterService.bus().postSticky(new RosterLoadedEvent());
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
        final MessageCenterService service = mService.get();
        for (Jid jid : addresses) {
            if (Keyring.getPublicKey(service, jid.toString(), MyUsers.Keys.TRUST_UNKNOWN) == null) {
                // autotrust the first key we have
                // but set the trust level to ignored because we didn't really verify it
                Keyring.setAutoTrustLevel(service, jid.toString(), MyUsers.Keys.TRUST_IGNORED);
            }
        }
    }

    @Override
    public void entriesUpdated(Collection<Jid> addresses) {
        final MessageCenterService service = mService.get();
        if (service == null)
            return;

        // we got an updated roster entry
        // check if it's a subscription "both"
        for (Jid jid : addresses) {
            RosterEntry e = service.getRosterEntry(jid.asBareJid());
            if (e != null && e.canSeeHisPresence()) {
                userSubscribed(jid);
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

    private void userSubscribed(Jid jid) {
        String from = jid.asBareJid().toString();

        // invalidate cached contact
        Contact.invalidate(from);

        // send an event
        MessageCenterService.bus()
            .post(new UserSubscribedEvent(jid));

        // MessagesController will send any pending messages
    }

}

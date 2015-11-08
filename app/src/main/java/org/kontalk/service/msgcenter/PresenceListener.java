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

import java.io.IOException;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.kontalk.client.PublicKeyPresence;
import org.kontalk.client.PublicKeyPublish;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PGPUserID;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Threads.Requests;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.provider.UsersProvider;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.Preferences;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_PRESENCE;
import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_SUBSCRIBED;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PRIORITY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_ROSTER_NAME;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_SHOW;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_STAMP;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_STATUS;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TYPE;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FINGERPRINT;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_SUBSCRIBED_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_SUBSCRIBED_TO;


/**
 * Packet listener for presence stanzas.
 * @author Daniele Ricci
 */
class PresenceListener extends MessageCenterPacketListener {

    public PresenceListener(MessageCenterService instance) {
        super(instance);
    }

    private Stanza createSubscribed(Presence p) {
        ExtensionElement _pkey = p.getExtension(PublicKeyPresence.ELEMENT_NAME, PublicKeyPresence.NAMESPACE);

        try {

            if (_pkey instanceof PublicKeyPresence) {
                PublicKeyPresence pkey = (PublicKeyPresence) _pkey;

                byte[] keydata = pkey.getKey();
                // just to ensure it's valid data
                PGP.readPublicKeyring(keydata);

                String jid = XmppStringUtils.parseBareJid(p.getFrom());
                // store key to users table
                UsersProvider.setUserKey(getContext(), jid, keydata);
                // maybe trust the key
                UsersProvider.trustUserKey(getContext(), jid);
            }

            Presence p2 = new Presence(Presence.Type.subscribed);
            p2.setTo(p.getFrom());
            return p2;

        }
        catch (Exception e) {
            Log.w(MessageCenterService.TAG, "unable to accept subscription from user", e);
            // TODO should we notify the user about this?
            // TODO throw new PGPException(...)
            return null;
        }
    }

    @Override
    public void processPacket(Stanza packet) {
        try {
            Presence p = (Presence) packet;

            // presence subscription request
            if (p.getType() == Presence.Type.subscribe) {

                handleSubscribe(p);

            }

            // presence subscription response
            else if (p.getType() == Presence.Type.subscribed) {

                handleSubscribed(p);

            }

            /*
            else if (p.getType() == Presence.Type.unsubscribed) {
                // TODO can this even happen?
            }
            */

            else {

                handlePresence(p);

            }
        }
        catch (Exception e) {
            Log.e(MessageCenterService.TAG, "error parsing presence", e);
        }
    }

    private void handleSubscribe(Presence p)
            throws NotConnectedException, IOException, PGPException {

        Context ctx = getContext();

        // auto-accept subscription
        if (Preferences.getAutoAcceptSubscriptions(ctx) || isAlreadyTrusted(p)) {

            // TODO user database entry should be stored here too

            Stanza r = createSubscribed(p);
            if (r != null)
                getConnection().sendStanza(r);

        }

        // ask the user
        else {

            /*
             * Subscription procedure:
             * 1. update (or insert) users table with the public key just received
             * 2. update (or insert) threads table with a special subscription record
             * 3. user will either accept or refuse
             */

            String from = XmppStringUtils.parseBareJid(p.getFrom());

            // extract public key
            String name = null, fingerprint = null;
            byte[] publicKey = null;
            ExtensionElement _pkey = p.getExtension(PublicKeyPresence.ELEMENT_NAME, PublicKeyPresence.NAMESPACE);
            if (_pkey instanceof PublicKeyPresence) {
                PublicKeyPresence pkey = (PublicKeyPresence) _pkey;
                byte[] _publicKey = pkey.getKey();
                // extract the name from the uid
                PGPPublicKeyRing ring = PGP.readPublicKeyring(_publicKey);
                if (ring != null) {
                    PGPPublicKey pk = PGP.getMasterKey(ring);
                    if (pk != null) {
                        // set all parameters
                        PGPUserID uid = PGPUserID.parse(PGP.getUserId(pk, getServer().getNetwork()));
                        if (uid != null)
                            name = uid.getName();
                        fingerprint = PGP.getFingerprint(pk);
                        publicKey = _publicKey;
                    }
                }
            }

            ContentResolver cr = ctx.getContentResolver();
            ContentValues values = new ContentValues(7);

            if (name == null)
                name = from;

            // insert public key into the users table
            values.put(Users.HASH, XmppStringUtils.parseLocalpart(from));
            values.put(Users.JID, from);
            values.put(Users.NUMBER, from);
            if (publicKey != null && fingerprint != null) {
                values.put(Users.FINGERPRINT, fingerprint);
                values.put(Users.PUBLIC_KEY, publicKey);
            }
            values.put(Users.DISPLAY_NAME, name);
            values.put(Users.REGISTERED, true);
            cr.insert(Users.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Users.DISCARD_NAME, "true")
                    .build(), values);

            // invalidate cache for this user
            Contact.invalidate(from);

            // insert request into the database
            values.clear();
            values.put(CommonColumns.PEER, from);
            values.put(CommonColumns.TIMESTAMP, System.currentTimeMillis());
            if (cr.insert(Requests.CONTENT_URI, values) != null) {
                // fire up a notification
                MessagingNotification.chatInvitation(ctx, from);
            }
        }
    }

    private boolean isAlreadyTrusted(Presence p) {
        RosterEntry entry = getRosterEntry(p.getFrom());
        return (entry != null && (entry.getType() == RosterPacket.ItemType.to ||
            entry.getType() == RosterPacket.ItemType.both));
    }

    private void handleSubscribed(Presence p) {
        String from = XmppStringUtils.parseBareJid(p.getFrom());

        // save last seen (if any)
        DelayInformation delay = p.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
        if (delay != null) {
            UsersProvider.setLastSeen(getContext(), from, delay.getStamp().getTime());
        }

        if (UsersProvider.getPublicKey(getContext(), from, false) == null) {
            // public key not found
            // assuming the user has allowed us, request it

            PublicKeyPublish pkey = new PublicKeyPublish();
            pkey.setTo(from);

            sendPacket(pkey);
        }

        // send a broadcast
        Intent i = new Intent(ACTION_SUBSCRIBED);
        i.putExtra(EXTRA_TYPE, Presence.Type.subscribed.name());
        i.putExtra(EXTRA_PACKET_ID, p.getStanzaId());

        i.putExtra(EXTRA_FROM, p.getFrom());
        i.putExtra(EXTRA_TO, p.getTo());

        sendBroadcast(i);

        // send any pending messages now
        resendPending(false, true, from);
    }

    private void handlePresence(final Presence p) {
        queueTask(new Runnable() {
            @Override
            public void run() {
                updateUsersDatabase(p);

                // request the new key if fingerprint changed
                String newFingerprint = PublicKeyPresence.getFingerprint(p);
                if (newFingerprint != null) {
                    String jid = XmppStringUtils.parseBareJid(p.getFrom());
                    PGPPublicKeyRing pubRing = UsersProvider.getPublicKey(getContext(),
                        jid, false);
                    if (pubRing != null) {
                        String oldFingerprint = PGP.getFingerprint(PGP.getMasterKey(pubRing));
                        if (!newFingerprint.equalsIgnoreCase(oldFingerprint)) {
                            MessageCenterService.requestPublicKey(getContext(), jid);
                        }
                    }
                }

                Intent i = createIntent(getContext(), p, getRosterEntry(p.getFrom()));
                sendBroadcast(i);
            }
        });
    }

    public static Intent createIntent(Context ctx, Presence p, RosterEntry entry) {
        Intent i = new Intent(ACTION_PRESENCE);
        Presence.Type type = p.getType();
        i.putExtra(EXTRA_TYPE, type != null ? type.name() : Presence.Type.available.name());
        i.putExtra(EXTRA_PACKET_ID, p.getStanzaId());

        i.putExtra(EXTRA_FROM, p.getFrom());
        i.putExtra(EXTRA_TO, p.getTo());
        i.putExtra(EXTRA_STATUS, p.getStatus());
        Presence.Mode mode = p.getMode();
        i.putExtra(EXTRA_SHOW, mode != null ? mode.name() : Presence.Mode.available.name());
        i.putExtra(EXTRA_PRIORITY, p.getPriority());

        String jid = XmppStringUtils.parseBareJid(p.getFrom());

        long timestamp;
        DelayInformation delay = p.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
        if (delay != null) {
            timestamp = delay.getStamp().getTime();
        }
        else {
            // try last seen from database
            timestamp = UsersProvider.getLastSeen(ctx, jid);
            if (timestamp < 0)
                timestamp = System.currentTimeMillis();
        }

        i.putExtra(EXTRA_STAMP, timestamp);

        // public key fingerprint
        String fingerprint = PublicKeyPresence.getFingerprint(p);
        if (fingerprint == null) {
            // try untrusted fingerprint from database
            fingerprint = UsersProvider.getFingerprint(ctx, jid, false);
        }
        i.putExtra(EXTRA_FINGERPRINT, fingerprint);

        // subscription information
        if (entry != null) {
            i.putExtra(EXTRA_ROSTER_NAME, entry.getName());

            RosterPacket.ItemType subscriptionType = entry.getType();
            i.putExtra(EXTRA_SUBSCRIBED_FROM, subscriptionType == RosterPacket.ItemType.both ||
                subscriptionType == RosterPacket.ItemType.from);
            i.putExtra(EXTRA_SUBSCRIBED_TO, subscriptionType == RosterPacket.ItemType.both ||
                subscriptionType == RosterPacket.ItemType.to);
        }

        return i;
    }

    private int updateUsersDatabase(Presence p) {
        String jid = XmppStringUtils.parseBareJid(p.getFrom());

        ContentValues values = new ContentValues(4);
        values.put(Users.REGISTERED, 1);

        // status
        String status = p.getStatus();
        if (status != null)
            values.put(Users.STATUS, status);
        else
            values.putNull(Users.STATUS);

        // delay
        long timestamp = 0;
        DelayInformation delay = p.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
        if (delay != null) {
            // delay from presence (rare)
            timestamp = delay.getStamp().getTime();
        }
        else if (p.isAvailable()) {
            // logged in now
            timestamp = System.currentTimeMillis();
        }

        if (timestamp > 0)
            values.put(Users.LAST_SEEN, timestamp);

        // public key extension (for fingerprint)
        PublicKeyPresence pkey = p.getExtension(PublicKeyPresence.ELEMENT_NAME, PublicKeyPresence.NAMESPACE);
        if (pkey != null) {
            String fingerprint = pkey.getFingerprint();
            if (fingerprint != null)
                values.put(Users.FINGERPRINT, fingerprint);
        }

        return getContext().getContentResolver().update(Users.CONTENT_URI,
            values, Users.JID + "=?", new String[] { jid });
    }

}

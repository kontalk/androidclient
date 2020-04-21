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

package org.kontalk.service.msgcenter;

import java.util.Date;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.SubscribeListener;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jxmpp.jid.Jid;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.client.PublicKeyPresence;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PGPUserID;
import org.kontalk.data.Contact;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.msgcenter.event.PresenceEvent;
import org.kontalk.service.msgcenter.event.PublicKeyRequest;
import org.kontalk.service.msgcenter.event.UserOfflineEvent;
import org.kontalk.service.msgcenter.event.UserOnlineEvent;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.Preferences;


/**
 * Packet listener for presence stanzas.
 * @author Daniele Ricci
 */
class PresenceListener extends MessageCenterPacketListener implements SubscribeListener {

    public PresenceListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        try {
            Presence p = (Presence) packet;

            if (p.getType() == Presence.Type.available || p.getType() == Presence.Type.unavailable) {
                if (p.getType() == Presence.Type.unavailable) {
                    // clear contact volatile state and cached data
                    Contact.invalidateData(p.getFrom().toString());
                }

                handlePresence(p);
            }
        }
        catch (Exception e) {
            Log.e(MessageCenterService.TAG, "error parsing presence", e);
        }
    }

    @Override
    public SubscribeAnswer processSubscribe(Jid from, Presence subscribeRequest) {
        Context ctx = getContext();

        // auto-accept subscription
        if (canAutoAcceptSubscription(ctx, subscribeRequest)) {
            // TODO user database entry should be stored here too

            ExtensionElement _pkey = subscribeRequest.getExtension(PublicKeyPresence.ELEMENT_NAME, PublicKeyPresence.NAMESPACE);

            try {
                if (_pkey instanceof PublicKeyPresence) {
                    PublicKeyPresence pkey = (PublicKeyPresence) _pkey;

                    byte[] keydata = pkey.getKey();
                    // just to ensure it's valid data
                    PGP.readPublicKeyring(keydata);

                    String jid = from.asBareJid().toString();
                    // store key to users table
                    Keyring.setKey(getContext(), jid, keydata, Keyring.TRUST_VERIFIED);
                }
            }
            catch (Exception e) {
                Log.w(TAG, "invalid public key from " + from, e);
                // TODO should we notify the user about this?
            }

            return SubscribeAnswer.Approve;
        }

        // ask the user
        else {
            /*
             * Subscription procedure:
             * 1. update (or insert) users table with the public key just received
             * 2. update (or insert) threads table with a special subscription record
             * 3. user will either accept or refuse
             */

            String fromStr = from.asBareJid().toString();

            // extract public key
            String name = null;
            byte[] publicKey = null;
            ExtensionElement _pkey = subscribeRequest.getExtension(PublicKeyPresence.ELEMENT_NAME, PublicKeyPresence.NAMESPACE);
            if (_pkey instanceof PublicKeyPresence) {
                PublicKeyPresence pkey = (PublicKeyPresence) _pkey;
                byte[] _publicKey = pkey.getKey();
                // extract the name from the uid
                PGPPublicKeyRing ring = null;
                try {
                    ring = PGP.readPublicKeyring(_publicKey);
                }
                catch (Exception e) {
                    Log.w(TAG, "invalid public key from " + fromStr, e);
                }
                if (ring != null) {
                    PGPPublicKey pk = PGP.getMasterKey(ring);
                    if (pk != null) {
                        // set all parameters
                        PGPUserID uid = PGPUserID.parse(PGP.getUserId(pk, getServer().getNetwork()));
                        if (uid != null)
                            name = uid.getName();
                        publicKey = _publicKey;
                    }
                }
            }

            ContentResolver cr = ctx.getContentResolver();
            ContentValues values = new ContentValues(4);

            if (name == null)
                name = fromStr;

            // insert public key into the users table
            values.put(Users.JID, fromStr);
            values.put(Users.NUMBER, fromStr);
            values.put(Users.DISPLAY_NAME, name);
            values.put(Users.REGISTERED, true);
            cr.insert(Users.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Users.DISCARD_NAME, "true")
                    .build(), values);

            // insert key if any
            if (publicKey != null) {
                try {
                    Keyring.setKey(ctx, fromStr, publicKey, Keyring.TRUST_UNKNOWN);
                }
                catch (Exception e) {
                    Log.w(TAG, "invalid public key from " + fromStr, e);
                }
            }

            // invalidate cache for this user
            Contact.invalidate(fromStr);

            // insert request into the database
            if (MessagesProviderClient.newChatRequest(ctx, fromStr) != null) {
                // fire up a notification
                MessagingNotification.chatInvitation(ctx, fromStr);
            }

            return null;
        }
    }

    private boolean canAutoAcceptSubscription(Context context, Presence subscribeRequest) {
        return Preferences.getAutoAcceptSubscriptions(context) ||
            Kontalk.get().getDefaultAccount().isNetworkJID(subscribeRequest.getFrom().asBareJid()) ||
            isAlreadyTrusted(subscribeRequest);
    }

    private boolean isAlreadyTrusted(Presence p) {
        RosterEntry entry = getRosterEntry(p.getFrom());
        return (entry != null && (entry.getType() == RosterPacket.ItemType.to ||
            entry.getType() == RosterPacket.ItemType.both));
    }

    private void handlePresence(final Presence p) {
        queueTask(new Runnable() {
            @Override
            public void run() {
                updateUsersDatabase(p);

                // request the new key if fingerprint changed
                String newFingerprint = PublicKeyPresence.getFingerprint(p);
                if (newFingerprint != null) {
                    boolean requestKey = false;
                    String jid = p.getFrom().asBareJid().toString();
                    PGPPublicKeyRing pubRing = Keyring.getPublicKey(getContext(),
                        jid, Keyring.TRUST_UNKNOWN);
                    if (pubRing != null) {
                        String oldFingerprint = PGP.getFingerprint(PGP.getMasterKey(pubRing));
                        if (!newFingerprint.equalsIgnoreCase(oldFingerprint)) {
                            // key has changed, request new one
                            requestKey = true;
                        }
                    }
                    else {
                        // no key available, request one
                        requestKey = true;
                    }

                    if (requestKey) {
                        MessageCenterService.bus()
                            .post(new PublicKeyRequest(p.getFrom().asBareJid()));
                    }
                }

                MessageCenterService.bus()
                    .post(createEvent(getContext(), p, getRosterEntry(p.getFrom()), null));
            }
        });
    }

    public static PresenceEvent createEvent(Context ctx, Presence p, RosterEntry entry, String id) {
        String jid = p.getFrom().asBareJid().toString();

        Date delayTime;
        DelayInformation delay = p.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
        if (delay != null) {
            delayTime = delay.getStamp();
        }
        else {
            // try last seen from database
            long timestamp = UsersProvider.getLastSeen(ctx, jid);
            if (timestamp < 0)
                timestamp = System.currentTimeMillis();
            delayTime = new Date(timestamp);
        }

        // public key fingerprint
        String fingerprint = PublicKeyPresence.getFingerprint(p);
        if (fingerprint == null) {
            // try untrusted fingerprint from database
            fingerprint = Keyring.getFingerprint(ctx, jid, Keyring.TRUST_UNKNOWN);
        }

        // subscription information
        String rosterName = null;
        boolean subscribedFrom = false;
        boolean subscribedTo = false;
        if (entry != null) {
            rosterName = entry.getName();

            RosterPacket.ItemType subscriptionType = entry.getType();
            subscribedFrom = subscriptionType == RosterPacket.ItemType.both ||
                subscriptionType == RosterPacket.ItemType.from;
            subscribedTo = subscriptionType == RosterPacket.ItemType.both ||
                subscriptionType == RosterPacket.ItemType.to;
        }

        if (p.getType() == Presence.Type.available) {
            return new UserOnlineEvent(p.getFrom(), p.getMode(), p.getPriority(),
                p.getStatus(), delayTime, rosterName, subscribedFrom, subscribedTo, fingerprint, id);
        }
        else if (p.getType() == Presence.Type.unavailable) {
            return new UserOfflineEvent(p.getFrom(), p.getMode(), p.getPriority(),
                p.getStatus(), delayTime, rosterName, subscribedFrom, subscribedTo, fingerprint, id);
        }
        else {
            // TODO other presence types
            throw new UnsupportedOperationException("Not implemented: " + p.getType());
        }
    }

    @SuppressWarnings("WeakerAccess")
    int updateUsersDatabase(Presence p) {
        String jid = p.getFrom().asBareJid().toString();

        ContentValues values = new ContentValues(4);
        values.put(Users.REGISTERED, 1);

        // status
        String status = p.getStatus();
        if (status != null)
            values.put(Users.STATUS, status);
        else
            values.putNull(Users.STATUS);

        // delay
        long timestamp;
        DelayInformation delay = p.getExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE);
        if (delay != null) {
            // delay from presence (rare)
            timestamp = delay.getStamp().getTime();
        }
        else {
            // logged in/out now
            timestamp = System.currentTimeMillis();
        }

        if (timestamp > 0)
            values.put(Users.LAST_SEEN, timestamp);

        // public key extension (for fingerprint)
        PublicKeyPresence pkey = p.getExtension(PublicKeyPresence.ELEMENT_NAME, PublicKeyPresence.NAMESPACE);
        if (pkey != null) {
            String fingerprint = pkey.getFingerprint();
            if (fingerprint != null) {
                // insert new key with empty key data
                Keyring.setKey(getContext(), jid, fingerprint, new Date());
            }
        }

        return getContext().getContentResolver().update(Users.CONTENT_URI,
            values, Users.JID + "=?", new String[] { jid });
    }

}

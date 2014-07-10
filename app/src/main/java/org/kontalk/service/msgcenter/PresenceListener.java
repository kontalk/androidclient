/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_PRESENCE;
import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_SUBSCRIBED;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM_USERID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_GROUP_COUNT;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_GROUP_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PRIORITY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_SHOW;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_STAMP;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_STATUS;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TYPE;

import java.io.IOException;
import java.util.Iterator;

import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.kontalk.client.StanzaGroupExtension;
import org.kontalk.client.SubscribePublicKey;
import org.kontalk.client.VCard4;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Threads.Requests;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.provider.UsersProvider;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


/**
 * Packet listener for presence stanzas.
 * @author Daniele Ricci
 */
class PresenceListener extends MessageCenterPacketListener {

    public PresenceListener(MessageCenterService instance) {
        super(instance);
    }

    private Packet createSubscribe(Presence p) {
        PacketExtension _pkey = p.getExtension(SubscribePublicKey.ELEMENT_NAME, SubscribePublicKey.NAMESPACE);

        try {

            if (_pkey instanceof SubscribePublicKey) {
                SubscribePublicKey pkey = (SubscribePublicKey) _pkey;

                PGPPublicKeyRing pubRing = PGP.readPublicKeyring(pkey.getKey());
                PGPPublicKey publicKey = PGP.getMasterKey(pubRing);
                String fingerprint = MessageUtils.bytesToHex(publicKey.getFingerprint());

                // store key to users table
                String userId = StringUtils.parseName(p.getFrom());
                UsersProvider.setUserKey(getContext(), userId,
                    pkey.getKey(), fingerprint);
            }

            Presence p2 = new Presence(Presence.Type.subscribed);
            p2.setTo(p.getFrom());
            return p2;

        }
        catch (Exception e) {
            Log.w(MessageCenterService.TAG, "unable add user to whitelist", e);
            // TODO should we notify the user about this?
            // TODO throw new PGPException(...)
            return null;
        }
    }

    @Override
    public void processPacket(Packet packet) {
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
        if (Preferences.getAutoAcceptSubscriptions(ctx)) {

            Packet r = createSubscribe(p);
            if (r != null)
                getConnection().sendPacket(r);

        }

        // ask the user
        else {

            /*
             * Subscription procedure:
             * 1. update (or insert) users table with the public key just received
             * 2. update (or insert) threads table with a special subscription record
             * 3. user will either accept or refuse
             */

            String from = StringUtils.parseName(p.getFrom());

            // extract public key
            String name = null, fingerprint = null;
            byte[] publicKey = null;
            PacketExtension _pkey = p.getExtension(SubscribePublicKey.ELEMENT_NAME, SubscribePublicKey.NAMESPACE);
            if (_pkey instanceof SubscribePublicKey) {
                SubscribePublicKey pkey = (SubscribePublicKey) _pkey;
                byte[] _publicKey = pkey.getKey();
                // extract the name from the uid
                PGPPublicKeyRing ring = PGP.readPublicKeyring(_publicKey);
                if (ring != null) {
                    PGPPublicKey pk = PGP.getMasterKey(ring);
                    if (pk != null) {
                        // set all parameters
                        name = PGP.getUserId(pk, getServer().getNetwork());
                        fingerprint = PGP.getFingerprint(pk);
                        publicKey = _publicKey;
                    }
                }
            }

            ContentResolver cr = ctx.getContentResolver();
            ContentValues values = new ContentValues(4);

            // insert public key into the users table
            values.put(Users.HASH, from);
            values.put(Users.PUBLIC_KEY, publicKey);
            values.put(Users.FINGERPRINT, fingerprint);
            values.put(Users.DISPLAY_NAME, name);
            cr.insert(Users.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Users.DISCARD_NAME, "true")
                    .build(), values);

            // invalidate cache for this user
            Contact.invalidate(from);

            // insert request into the database
            values.clear();
            values.put(CommonColumns.PEER, from);
            values.put(CommonColumns.TIMESTAMP, System.currentTimeMillis());
            cr.insert(Requests.CONTENT_URI, values);

            // fire up a notification
            MessagingNotification.chatInvitation(ctx, from);
        }
    }

    private void handleSubscribed(Presence p) {
        String from = StringUtils.parseName(p.getFrom());

        if (UsersProvider.getPublicKey(getContext(), from) == null) {
            // public key not found
            // assuming the user has allowed us, request it

            VCard4 vcard = new VCard4();
            vcard.setType(IQ.Type.GET);
            vcard.setTo(StringUtils.parseBareAddress(p.getFrom()));

            sendPacket(vcard);
        }

        // send a broadcast
        Intent i = new Intent(ACTION_SUBSCRIBED);
        i.putExtra(EXTRA_TYPE, Presence.Type.subscribed.name());
        i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

        from = p.getFrom();
        String network = StringUtils.parseServer(from);
        // our network - convert to userId
        if (network.equalsIgnoreCase(getServer().getNetwork())) {
            StringBuilder b = new StringBuilder();
            b.append(StringUtils.parseName(from));
            b.append(StringUtils.parseResource(from));
            i.putExtra(EXTRA_FROM_USERID, b.toString());
        }

        i.putExtra(EXTRA_FROM, from);
        i.putExtra(EXTRA_TO, p.getTo());

        sendBroadcast(i);
    }

    private void handlePresence(Presence p) {
        Intent i = new Intent(ACTION_PRESENCE);
        Presence.Type type = p.getType();
        i.putExtra(EXTRA_TYPE, type != null ? type.name() : Presence.Type.available.name());
        i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

        String from = p.getFrom();
        String network = StringUtils.parseServer(from);
        // our network - convert to userId
        if (network.equalsIgnoreCase(getServer().getNetwork())) {
            StringBuilder b = new StringBuilder();
            b.append(StringUtils.parseName(from));
            b.append(StringUtils.parseResource(from));
            i.putExtra(EXTRA_FROM_USERID, b.toString());
        }

        i.putExtra(EXTRA_FROM, from);
        i.putExtra(EXTRA_TO, p.getTo());
        i.putExtra(EXTRA_STATUS, p.getStatus());
        Presence.Mode mode = p.getMode();
        i.putExtra(EXTRA_SHOW, mode != null ? mode.name() : Presence.Mode.available.name());
        i.putExtra(EXTRA_PRIORITY, p.getPriority());

        // getExtension doesn't work here
        Iterator<PacketExtension> iter = p.getExtensions().iterator();
        while (iter.hasNext()) {
            PacketExtension _ext = iter.next();
            if (_ext instanceof DelayInformation) {
                DelayInformation delay = (DelayInformation) _ext;
                i.putExtra(EXTRA_STAMP, delay.getStamp().getTime());
                break;
            }
        }

        // non-standard stanza group extension
        PacketExtension ext = p.getExtension(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE);
        if (ext != null && ext instanceof StanzaGroupExtension) {
            StanzaGroupExtension g = (StanzaGroupExtension) ext;
            i.putExtra(EXTRA_GROUP_ID, g.getId());
            i.putExtra(EXTRA_GROUP_COUNT, g.getCount());
        }

        Log.v(MessageCenterService.TAG, "broadcasting presence: " + i);
        sendBroadcast(i);
    }

}


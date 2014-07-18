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

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_VCARD;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM_USERID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PUBLIC_KEY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.VCard4;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.data.Contact;
import org.kontalk.provider.UsersProvider;
import org.kontalk.util.MessageUtils;

import android.content.Intent;
import android.util.Log;


/**
 * Packet Listener for vCard4 iq stanzas.
 * @author Daniele Ricci
 */
class VCardListener extends MessageCenterPacketListener {

    public VCardListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        VCard4 p = (VCard4) packet;

        // will be true if it's our card
        boolean myCard = false;
        byte[] _publicKey = p.getPGPKey();

        // vcard was requested, store but do not broadcast
        if (p.getType() == IQ.Type.RESULT) {

            if (_publicKey != null) {

                // FIXME always false LOL
                if (myCard) {
                    byte[] bridgeCertData;
                    try {
                        PersonalKey key = getApplication().getPersonalKey();

                        // TODO subjectAltName?
                        bridgeCertData = X509Bridge.createCertificate(_publicKey,
                            key.getSignKeyPair().getPrivateKey(), null).getEncoded();
                    }
                    catch (Exception e) {
                        Log.e(MessageCenterService.TAG, "error decoding key data", e);
                        bridgeCertData = null;
                    }

                    if (bridgeCertData != null) {
                        // store key data in AccountManager
                        Authenticator.setDefaultPersonalKey(getContext(),
                            _publicKey, null, bridgeCertData, null);
                        // invalidate cached personal key
                        getApplication().invalidatePersonalKey();

                        Log.v(MessageCenterService.TAG, "personal key updated.");
                    }
                }

                try {
                    String userId = StringUtils.parseName(p.getFrom());
                    String fingerprint = PGP.getFingerprint(_publicKey);
                    UsersProvider.setUserKey(getContext(), userId,
                        _publicKey, fingerprint);

                    // invalidate cache for this user
                    Contact.invalidate(userId);
                }
                catch (Exception e) {
                    // TODO warn user
                    Log.e(MessageCenterService.TAG, "unable to update user key", e);
                }
            }

        }

        // vcard coming from sync, send a broadcast but do not store
        else if (p.getType() == IQ.Type.SET) {

            Intent i = new Intent(ACTION_VCARD);
            i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

            String from = p.getFrom();
            String network = StringUtils.parseServer(from);
            // our network - convert to userId
            if (network.equalsIgnoreCase(getServer().getNetwork())) {
                StringBuilder b = new StringBuilder();

                // is this our vCard?
                String userId = StringUtils.parseName(from);
                String hash = MessageUtils.sha1(getMyUsername());
                if (userId.equalsIgnoreCase(hash))
                    myCard = true;

                b.append(userId);
                b.append(StringUtils.parseResource(from));
                i.putExtra(EXTRA_FROM_USERID, b.toString());
            }

            i.putExtra(EXTRA_FROM, from);
            i.putExtra(EXTRA_TO, p.getTo());
            i.putExtra(EXTRA_PUBLIC_KEY, _publicKey);

            Log.v(MessageCenterService.TAG, "broadcasting vcard: " + i);
            sendBroadcast(i);

        }
    }
}


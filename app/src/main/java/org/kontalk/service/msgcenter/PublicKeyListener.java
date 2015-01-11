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

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jxmpp.util.XmppStringUtils;

import android.content.Intent;
import android.util.Log;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.PublicKeyPublish;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.data.Contact;
import org.kontalk.provider.UsersProvider;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.XMPPUtils;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_PUBLICKEY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PUBLIC_KEY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;


/**
 * Packet Listener for public key publish iq stanzas.
 * @author Daniele Ricci
 */
class PublicKeyListener extends MessageCenterPacketListener {

    public PublicKeyListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        PublicKeyPublish p = (PublicKeyPublish) packet;

        // will be true if it's our card
        boolean myCard = false;
        byte[] _publicKey = p.getPublicKey();

        // vcard was requested, store but do not broadcast
        if (p.getType() == IQ.Type.result) {

            if (_publicKey != null) {

                String from = XmppStringUtils.parseBareJid(p.getFrom());

                boolean networkUser = XMPPUtils.isLocalJID(from, getServer().getNetwork());
                // our network - convert to userId
                if (networkUser) {
                    // is this our vCard?
                    String userId = XmppStringUtils.parseLocalpart(from);
                    String hash = MessageUtils.sha1(getMyUsername());
                    if (userId.equalsIgnoreCase(hash))
                        myCard = true;
                }

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

                if (SyncAdapter.isActive(getContext())) {
                    // sync currently active, broadcast the key
                    Intent i = new Intent(ACTION_PUBLICKEY);
                    i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

                    i.putExtra(EXTRA_FROM, p.getFrom());
                    i.putExtra(EXTRA_TO, p.getTo());
                    i.putExtra(EXTRA_PUBLIC_KEY, _publicKey);

                    Log.v(MessageCenterService.TAG, "broadcasting public key: " + i);
                    sendBroadcast(i);
                }

                else {
                    try {
                        Log.v("pubkey", "networkUser = " + networkUser + " (" + from + ")");
                        if (networkUser) {
                            String fingerprint = PGP.getFingerprint(_publicKey);
                            Log.v("pubkey", "Updating key for " + from + " fingerprint " + fingerprint);
                            UsersProvider.setUserKey(getContext(), from,
                                _publicKey, fingerprint);

                            // invalidate cache for this user
                            Contact.invalidate(from);
                        }
                    }
                    catch (Exception e) {
                        // TODO warn user
                        Log.e(MessageCenterService.TAG, "unable to update user key", e);
                    }
                }
            }

        }
    }
}


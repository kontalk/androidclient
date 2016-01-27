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

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jxmpp.util.XmppStringUtils;

import android.content.Intent;
import android.util.Log;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.PublicKeyPublish;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.data.Contact;
import org.kontalk.provider.UsersProvider;
import org.kontalk.sync.SyncAdapter;

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
    public void processPacket(Stanza packet) {
        PublicKeyPublish p = (PublicKeyPublish) packet;

        byte[] _publicKey = p.getPublicKey();

        // vcard was requested, store but do not broadcast
        if (p.getType() == IQ.Type.result) {

            if (_publicKey != null) {
                String from = XmppStringUtils.parseBareJid(p.getFrom());

                // is this our key?
                if (Authenticator.isSelfJID(getContext(), from)) {
                    byte[] bridgeCertData;
                    try {
                        PersonalKey key = getApplication().getPersonalKey();

                        bridgeCertData = X509Bridge.createCertificate(_publicKey,
                            key.getAuthKeyPair().getPrivateKey()).getEncoded();
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

                String id = p.getStanzaId();
                // we are syncing and this is a response for the Syncer
                if (SyncAdapter.getIQPacketId().equals(id) && SyncAdapter.isActive(getContext())) {
                    // sync currently active, broadcast the key
                    Intent i = new Intent(ACTION_PUBLICKEY);
                    i.putExtra(EXTRA_PACKET_ID, p.getStanzaId());

                    i.putExtra(EXTRA_FROM, p.getFrom());
                    i.putExtra(EXTRA_TO, p.getTo());
                    i.putExtra(EXTRA_PUBLIC_KEY, _publicKey);

                    sendBroadcast(i);
                }

                else {
                    try {
                        Log.v("pubkey", "Updating key for " + from);
                        UsersProvider.setUserKey(getContext(), from, _publicKey);
                        // maybe trust the key
                        UsersProvider.maybeTrustUserKey(getContext(), from, _publicKey);

                        // invalidate cache for this user
                        Contact.invalidate(from);
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


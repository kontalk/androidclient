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

import org.jivesoftware.smack.ExceptionCallback;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jxmpp.jid.BareJid;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.PublicKeyPublish;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PGPUserID;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.data.Contact;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.msgcenter.event.PublicKeyEvent;
import org.kontalk.sync.SyncAdapter;


/**
 * Packet Listener for public key publish iq stanzas.
 * @author Daniele Ricci
 */
class PublicKeyListener extends MessageCenterPacketListener implements ExceptionCallback {

    private final IQ mRequest;

    public PublicKeyListener(MessageCenterService instance, IQ request) {
        super(instance);
        mRequest = request;
    }

    @Override
    public void processStanza(Stanza packet) {
        PublicKeyPublish p = (PublicKeyPublish) packet;

        byte[] _publicKey = p.getPublicKey();

        if (_publicKey != null) {
            BareJid from = p.getFrom().asBareJid();
            boolean selfJid = Authenticator.isSelfJID(getContext(), from);

            // is this our key?
            if (selfJid) {
                byte[] bridgeCertData;
                try {
                    PersonalKey key = Kontalk.get().getPersonalKey();

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
                    Kontalk.get().invalidatePersonalKey();

                    Log.v(MessageCenterService.TAG, "personal key updated.");
                }
            }

            String id = p.getStanzaId();

            // broadcast key update
            MessageCenterService.bus()
                .post(new PublicKeyEvent(packet.getFrom(), _publicKey, packet.getStanzaId()));

            // if we are not syncing and this is not a response for the Syncer
            // save the key immediately
            if (!SyncAdapter.getIQKeysPacketId().equals(id) || !SyncAdapter.isActive(getContext())) {

                // updating server key
                if (from.isDomainBareJid()) {
                    Log.v("pubkey", "Updating server key for " + from);
                    try {
                        Keyring.setKey(getContext(), from.toString(), _publicKey,
                            Keyring.TRUST_VERIFIED);
                    }
                    catch (Exception e) {
                        // TODO warn user
                        Log.e(MessageCenterService.TAG, "unable to update user key", e);
                    }
                }

                else {
                    try {
                        Log.v("pubkey", "Updating key for " + from);
                        Keyring.setKey(getContext(), from.toString(), _publicKey,
                            selfJid ? Keyring.TRUST_VERIFIED : -1);

                        // update display name with uid (if empty)
                        PGPUserID keyUid = PGP.parseUserId(_publicKey, getConnection().getXMPPServiceDomain().toString());
                        if (keyUid != null && keyUid.getName() != null)
                            UsersProvider.updateDisplayNameIfEmpty(getContext(), from.toString(), keyUid.getName());

                        // invalidate cache for this user
                        Contact.invalidate(from.toString());
                    }
                    catch (Exception e) {
                        // TODO warn user
                        Log.e(MessageCenterService.TAG, "unable to update user key", e);
                    }
                }
            }
        }
    }

    @Override
    public void processException(Exception exception) {
        Log.w(TAG, "error processing public key", exception);
        MessageCenterService.bus()
            .post(new PublicKeyEvent(exception, mRequest.getTo(), mRequest.getStanzaId()));
    }
}

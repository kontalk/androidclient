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

package org.kontalk.crypto;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.omemo.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.element.OmemoElement;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.ClearTextMessage;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.util.OmemoConstants;
import org.jivesoftware.smackx.pubsub.packet.PubSub;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;

import org.kontalk.client.KontalkConnection;
import org.kontalk.provider.Keyring;


/**
 * OMEMO coder implementation.
 * @author Daniele Ricci
 */
public class OmemoCoder extends Coder {

    private final TrustedRecipient[] mRecipients;
    private final BareJid mSender;

    private OmemoManager mManager;

    /** For encryption. */
    public OmemoCoder(XMPPConnection connection, TrustedRecipient[] recipients) throws XMPPException.XMPPErrorException,
            SmackException.NotConnectedException, InterruptedException, SmackException.NoResponseException {
        init(connection);

        mSender = null;
        mRecipients = recipients;

        if (recipients != null) {
            for (TrustedRecipient rcpt : recipients) {
                BareJid user = rcpt.jid.asBareJid();
                Map<OmemoDevice, OmemoFingerprint> fingerprints = mManager
                    .getActiveFingerprints(user);

                if (fingerprints.isEmpty()) {
                    if (!mManager.contactSupportsOmemo(user)) {
                        throw new UnsupportedOperationException("Recipient " + user + " does not support OMEMO");
                    }

                    // fingerprints should be available after contactSupportsOmemo()
                    fingerprints = mManager.getActiveFingerprints(user);
                }

                // Trust the OMEMO fingerprints by looking at user trust information.
                // Unknown trust level means a new key came in recently and was not ignored nor verified.
                // When that meets manual trust, it means user exited from Blind Trust Before Verification.
                // In that case, identities will not be trusted and encryption will fail.
                boolean willTrust = !(rcpt.trustLevel == Keyring.TRUST_UNKNOWN && rcpt.manualTrust);
                for (Map.Entry<OmemoDevice, OmemoFingerprint> device : fingerprints.entrySet()) {
                    if (willTrust) {
                        mManager.trustOmemoIdentity(device.getKey(), device.getValue());
                    }
                    else {
                        mManager.distrustOmemoIdentity(device.getKey(), device.getValue());
                    }
                }
            }
        }
    }

    /** For decryption. */
    public OmemoCoder(XMPPConnection connection, Jid sender) throws XMPPException.XMPPErrorException,
            SmackException.NotConnectedException, InterruptedException, SmackException.NoResponseException {
        init(connection);

        mSender = sender.asBareJid();
        mRecipients = null;
    }

    private void init(XMPPConnection connection) throws XMPPException.XMPPErrorException,
            SmackException.NotConnectedException, InterruptedException, SmackException.NoResponseException {
        // FIXME should be: if (!OmemoManager.serverSupportsOmemo(connection, connection.getXMPPServiceDomain())) {
        if (!((KontalkConnection) connection).supportsFeature(PubSub.NAMESPACE)) {
            throw new UnsupportedOperationException("Server does not support OMEMO");
        }

        mManager = OmemoManager.getInstanceFor(connection);
    }

    @Override
    public int getSupportedFlags() {
        return Coder.SECURITY_ADVANCED;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Message encryptMessage(Message message, String placeholder) throws GeneralSecurityException {
        Message output;
        ArrayList<BareJid> recipients = new ArrayList(mRecipients.length);
        for (TrustedRecipient rcpt : mRecipients) {
            recipients.add(rcpt.jid.asBareJid());
        }

        try {
            output = mManager.encrypt(recipients, message.getBody());
        }
        catch (UndecidedOmemoIdentityException e) {
            // TODO experimenting; crash for now
            throw new RuntimeException("Impossible: we should have decided already!", e);
        }
        catch (CannotEstablishOmemoSessionException e) {
            try {
                output = mManager.encryptForExistingSessions(e, message.getBody());
            }
            catch (UndecidedOmemoIdentityException e1) {
                // TODO experimenting; crash for now
                throw new RuntimeException("Impossible: we should have decided already!", e1);
            }
            catch (CryptoFailedException e1) {
                throw new GeneralSecurityException(e1);
            }
        }
        catch (Exception e) {
            throw new GeneralSecurityException(e);
        }

        if (output != null) {
            output.setBody(placeholder);
            output.setStanzaId(message.getStanzaId());
            output.setFrom(message.getFrom());
            output.setTo(message.getTo());
            output.setType(message.getType());
            output.addExtensions(message.getExtensions());
        }

        return output;
    }

    /**
     * For now just here to fool {@link org.jivesoftware.smack.MessageListener}.
     */
    @Override
    public DecryptOutput decryptMessage(Message message, boolean verify) throws GeneralSecurityException {
        if (message.hasExtension(DelayInformation.ELEMENT, DelayInformation.NAMESPACE)) {
            // offline message - decrypt manually
            ClearTextMessage cleartext;
            try {
                cleartext = mManager.decrypt(mSender, message);
            }
            catch (Exception e) {
                throw new GeneralSecurityException("OMEMO decryption failed", e);
            }

            if (cleartext.getBody() == null) {
                throw new DecryptException(DecryptException.DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND);
            }

            // simple text message
            Message output = new Message();
            output.setStanzaId(message.getStanzaId());
            output.setType(message.getType());
            output.setFrom(message.getFrom());
            output.setTo(message.getTo());
            output.setBody(cleartext.getBody());
            // copy extensions and remove our own
            output.addExtensions(message.getExtensions());
            output.removeExtension(OmemoElement.ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL);
            return new DecryptOutput(output, "text/plain", new Date(), SECURITY_ADVANCED, Collections.emptyList());
        }
        else {
            // online message - already decrypted by smack-omemo
            return new DecryptOutput(message, "text/plain", new Date(), SECURITY_ADVANCED, Collections.emptyList());
        }
    }

    @Override
    public void encryptFile(InputStream input, OutputStream output) throws GeneralSecurityException {
        throw new UnsupportedOperationException("OMEMO does not support file encryption");
    }

    @Override
    public void decryptFile(InputStream input, boolean verify, OutputStream output, List<DecryptException> errors) throws GeneralSecurityException {
        throw new UnsupportedOperationException("OMEMO does not support file encryption");
    }

    @Override
    public VerifyOutput verifyText(byte[] signed, boolean verify) throws GeneralSecurityException {
        throw new UnsupportedOperationException("OMEMO does not support verification and signing");
    }

    /**
     * Recipient information for encryption.
     * The trust level is considered blocking if TRUST_UKNOWN and manualTrust is true,
     * meaning we manually verified a previous key and thus overridden Blind Trust
     * Before Verification.
     */
    public static class TrustedRecipient {
        public final Jid jid;
        public final int trustLevel;
        public final boolean manualTrust;

        public TrustedRecipient(Jid jid, int trustLevel, boolean manualTrust) {
            this.jid = jid;
            this.trustLevel = trustLevel;
            this.manualTrust = manualTrust;
        }
    }
}

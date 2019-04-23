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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.omemo.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.element.OmemoElement;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.ClearTextMessage;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.util.OmemoConstants;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;


/**
 * OMEMO coder implementation.
 * @author Daniele Ricci
 */
public class OmemoCoder extends Coder {

    private final OmemoManager mManager;
    private final Jid[] mRecipients;

    public OmemoCoder(XMPPConnection connection, Jid[] recipients) throws XMPPException.XMPPErrorException,
            SmackException.NotConnectedException, InterruptedException, SmackException.NoResponseException {
        mManager = OmemoManager.getInstanceFor(connection);
        if (!OmemoManager.serverSupportsOmemo(connection, connection.getXMPPServiceDomain())) {
            throw new UnsupportedOperationException("Server does not support OMEMO");
        }

        mRecipients = recipients;
        if (recipients != null) {
            for (Jid jid : recipients) {
                Map<OmemoDevice, OmemoFingerprint> fingerprints = mManager
                    .getActiveFingerprints(JidCreate.bareFromOrThrowUnchecked(jid));
                if (fingerprints.size() == 0) {
                    throw new UnsupportedOperationException("Recipient " + jid + " does not support OMEMO");
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Message encryptMessage(Message message, String placeholder) throws GeneralSecurityException {
        // FIXME test code
        Message output = null;
        ArrayList recipients = new ArrayList(Arrays.asList(mRecipients));
        try {
            output = mManager.encrypt(recipients, message.getBody());
        }
        catch (UndecidedOmemoIdentityException e) {
            // TODO rethrow?
            e.printStackTrace();
        }
        catch (CannotEstablishOmemoSessionException e) {
            // TODO rethrow?
            try {
                output = mManager.encryptForExistingSessions(e, message.getBody());
            }
            catch (UndecidedOmemoIdentityException e1) {
                // TODO rethrow?
                e1.printStackTrace();
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
        }

        return output;
    }

    @Override
    public DecryptOutput decryptMessage(Message message, boolean verify) throws GeneralSecurityException {
        ClearTextMessage cleartext;
        try {
            cleartext = mManager.decrypt(null, message);
        }
        catch (Exception e) {
            throw new GeneralSecurityException("OMEMO decryption failed", e);
        }

        if (cleartext.getBody() == null) {
            throw new DecryptException(DecryptException.DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND);
        }

        // simple text message
        Message output = new Message();
        output.setType(message.getType());
        output.setFrom(message.getFrom());
        output.setTo(message.getTo());
        output.setBody(cleartext.getBody());
        // copy extensions and remove our own
        output.addExtensions(message.getExtensions());
        output.removeExtension(OmemoElement.ENCRYPTED, OmemoConstants.OMEMO_NAMESPACE_V_AXOLOTL);
        return new DecryptOutput(output, "text/plain", new Date(), SECURITY_ADVANCED, Collections.emptyList());
    }

    @Override
    public void encryptFile(InputStream input, OutputStream output) throws GeneralSecurityException {
        // TODO
    }

    @Override
    public void decryptFile(InputStream input, boolean verify, OutputStream output, List<DecryptException> errors) throws GeneralSecurityException {
        // TODO

    }

    @Override
    public VerifyOutput verifyText(byte[] signed, boolean verify) throws GeneralSecurityException {
        // TODO
        return null;
    }
}

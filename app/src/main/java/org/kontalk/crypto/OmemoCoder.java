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
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.element.OmemoElement;
import org.jivesoftware.smackx.omemo.internal.ClearTextMessage;
import org.jivesoftware.smackx.omemo.util.OmemoConstants;


/**
 * OMEMO coder implementation.
 * @author Daniele Ricci
 */
public class OmemoCoder extends Coder {

    private final OmemoManager mManager;

    public OmemoCoder(XMPPConnection connection) {
        mManager = OmemoManager.getInstanceFor(connection);
    }

    @Override
    public byte[] encryptText(CharSequence text) throws GeneralSecurityException {
        return new byte[0];
    }

    @Override
    public byte[] encryptStanza(CharSequence xml) throws GeneralSecurityException {
        return new byte[0];
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

    }

    @Override
    public void decryptFile(InputStream input, boolean verify, OutputStream output, List<DecryptException> errors) throws GeneralSecurityException {

    }

    @Override
    public VerifyOutput verifyText(byte[] signed, boolean verify) throws GeneralSecurityException {
        return null;
    }
}

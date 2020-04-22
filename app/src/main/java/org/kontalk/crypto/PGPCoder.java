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

package org.kontalk.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignature;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;

import org.kontalk.client.E2EEncryption;
import org.kontalk.client.EndpointServer;
import org.kontalk.message.TextComponent;
import org.kontalk.util.CPIMMessage;
import org.kontalk.util.DataUtils;
import org.kontalk.util.XMPPUtils;

import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INTEGRITY_CHECK;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_DATA;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_RECIPIENT;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_SENDER;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_TIMESTAMP;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_VERIFICATION_FAILED;
import static org.kontalk.crypto.VerifyException.VERIFY_EXCEPTION_INVALID_DATA;
import static org.kontalk.crypto.VerifyException.VERIFY_EXCEPTION_VERIFICATION_FAILED;


/**
 * PGP coder implementation.
 * @author Daniele Ricci
 */
public class PGPCoder extends Coder {

    private static final KeyFingerPrintCalculator sFingerprintCalculator =
        PGP.sFingerprintCalculator;

    /** Buffer size. It should always be a power of 2. */
    private static final int BUFFER_SIZE = 1 << 8;

    private final EndpointServer mServer;
    private final PersonalKey mKey;

    // either one of these two has a value

    private final PGPPublicKeyRing[] mRecipients;
    private final PGPPublicKeyRing mSender;

    public PGPCoder(EndpointServer server, PersonalKey key, PGPPublicKeyRing[] recipients) {
        mServer = server;
        mKey = key;
        mRecipients = recipients;
        mSender = null;
    }

    public PGPCoder(EndpointServer server, PersonalKey key, PGPPublicKeyRing sender) {
        mServer = server;
        mKey = key;
        mRecipients = null;
        mSender = sender;
    }

    @Override
    public int getSupportedFlags() {
        return Coder.SECURITY_BASIC;
    }

    @Deprecated
    public byte[] encryptText(CharSequence text) throws GeneralSecurityException {
        try {
            // consider plain text
            return encryptData("text/plain", text);
        }

        catch (PGPException e) {
            throw new GeneralSecurityException(e);
        }

        catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private byte[] encryptStanza(CharSequence xml) throws GeneralSecurityException {
        try {
            // prepare XML wrapper
            final String xmlWrapper =
                "<xmpp xmlns='jabber:client'>" +
                    xml +
                "</xmpp>";

            return encryptData(XMPPUtils.XML_XMPP_TYPE, xmlWrapper);
        }

        catch (PGPException e) {
            throw new GeneralSecurityException(e);
        }

        catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    @Override
    public Message encryptMessage(Message message, String placeholder) throws GeneralSecurityException {
        byte[] toMessage = encryptStanza(message.toXML());

        org.jivesoftware.smack.packet.Message encMsg =
            new org.jivesoftware.smack.packet.Message(message.getTo(), message.getType());

        encMsg.setBody(placeholder);
        encMsg.setStanzaId(message.getStanzaId());
        encMsg.addExtension(new E2EEncryption(toMessage));
        return encMsg;
    }

    private byte[] encryptData(String mime, CharSequence data)
            throws PGPException, IOException, SignatureException {

        String from = mKey.getUserId(mServer.getNetwork());
        String[] to = new String[mRecipients.length];
        for (int i = 0; i < to.length; i++)
            to[i] = PGP.getUserId(PGP.getMasterKey(mRecipients[i]), mServer.getNetwork());

        // secure the message against the most basic attacks using Message/CPIM
        CPIMMessage cpim = new CPIMMessage(from, to, new Date(), mime, data);
        byte[] plainText = cpim.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(plainText);

        // setup data encryptor & generator
        BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
        encryptor.setWithIntegrityPacket(true);
        encryptor.setSecureRandom(new SecureRandom());

        // add public key recipients
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
        for (PGPPublicKeyRing rcpt : mRecipients)
            encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(PGP.getEncryptionKey(rcpt)));

        OutputStream encryptedOut = encGen.open(out, new byte[BUFFER_SIZE]);

        // setup compressed data generator
        PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
        OutputStream compressedOut = compGen.open(encryptedOut, new byte[BUFFER_SIZE]);

        // setup signature generator
        PGPSignatureGenerator sigGen = new PGPSignatureGenerator
                (new BcPGPContentSignerBuilder(mKey.getSignKeyPair()
                    .getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
        sigGen.init(PGPSignature.BINARY_DOCUMENT, mKey.getSignKeyPair().getPrivateKey());

        PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
        spGen.setSignerUserID(false, mKey.getUserId(mServer.getNetwork()));
        sigGen.setUnhashedSubpackets(spGen.generate());

        sigGen.generateOnePassVersion(false)
            .encode(compressedOut);

        // Initialize literal data generator
        PGPLiteralDataGenerator literalGen = new PGPLiteralDataGenerator();
        OutputStream literalOut = literalGen.open(
            compressedOut,
            PGPLiteralData.BINARY,
            "",
            new Date(),
            new byte[BUFFER_SIZE]);

        // read the "in" stream, compress, encrypt and write to the "out" stream
        // this must be done if clear data is bigger than the buffer size
        // but there are other ways to optimize...
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = in.read(buf)) > 0) {
            literalOut.write(buf, 0, len);
            sigGen.update(buf, 0, len);
        }

        in.close();
        literalGen.close();
        // Generate the signature, compress, encrypt and write to the "out" stream
        sigGen.generate().encode(compressedOut);
        compGen.close();
        encGen.close();

        return out.toByteArray();
    }

    @Override
    public DecryptOutput decryptMessage(Message message, boolean verify) throws GeneralSecurityException {
        ExtensionElement _encrypted = message.getExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);
        if (!(_encrypted instanceof E2EEncryption)) {
            throw new DecryptException(DECRYPT_EXCEPTION_INVALID_DATA, "Not an encrypted message");
        }

        E2EEncryption encrypted = (E2EEncryption) _encrypted;
        byte[] encryptedData = encrypted.getData();

        return decryptText(encryptedData, message, verify);
    }

    @SuppressWarnings("unchecked")
    private DecryptOutput decryptText(byte[] encrypted, Message origin, boolean verify)
                throws GeneralSecurityException {

        List<DecryptException> errors = new ArrayList<>();
        String mime = null;
        Date timestamp = null;
        String out = null;
        InputStream cDataIn = null;

        try {
            PGPObjectFactory pgpF = new PGPObjectFactory(encrypted, sFingerprintCalculator);
            PGPEncryptedDataList enc;

            Object o = pgpF.nextObject();

            // the first object might be a PGP marker packet
            if (o instanceof PGPEncryptedDataList) {
                enc = (PGPEncryptedDataList) o;
            }
            else {
                enc = (PGPEncryptedDataList) pgpF.nextObject();
            }

            // check if secret key matches
            Iterator<PGPEncryptedData> it = enc.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;

            // our encryption keyID
            long ourKeyID = mKey.getEncryptKeyPair().getPrivateKey().getKeyID();

            while (sKey == null && it.hasNext()) {
                pbe = (PGPPublicKeyEncryptedData) it.next();

                if (pbe.getKeyID() == ourKeyID)
                    sKey = mKey.getEncryptKeyPair().getPrivateKey();
            }

            if (sKey == null) {
                // unrecoverable situation
                throw new DecryptException(
                    DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND,
                    "Secret key for message not found.");
            }

            InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(sKey));

            PGPObjectFactory plainFact = new PGPObjectFactory(clear, sFingerprintCalculator);

            Object message = plainFact.nextObject();

            CharSequence msgData = null;

            if (message instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) message;
                cDataIn = cData.getDataStream();
                PGPObjectFactory pgpFact = new PGPObjectFactory(cDataIn, sFingerprintCalculator);

                message = pgpFact.nextObject();

                PGPOnePassSignature ops = null;
                if (message instanceof PGPOnePassSignatureList) {
                    if (verify && mSender != null) {
                        ops = ((PGPOnePassSignatureList) message).get(0);
                        try {
                            ops.init(new BcPGPContentVerifierBuilderProvider(), PGP.getSigningKey(mSender));
                        }
                        catch (ClassCastException e) {
                            try {
                                // workaround for backward compatibility
                                ops.init(new BcPGPContentVerifierBuilderProvider(), PGP.getMasterKey(mSender));
                            }
                            catch (ClassCastException e2) {
                                // peer used new ECC key to sign, but we still have the old RSA one
                                // no verification is possible
                                ops = null;
                            }
                        }
                    }

                    message = pgpFact.nextObject();
                }

                if (message instanceof PGPLiteralData) {
                    PGPLiteralData ld = (PGPLiteralData) message;

                    InputStream unc = ld.getInputStream();
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();

                    byte[] buf = new byte[4096];
                    int num;

                    while ((num = unc.read(buf)) >= 0) {
                        bout.write(buf, 0, num);

                        if (ops != null)
                            ops.update(buf, 0, num);
                    }

                    if (verify) {
                        if (ops == null) {
                            errors.add(new DecryptException(
                                DECRYPT_EXCEPTION_VERIFICATION_FAILED,
                                "No signature list found"));
                        }

                        message = pgpFact.nextObject();

                        if (ops != null) {

                            if (message instanceof PGPSignatureList) {
                                PGPSignature signature = ((PGPSignatureList) message).get(0);
                                if (!ops.verify(signature)) {
                                    errors.add(new DecryptException(
                                        DECRYPT_EXCEPTION_VERIFICATION_FAILED,
                                        "Signature verification failed"));
                                }
                            }

                            else {
                                errors.add(new DecryptException(
                                        DECRYPT_EXCEPTION_INVALID_DATA,
                                        "Invalid signature packet"));
                            }

                        }

                    }

                    // verify message integrity
                    if (pbe.isIntegrityProtected()) {
                        try {
                            if (!pbe.verify()) {
                                // unrecoverable situation
                                throw new DecryptException(
                                    DECRYPT_EXCEPTION_INTEGRITY_CHECK,
                                    "Message integrity check failed");
                            }
                        }
                        catch (PGPException e) {
                            // unrecoverable situation
                            throw new DecryptException(
                                DECRYPT_EXCEPTION_INTEGRITY_CHECK,
                                e);
                        }
                    }

                    String data = bout.toString();

                    try {
                        // parse and check Message/CPIM
                        CPIMMessage msg = CPIMMessage.parse(data);

                        mime = msg.getMime();

                        msgData = msg.getBody();

                        if (verify) {
                            // verify CPIM headers, including mime type must be either text or xml

                            // check mime type
                            if (!TextComponent.MIME_TYPE.equalsIgnoreCase(msg.getMime()) &&
                                    !XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(msg.getMime())) {
                                // unrecoverable situation
                                throw new DecryptException(
                                    DECRYPT_EXCEPTION_INTEGRITY_CHECK,
                                    "MIME type mismatch");
                            }

                            // check that the recipient matches the full uid of the personal key
                            boolean foundTo = false;
                            String myUid = mKey.getUserId(mServer.getNetwork());
                            String[] msgTo = msg.getTo();
                            if (msgTo != null) {
                                for (String to : msgTo) {
                                    if (myUid.equals(to)) {
                                        foundTo = true;
                                        break;
                                    }
                                }
                            }
                            if (!foundTo) {
                                errors.add(new DecryptException(
                                    DECRYPT_EXCEPTION_INVALID_RECIPIENT,
                                    "Destination does not match personal key"));
                            }

                            // check that the sender matches the full uid of the sender's key
                            if (mSender != null) {
                                String otherUid = PGP.getUserId(PGP.getMasterKey(mSender), mServer.getNetwork());
                                if (!otherUid.equals(msg.getFrom())) {
                                    errors.add(new DecryptException(
                                        DECRYPT_EXCEPTION_INVALID_SENDER,
                                        "Sender does not match sender's key"));
                                }
                            }
                            else {
                                errors.add(new DecryptException(
                                    DECRYPT_EXCEPTION_VERIFICATION_FAILED,
                                    "No public key available to verify sender"));
                            }

                            timestamp = msg.getDate();
                            if (timestamp == null) {
                                errors.add(new DecryptException(
                                    DECRYPT_EXCEPTION_INVALID_TIMESTAMP,
                                    "Invalid timestamp"));
                            }

                            // check DateTime (plain text only, <delay/> is left to the caller)
                            if (TextComponent.MIME_TYPE.equalsIgnoreCase(msg.getMime())) {
                                long time = msg.getDate().getTime();
                                long now = System.currentTimeMillis();
                                long diff = Math.abs(now - time);
                                if (diff > TIMEDIFF_THRESHOLD) {
                                    errors.add(new DecryptException(
                                        DECRYPT_EXCEPTION_INVALID_TIMESTAMP,
                                        "Drifted timestamp"));
                                }
                            }
                        }

                    }
                    catch (ParseException pe) {
                        // return data as-is
                        msgData = data;

                        if (verify) {
                            // verification requested: invalid CPIM data
                            errors.add(new DecryptException(
                                DECRYPT_EXCEPTION_INVALID_DATA, pe,
                                "Verification was requested but no CPIM valid data was found"));
                        }
                    }

                    catch (DecryptException de) {
                        errors.add(de);
                    }

                    finally {
                        if (msgData != null)
                            out = String.valueOf(msgData);
                    }

                }
                else {
                    // invalid or unknown packet
                    throw new DecryptException(
                        DECRYPT_EXCEPTION_INVALID_DATA,
                        "Unknown packet type " + message.getClass().getName());
                }

            }

            else {
                throw new DecryptException(DecryptException
                    .DECRYPT_EXCEPTION_INVALID_DATA,
                    "Compressed data packet expected");
            }

        }

        // unrecoverable situations

        catch (IOException ioe) {
            throw new DecryptException(DECRYPT_EXCEPTION_INVALID_DATA, ioe);
        }

        catch (PGPException pe) {
            throw new DecryptException(DECRYPT_EXCEPTION_INVALID_DATA, pe);
        }

        finally {
            DataUtils.close(cDataIn);
        }

        Message message;
        if (XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(mime)) {
            try {
                message = XMPPUtils.parseMessageStanza(out);
            }
            catch (Exception e) {
                throw new DecryptException(DECRYPT_EXCEPTION_INVALID_DATA, e);
            }

            if (timestamp != null && !checkDriftedDelay(message, timestamp)) {
                errors.add(new DecryptException(DECRYPT_EXCEPTION_INVALID_TIMESTAMP,
                    "Drifted timestamp"));
            }

            // extensions won't be copied because the new message will take over
        }
        else {
            // simple text message
            message = new Message();
            message.setType(origin.getType());
            message.setFrom(origin.getFrom());
            message.setTo(origin.getTo());
            message.setBody(out);
            // copy extensions and remove our own
            message.addExtensions(message.getExtensions());
            message.removeExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);
        }

        return new DecryptOutput(message, mime, timestamp, SECURITY_BASIC, errors);
    }

    @Override
    public void encryptFile(InputStream input, OutputStream output) throws GeneralSecurityException {
        try {
            // setup data encryptor & generator
            BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
            encryptor.setWithIntegrityPacket(true);
            encryptor.setSecureRandom(new SecureRandom());

            // add public key recipients
            PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
            for (PGPPublicKeyRing rcpt : mRecipients)
                encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(PGP.getEncryptionKey(rcpt)));

            OutputStream encryptedOut = encGen.open(output, new byte[BUFFER_SIZE]);

            // setup compressed data generator
            PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            OutputStream compressedOut = compGen.open(encryptedOut, new byte[BUFFER_SIZE]);

            // setup signature generator
            PGPSignatureGenerator sigGen = new PGPSignatureGenerator
                (new BcPGPContentSignerBuilder(mKey.getSignKeyPair()
                    .getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
            sigGen.init(PGPSignature.BINARY_DOCUMENT, mKey.getSignKeyPair().getPrivateKey());

            PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();
            spGen.setSignerUserID(false, mKey.getUserId(mServer.getNetwork()));
            sigGen.setUnhashedSubpackets(spGen.generate());

            sigGen.generateOnePassVersion(false)
                .encode(compressedOut);

            // Initialize literal data generator
            PGPLiteralDataGenerator literalGen = new PGPLiteralDataGenerator();
            OutputStream literalOut = literalGen.open(
                compressedOut,
                PGPLiteralData.BINARY,
                "",
                new Date(),
                new byte[BUFFER_SIZE]);

            // read the "in" stream, compress, encrypt and write to the "out" stream
            // this must be done if clear data is bigger than the buffer size
            // but there are other ways to optimize...
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            while ((len = input.read(buf)) > 0) {
                literalOut.write(buf, 0, len);
                sigGen.update(buf, 0, len);
            }

            literalGen.close();
            // Generate the signature, compress, encrypt and write to the "out" stream
            sigGen.generate().encode(compressedOut);
            compGen.close();
            encGen.close();
        }
        catch (PGPException e) {
            throw new GeneralSecurityException(e);
        }

        catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    /** Decrypts a file. */
    @SuppressWarnings("unchecked")
    public void decryptFile(InputStream input, boolean verify,
        OutputStream output, List<DecryptException> errors)
            throws GeneralSecurityException {

        InputStream cDataIn = null;
        try {
            PGPObjectFactory pgpF = new PGPObjectFactory(input, sFingerprintCalculator);
            PGPEncryptedDataList enc;

            Object o = pgpF.nextObject();

            // the first object might be a PGP marker packet
            if (o instanceof PGPEncryptedDataList) {
                enc = (PGPEncryptedDataList) o;
            }
            else {
                enc = (PGPEncryptedDataList) pgpF.nextObject();
            }

            // check if secret key matches
            Iterator<PGPEncryptedData> it = enc.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;

            // our encryption keyID
            long ourKeyID = mKey.getEncryptKeyPair().getPrivateKey().getKeyID();

            while (sKey == null && it.hasNext()) {
                pbe = (PGPPublicKeyEncryptedData) it.next();

                if (pbe.getKeyID() == ourKeyID)
                    sKey = mKey.getEncryptKeyPair().getPrivateKey();
            }

            if (sKey == null)
                throw new DecryptException(
                    DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND,
                    "Secret key for message not found.");

            InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(sKey));

            PGPObjectFactory plainFact = new PGPObjectFactory(clear, sFingerprintCalculator);

            Object message = plainFact.nextObject();

            if (message instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) message;
                cDataIn = cData.getDataStream();
                PGPObjectFactory pgpFact = new PGPObjectFactory(cDataIn, sFingerprintCalculator);

                message = pgpFact.nextObject();

                PGPOnePassSignature ops = null;
                if (message instanceof PGPOnePassSignatureList) {
                    if (verify && mSender != null) {
                        ops = ((PGPOnePassSignatureList) message).get(0);
                        ops.init(new BcPGPContentVerifierBuilderProvider(), PGP.getSigningKey(mSender));
                    }

                    message = pgpFact.nextObject();
                }

                if (message instanceof PGPLiteralData) {
                    PGPLiteralData ld = (PGPLiteralData) message;

                    InputStream unc = ld.getInputStream();
                    byte[] buf = new byte[8192];
                    int num;

                    while ((num = unc.read(buf)) >= 0) {
                        output.write(buf, 0, num);

                        if (ops != null)
                            ops.update(buf, 0, num);
                    }

                    if (verify) {
                        if (ops == null) {
                            if (errors != null)
                                errors.add(new DecryptException(
                                    DECRYPT_EXCEPTION_VERIFICATION_FAILED,
                                    "No signature list found"));
                        }

                        message = pgpFact.nextObject();

                        if (ops != null) {

                            if (message instanceof PGPSignatureList) {
                                PGPSignature signature = ((PGPSignatureList) message).get(0);
                                if (!ops.verify(signature)) {
                                    if (errors != null)
                                        errors.add(new DecryptException(
                                            DECRYPT_EXCEPTION_VERIFICATION_FAILED,
                                            "Signature verification failed"));
                                }
                            }

                            else {
                                if (errors != null)
                                    errors.add(new DecryptException(
                                        DECRYPT_EXCEPTION_INVALID_DATA,
                                        "Invalid signature packet"));
                            }

                        }

                    }

                    // verify message integrity
                    if (pbe.isIntegrityProtected()) {
                        try {
                            if (!pbe.verify()) {
                                // unrecoverable situation
                                throw new DecryptException(
                                    DECRYPT_EXCEPTION_INTEGRITY_CHECK,
                                    "Message integrity check failed");
                            }
                        }
                        catch (PGPException e) {
                            // unrecoverable situation
                            throw new DecryptException(
                                DECRYPT_EXCEPTION_INTEGRITY_CHECK,
                                e);
                        }
                    }

                }
                else {
                    // invalid or unknown packet
                    throw new DecryptException(
                        DECRYPT_EXCEPTION_INVALID_DATA,
                        "Unknown packet type " + message.getClass().getName());
                }

            }

            else {
                throw new DecryptException(DecryptException
                    .DECRYPT_EXCEPTION_INVALID_DATA,
                    "Compressed data packet expected");
            }

        }

        // unrecoverable situations

        catch (IOException ioe) {
            throw new DecryptException(DECRYPT_EXCEPTION_INVALID_DATA, ioe);
        }

        catch (PGPException pe) {
            throw new DecryptException(DECRYPT_EXCEPTION_INVALID_DATA, pe);
        }

        finally {
            DataUtils.close(cDataIn);
        }
    }

    @Override
    public VerifyOutput verifyText(byte[] signed, boolean verify) throws GeneralSecurityException {
        List<VerifyException> errors = new ArrayList<>();
        Date timestamp;
        String out;

        try {
            PGPObjectFactory plainFact = new PGPObjectFactory(signed, sFingerprintCalculator);

            Object message = plainFact.nextObject();

            if (message instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) message;
                PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream(), sFingerprintCalculator);

                message = pgpFact.nextObject();

                PGPOnePassSignature ops = null;
                if (message instanceof PGPOnePassSignatureList) {
                    if (verify && mSender != null) {
                        ops = ((PGPOnePassSignatureList) message).get(0);
                        try {
                            ops.init(new BcPGPContentVerifierBuilderProvider(), PGP.getSigningKey(mSender));
                        }
                        catch (ClassCastException e) {
                            try {
                                // workaround for backward compatibility
                                ops.init(new BcPGPContentVerifierBuilderProvider(), PGP.getMasterKey(mSender));
                            }
                            catch (ClassCastException e2) {
                                // peer used new ECC key to sign, but we still have the old RSA one
                                // no verification is possible
                                ops = null;
                            }
                        }
                    }

                    message = pgpFact.nextObject();
                }

                if (message instanceof PGPLiteralData) {
                    PGPLiteralData ld = (PGPLiteralData) message;

                    timestamp = ld.getModificationTime();
                    InputStream unc = ld.getInputStream();
                    ByteArrayOutputStream bout = new ByteArrayOutputStream();

                    byte[] buf = new byte[4096];
                    int num;

                    while ((num = unc.read(buf)) >= 0) {
                        bout.write(buf, 0, num);

                        if (ops != null)
                            ops.update(buf, 0, num);
                    }

                    if (verify) {
                        if (ops == null) {
                            errors.add(new VerifyException(
                                VERIFY_EXCEPTION_VERIFICATION_FAILED,
                                "No signature list found"));
                        }

                        message = pgpFact.nextObject();

                        if (ops != null) {

                            if (message instanceof PGPSignatureList) {
                                PGPSignature signature = ((PGPSignatureList) message).get(0);
                                if (!ops.verify(signature)) {
                                    errors.add(new VerifyException(
                                        VERIFY_EXCEPTION_VERIFICATION_FAILED,
                                        "Signature verification failed"));
                                }
                            }

                            else {
                                errors.add(new VerifyException(
                                    VERIFY_EXCEPTION_INVALID_DATA,
                                    "Invalid signature packet"));
                            }

                        }

                    }

                    out = bout.toString();
                }
                else {
                    // invalid or unknown packet
                    throw new VerifyException(
                        VERIFY_EXCEPTION_INVALID_DATA,
                        "Unknown packet type " + message.getClass().getName());
                }

            }

            else {
                throw new VerifyException(
                    VERIFY_EXCEPTION_INVALID_DATA,
                    "Compressed data packet expected");
            }

        }

        // unrecoverable situations

        catch (IOException ioe) {
            throw new VerifyException(VERIFY_EXCEPTION_INVALID_DATA, ioe);
        }

        catch (PGPException pe) {
            throw new VerifyException(VERIFY_EXCEPTION_INVALID_DATA, pe);
        }

        return new VerifyOutput(out, timestamp, errors);
    }

    private static boolean checkDriftedDelay(Message m, Date expected) {
        Date stamp = XMPPUtils.getStanzaDelay(m);
        if (stamp != null) {
            long time = stamp.getTime();
            long now = expected.getTime();
            long diff = Math.abs(now - time);
            return (diff < Coder.TIMEDIFF_THRESHOLD);
        }

        // no timestamp found
        return true;
    }

}

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
package org.kontalk.crypto;

import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INTEGRITY_CHECK;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_DATA;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_RECIPIENT;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_SENDER;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND;
import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_VERIFICATION_FAILED;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.kontalk.client.EndpointServer;
import org.kontalk.message.TextComponent;
import org.kontalk.util.CPIMMessage;
import org.kontalk.util.XMPPUtils;
import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.openpgp.PGPCompressedData;
import org.spongycastle.openpgp.PGPCompressedDataGenerator;
import org.spongycastle.openpgp.PGPEncryptedData;
import org.spongycastle.openpgp.PGPEncryptedDataGenerator;
import org.spongycastle.openpgp.PGPEncryptedDataList;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPLiteralData;
import org.spongycastle.openpgp.PGPLiteralDataGenerator;
import org.spongycastle.openpgp.PGPObjectFactory;
import org.spongycastle.openpgp.PGPOnePassSignature;
import org.spongycastle.openpgp.PGPOnePassSignatureList;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyEncryptedData;
import org.spongycastle.openpgp.PGPSignature;
import org.spongycastle.openpgp.PGPSignatureGenerator;
import org.spongycastle.openpgp.PGPSignatureList;
import org.spongycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.spongycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.spongycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.spongycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.spongycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;


/**
 * PGP coder implementation.
 * @author Daniele Ricci
 */
public class PGPCoder extends Coder {

    /** Buffer size. It should always be a power of 2. */
    private static final int BUFFER_SIZE = 1 << 8;

    private final EndpointServer mServer;
    private final PersonalKey mKey;

    // either one of these two has a value

    private final PGPPublicKey[] mRecipients;
    private final PGPPublicKey mSender;

    public PGPCoder(EndpointServer server, PersonalKey key, PGPPublicKey[] recipients) {
        mServer = server;
        mKey = key;
        mRecipients = recipients;
        mSender = null;
    }

    public PGPCoder(EndpointServer server, PersonalKey key, PGPPublicKey sender) {
        mServer = server;
        mKey = key;
        mRecipients = null;
        mSender = sender;
    }

    @Override
    public byte[] encryptText(String text) throws GeneralSecurityException {
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

    @Override
    public byte[] encryptStanza(String xml) throws GeneralSecurityException {
        try {
            // prepare XML wrapper
            StringBuilder xmlWrapper = new StringBuilder(
            			"<xmpp xmlns='jabber:client'>")
            	.append(  xml  )
            	.append("</xmpp>");

            return encryptData(XMPPUtils.XML_XMPP_TYPE, xmlWrapper.toString());
        }

        catch (PGPException e) {
            throw new GeneralSecurityException(e);
        }

        catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private byte[] encryptData(String mime, String data)
    		throws PGPException, IOException, SignatureException {

        String from = mKey.getUserId(mServer.getNetwork());
        StringBuilder to = new StringBuilder();
        for (PGPPublicKey rcpt : mRecipients)
            to.append(PGP.getUserId(rcpt, mServer.getNetwork()))
                .append("; ");

        // secure the message against the most basic attacks using Message/CPIM
        CPIMMessage cpim = new CPIMMessage(from, to.toString(), new Date(), mime, data);
        byte[] plainText = cpim.toByteArray();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(plainText);

        // setup data encryptor & generator
        BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.AES_192);
        encryptor.setWithIntegrityPacket(true);
        encryptor.setSecureRandom(new SecureRandom());

        // add public key recipients
        PGPEncryptedDataGenerator encGen = new PGPEncryptedDataGenerator(encryptor);
        for (PGPPublicKey rcpt : mRecipients)
            encGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(rcpt));

        OutputStream encryptedOut = encGen.open(out, new byte[BUFFER_SIZE]);

        // setup compressed data generator
        PGPCompressedDataGenerator compGen = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
        OutputStream compressedOut = compGen.open(encryptedOut, new byte[BUFFER_SIZE]);

        // setup signature generator
        PGPSignatureGenerator sigGen = new PGPSignatureGenerator
                (new BcPGPContentSignerBuilder(mKey.getSignKeyPair()
                    .getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1));
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

    @SuppressWarnings("unchecked")
    @Override
    public void decryptText(byte[] encrypted, boolean verify,
    		StringBuilder out, StringBuilder mime, List<DecryptException> errors)
    				throws GeneralSecurityException {

        try {
            PGPObjectFactory pgpF = new PGPObjectFactory(encrypted);
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
            Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
            PGPPrivateKey sKey = null;
            PGPPublicKeyEncryptedData pbe = null;

            // our encryption keyID
            long ourKeyID = mKey.getEncryptKeyPair().getPrivateKey().getKeyID();

            while (sKey == null && it.hasNext()) {
                pbe = it.next();

                if (pbe.getKeyID() == ourKeyID)
                    sKey = mKey.getEncryptKeyPair().getPrivateKey();
            }

            if (sKey == null)
                throw new DecryptException(
                	DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND,
                	"Secret key for message not found.");

            InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(sKey));

            PGPObjectFactory plainFact = new PGPObjectFactory(clear);

            Object message = plainFact.nextObject();

            CharSequence msgData = null;

            if (message instanceof PGPCompressedData) {
                PGPCompressedData cData = (PGPCompressedData) message;
                PGPObjectFactory pgpFact = new PGPObjectFactory(cData.getDataStream());

                message = pgpFact.nextObject();

                PGPOnePassSignature ops = null;
                if (message instanceof PGPOnePassSignatureList) {
                	if (verify) {
                		ops = ((PGPOnePassSignatureList) message).get(0);
                		ops.init(new BcPGPContentVerifierBuilderProvider(), mSender);
                	}

                    message = pgpFact.nextObject();
                }

                if (message instanceof PGPLiteralData) {
                    PGPLiteralData ld = (PGPLiteralData) message;

                    InputStream unc = ld.getInputStream();
                    int ch;

                    ByteArrayOutputStream bout = new ByteArrayOutputStream();

                    while ((ch = unc.read()) >= 0) {
                        bout.write(ch);

                        if (ops != null)
                        	ops.update((byte) ch);
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

                    String data = bout.toString();

                    try {
	                    // parse and check Message/CPIM
	                    CPIMMessage msg = CPIMMessage.parse(data);

	                    if (mime != null)
	                    	mime.append(msg.getMime());

	                    msgData = msg.getBody();

	                    if (verify) {
	                    	// verify CPIM headers, including mime type must be either text or xml

	                    	// check mime type
	                    	if (!TextComponent.MIME_TYPE.equalsIgnoreCase(msg.getMime()) &&
	                    			!XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(msg.getMime()))
	                    		throw new DecryptException(
	                    			DECRYPT_EXCEPTION_INTEGRITY_CHECK,
	                    			"MIME type mismatch");

	                    	// check that the recipient matches the full uid of the personal key
	                    	String myUid = mKey.getUserId(mServer.getNetwork());
	                    	if (!myUid.equals(msg.getTo()))
	                    		throw new DecryptException(
	                    			DECRYPT_EXCEPTION_INVALID_RECIPIENT,
	                    			"Destination does not match personal key");

	                    	// check that the sender matches the full uid of the sender's key
	                    	String otherUid = PGP.getUserId(mSender, mServer.getNetwork());
	                    	if (!otherUid.equals(msg.getFrom()))
	                    		throw new DecryptException(
	                    			DECRYPT_EXCEPTION_INVALID_SENDER,
	                    			"Sender does not match sender's key");

	                    	// TODO check DateTime (possibly compare it with <delay/>)
	                    }

                    }
                    catch (ParseException pe) {
                		// return data as-is
                		msgData = data;

                    	if (verify && errors != null)
                    		// verification requested: invalid CPIM data
                    		errors.add(new DecryptException(
	                    		DECRYPT_EXCEPTION_INVALID_DATA, pe,
	                    		"Verification was requested but no CPIM valid data was found"));
                    }

                    catch (DecryptException de) {
                    	if (errors != null)
                    		errors.add(de);
                    }

                    finally {
                    	if (msgData != null)
                    		out.append(msgData);
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

    }

    public InputStream wrapInputStream(InputStream inputStream) throws GeneralSecurityException {
        // TODO
        return null;
        //return new CipherInputStream(inputStream, TODO);
    }

    public OutputStream wrapOutputStream(OutputStream outputStream) throws GeneralSecurityException {
        // TODO
        return null;
        //return new CipherOutputStream(outputStream, TODO);
    }

    public long getEncryptedLength(long decryptedLength) {
        // TODO
        return 0;
    }
}

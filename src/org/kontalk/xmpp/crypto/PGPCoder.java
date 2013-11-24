/*
 * Kontalk Android client
 * Copyright (C) 2013 Kontalk Devteam <devteam@kontalk.org>

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
package org.kontalk.xmpp.crypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Date;

import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.openpgp.PGPCompressedData;
import org.spongycastle.openpgp.PGPCompressedDataGenerator;
import org.spongycastle.openpgp.PGPEncryptedData;
import org.spongycastle.openpgp.PGPEncryptedDataGenerator;
import org.spongycastle.openpgp.PGPLiteralData;
import org.spongycastle.openpgp.PGPLiteralDataGenerator;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPSignature;
import org.spongycastle.openpgp.PGPSignatureGenerator;
import org.spongycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.spongycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.spongycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;


/**
 * PGP coder implementation.
 * @author Daniele Ricci
 */
public class PGPCoder implements Coder {

    /** Buffer size. It should always be a power of 2. */
    private static final int BUFFER_SIZE = 1 << 8;

    private final PGPPublicKey[] mRecipients;
    private final PersonalKey mKey;

    public PGPCoder(PersonalKey key, PGPPublicKey[] recipients) {
        mKey = key;
        mRecipients = recipients;
    }

    public byte[] encrypt(byte[] unencrypted) throws GeneralSecurityException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayInputStream in = new ByteArrayInputStream(unencrypted);

            // setup data encryptor & generator
            BcPGPDataEncryptorBuilder encryptor = new BcPGPDataEncryptorBuilder(PGPEncryptedData.TRIPLE_DES);
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
            // TODO null network
            spGen.setSignerUserID(false, mKey.getUserId(null));
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

        catch (Exception e) {
            throw new GeneralSecurityException(e);
        }
    }

    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException {
        // TODO
        return null;
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

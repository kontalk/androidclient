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

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;

import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.openpgp.PGPEncryptedData;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPKeyPair;
import org.spongycastle.openpgp.PGPKeyRingGenerator;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.spongycastle.openpgp.PGPSignature;
import org.spongycastle.openpgp.PGPSignatureGenerator;
import org.spongycastle.openpgp.PGPUtil;
import org.spongycastle.openpgp.operator.PGPDigestCalculator;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;

import android.os.Parcel;


/** Some PGP utility method, mainly for use by {@link PersonalKey}. */
public class PGP {

    /** Security provider: Spongy Castle. */
    public static final String PROVIDER = "SC";

    /** Default EC curve used. */
    private static final String EC_CURVE = "P-256";

    /** Default RSA key length used. */
    private static final int RSA_KEY_LENGTH = 2048;

    // temporary flag for ECC experimentation
    private static final boolean EXPERIMENTAL_ECC = false;

    private PGP() {
    }

    public static final class PGPDecryptedKeyPairRing {
        /* Master (signing) key. */
        PGPKeyPair signKey;
        /* Sub (encryption) key. */
        PGPKeyPair encryptKey;

        public PGPDecryptedKeyPairRing(PGPKeyPair sign, PGPKeyPair encrypt) {
            this.signKey = sign;
            this.encryptKey = encrypt;
        }
    }

    public static final class PGPKeyPairRing {
        public PGPPublicKeyRing publicKey;
        public PGPSecretKeyRing secretKey;

        PGPKeyPairRing(PGPPublicKeyRing publicKey, PGPSecretKeyRing secretKey) {
            this.publicKey = publicKey;
            this.secretKey = secretKey;
        }
    }

    public static void registerProvider() {
        // register spongy castle provider
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    /** Creates an ECDSA/ECDH key pair. */
    public static PGPDecryptedKeyPairRing create()
            throws NoSuchAlgorithmException, NoSuchProviderException, PGPException, InvalidAlgorithmParameterException {

        KeyPairGenerator gen;
        PGPKeyPair encryptKp, signKp;

        if (EXPERIMENTAL_ECC) {

            gen = KeyPairGenerator.getInstance("ECDH", PROVIDER);
            gen.initialize(new ECGenParameterSpec(EC_CURVE));

            encryptKp = new JcaPGPKeyPair(PGPPublicKey.ECDH, gen.generateKeyPair(), new Date());

            gen = KeyPairGenerator.getInstance("ECDSA", PROVIDER);
            gen.initialize(new ECGenParameterSpec(EC_CURVE));

            signKp = new JcaPGPKeyPair(PGPPublicKey.ECDSA, gen.generateKeyPair(), new Date());
        }

        else {

            gen = KeyPairGenerator.getInstance("RSA", PROVIDER);
            gen.initialize(RSA_KEY_LENGTH);

            encryptKp = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, gen.generateKeyPair(), new Date());

            gen = KeyPairGenerator.getInstance("RSA", PROVIDER);
            gen.initialize(RSA_KEY_LENGTH);

            signKp = new JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, gen.generateKeyPair(), new Date());
        }

        return new PGPDecryptedKeyPairRing(signKp, encryptKp);
    }

    /** Creates public and secret keyring for a given keypair. */
    public static PGPKeyPairRing store(PGPDecryptedKeyPairRing pair,
            String id,
            String passphrase)
                throws PGPException {

        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        PGPKeyRingGenerator keyRingGen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, pair.signKey,
            id, sha1Calc, null, null,
            new JcaPGPContentSignerBuilder(pair.signKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1),
            new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha1Calc)
                .setProvider(PROVIDER).build(passphrase.toCharArray()));

        keyRingGen.addSubKey(pair.encryptKey);

        PGPSecretKeyRing secRing = keyRingGen.generateSecretKeyRing();
        PGPPublicKeyRing pubRing = keyRingGen.generatePublicKeyRing();

        return new PGPKeyPairRing(pubRing, secRing);
    }

    public static PGPPublicKey signPublicKey(PGPKeyPair secret, PGPPublicKey keyToBeSigned, String id)
            throws PGPException, IOException, SignatureException {

        PGPPrivateKey pgpPrivKey = secret.getPrivateKey();

        PGPSignatureGenerator       sGen = new PGPSignatureGenerator(
            new JcaPGPContentSignerBuilder(secret.getPublicKey().getAlgorithm(),
                PGPUtil.SHA1).setProvider(PROVIDER));

        sGen.init(PGPSignature.CASUAL_CERTIFICATION, pgpPrivKey);

        return PGPPublicKey.addCertification(keyToBeSigned, id, sGen.generateCertification(id, keyToBeSigned));
    }

    public static PGPDecryptedKeyPairRing fromParcel(Parcel in) throws PGPException {
        JcaPGPKeyConverter conv = new JcaPGPKeyConverter().setProvider(PROVIDER);

        // TODO read byte data
        PrivateKey privSign = (PrivateKey) in.readSerializable();
        PublicKey pubSign = (PublicKey) in.readSerializable();
        int algoSign = in.readInt();
        Date dateSign = new Date(in.readLong());

        PGPPublicKey pubKeySign = conv.getPGPPublicKey(algoSign, pubSign, dateSign);
        PGPPrivateKey privKeySign = conv.getPGPPrivateKey(pubKeySign, privSign);
        PGPKeyPair signKp = new PGPKeyPair(pubKeySign, privKeySign);

        PrivateKey privEnc = (PrivateKey) in.readSerializable();
        PublicKey pubEnc = (PublicKey) in.readSerializable();
        int algoEnc = in.readInt();
        Date dateEnc = new Date(in.readLong());

        PGPPublicKey pubKeyEnc = conv.getPGPPublicKey(algoEnc, pubEnc, dateEnc);
        PGPPrivateKey privKeyEnc = conv.getPGPPrivateKey(pubKeyEnc, privEnc);
        PGPKeyPair encryptKp = new PGPKeyPair(pubKeyEnc, privKeyEnc);

        return new PGPDecryptedKeyPairRing(signKp, encryptKp);
    }

    public static void toParcel(PGPDecryptedKeyPairRing pair, Parcel dest)
            throws NoSuchProviderException, PGPException {

        // FIXME using deprecated methods
        PrivateKey privSign = pair.signKey.getPrivateKey().getKey();
        PublicKey pubSign = pair.signKey.getPublicKey().getKey(PROVIDER);
        int algoSign = pair.signKey.getPrivateKey().getPublicKeyPacket().getAlgorithm();
        Date dateSign = pair.signKey.getPrivateKey().getPublicKeyPacket().getTime();

        PrivateKey privEnc = pair.encryptKey.getPrivateKey().getKey();
        PublicKey pubEnc = pair.encryptKey.getPublicKey().getKey(PROVIDER);
        int algoEnc = pair.encryptKey.getPrivateKey().getPublicKeyPacket().getAlgorithm();
        Date dateEnc = pair.encryptKey.getPrivateKey().getPublicKeyPacket().getTime();

        dest.writeSerializable(privSign);
        dest.writeSerializable(pubSign);
        dest.writeInt(algoSign);
        dest.writeLong(dateSign.getTime());

        dest.writeSerializable(privEnc);
        dest.writeSerializable(pubEnc);
        dest.writeInt(algoEnc);
        dest.writeLong(dateEnc.getTime());

    }

}

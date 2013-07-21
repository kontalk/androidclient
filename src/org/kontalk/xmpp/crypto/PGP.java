package org.kontalk.xmpp.crypto;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.SignatureException;
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
import org.spongycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.spongycastle.openpgp.PGPSignatureSubpacketVector;
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
    private static final String PROVIDER = "SC";

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

    // TODO one day this will be ECDSA
    public static PGPDecryptedKeyPairRing create(int keysize)
            throws NoSuchAlgorithmException, NoSuchProviderException, PGPException {

        KeyPairGenerator gen = KeyPairGenerator.getInstance("ElGamal", PROVIDER);
        gen.initialize(keysize, new SecureRandom());

        PGPKeyPair encryptKp = new JcaPGPKeyPair(PGPPublicKey.ELGAMAL_ENCRYPT, gen.generateKeyPair(), new Date());

        gen = KeyPairGenerator.getInstance("DSA", PROVIDER);
        PGPKeyPair signKp = new JcaPGPKeyPair(PGPPublicKey.DSA, gen.generateKeyPair(), new Date());

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
                PGPUtil.SHA256).setProvider(PROVIDER));

        sGen.init(PGPSignature.DIRECT_KEY, pgpPrivKey);

        PGPSignatureSubpacketGenerator spGen = new PGPSignatureSubpacketGenerator();

        PGPSignatureSubpacketVector packetVector = spGen.generate();
        sGen.setHashedSubpackets(packetVector);

        return PGPPublicKey.addCertification(keyToBeSigned, id, sGen.generate());
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

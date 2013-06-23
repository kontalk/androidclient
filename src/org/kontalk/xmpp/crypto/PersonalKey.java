/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Date;

import org.spongycastle.bcpg.ArmoredOutputStream;
import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.openpgp.PGPEncryptedData;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPKeyPair;
import org.spongycastle.openpgp.PGPKeyRing;
import org.spongycastle.openpgp.PGPKeyRingGenerator;
import org.spongycastle.openpgp.PGPPrivateKey;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.spongycastle.openpgp.PGPSignature;
import org.spongycastle.openpgp.operator.PGPDigestCalculator;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPKeyConverter;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPKeyPair;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;


/** Personal asymmetric encryption key. */
public class PersonalKey implements Parcelable {

    private static final String SECRET_KEY_FILE = "privatekey.pgp";
    private static final String PUBLIC_KEY_FILE = "publickey.pgp";

    private final PGPKeyPair mSignKp;
    private final PGPKeyPair mEncryptKp;

    private PersonalKey(PGPKeyPair signKp, PGPKeyPair encryptKp) {
        mSignKp = signKp;
        mEncryptKp = encryptKp;
    }

    private PersonalKey(Parcel in) throws PGPException {
        JcaPGPKeyConverter conv = new JcaPGPKeyConverter().setProvider("SC");

        // TODO read byte data
        PrivateKey privSign = (PrivateKey) in.readSerializable();
        PublicKey pubSign = (PublicKey) in.readSerializable();
        int algoSign = in.readInt();
        Date dateSign = new Date(in.readLong());

        PGPPublicKey pubKeySign = conv.getPGPPublicKey(algoSign, pubSign, dateSign);
        PGPPrivateKey privKeySign = conv.getPGPPrivateKey(pubKeySign, privSign);
        mSignKp = new PGPKeyPair(pubKeySign, privKeySign);

        PrivateKey privEnc = (PrivateKey) in.readSerializable();
        PublicKey pubEnc = (PublicKey) in.readSerializable();
        int algoEnc = in.readInt();
        Date dateEnc = new Date(in.readLong());

        PGPPublicKey pubKeyEnc = conv.getPGPPublicKey(algoEnc, pubEnc, dateEnc);
        PGPPrivateKey privKeyEnc = conv.getPGPPrivateKey(pubKeyEnc, privEnc);
        mEncryptKp = new PGPKeyPair(pubKeyEnc, privKeyEnc);
    }

    /**
     * TODO
     * @return the public keyring.
     */
    public PGPPublicKeyRing store(Context context, String name, String email, String comment, String passphrase) throws PGPException, IOException {
        StringBuilder userid = new StringBuilder(name)
            .append('<');
        if (email != null)
            userid.append(email);
        userid.append('>');
        if (comment != null)
            userid.append(" (")
            .append(comment)
            .append(')');

        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        PGPKeyRingGenerator keyRingGen = new PGPKeyRingGenerator(PGPSignature.POSITIVE_CERTIFICATION, mSignKp,
            userid.toString(), sha1Calc, null, null,
            new JcaPGPContentSignerBuilder(mSignKp.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA1),
            new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha1Calc).setProvider("BC").build(passphrase.toCharArray()));

        keyRingGen.addSubKey(mEncryptKp);

        File keyfile;
        OutputStream out;

        keyfile = new File(context.getFilesDir(), SECRET_KEY_FILE);
        out = new ArmoredOutputStream(new FileOutputStream(keyfile));
        keyRingGen.generateSecretKeyRing().encode(out);
        out.close();

        keyfile = new File(context.getFilesDir(), PUBLIC_KEY_FILE);
        out = new ArmoredOutputStream(new FileOutputStream(keyfile));
        PGPPublicKeyRing pubRing = keyRingGen.generatePublicKeyRing();
        pubRing.encode(out);
        out.close();

        return pubRing;
    }

    public static PersonalKey load(Context context) {
        // TODO
        return null;
    }

    public static PersonalKey create(Context context, int keysize) throws IOException {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("ElGamal", "SC");
            gen.initialize(keysize, new SecureRandom());

            PGPKeyPair encryptKp = new JcaPGPKeyPair(PGPPublicKey.ELGAMAL_ENCRYPT, gen.generateKeyPair(), new Date());

            gen = KeyPairGenerator.getInstance("DSA", "SC");
            PGPKeyPair signKp = new JcaPGPKeyPair(PGPPublicKey.DSA, gen.generateKeyPair(), new Date());

            return new PersonalKey(signKp, encryptKp);
        }
        catch (Exception e) {
            IOException io = new IOException("unable to generate keypair");
            io.initCause(e);
            throw io;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        // TODO write byte arrays
        try {
            PrivateKey privSign = mSignKp.getPrivateKey().getKey();
            PublicKey pubSign = mSignKp.getPublicKey().getKey("SC");
            int algoSign = mSignKp.getPrivateKey().getPublicKeyPacket().getAlgorithm();
            Date dateSign = mSignKp.getPrivateKey().getPublicKeyPacket().getTime();

            PrivateKey privEnc = mEncryptKp.getPrivateKey().getKey();
            PublicKey pubEnc = mEncryptKp.getPublicKey().getKey("SC");
            int algoEnc = mEncryptKp.getPrivateKey().getPublicKeyPacket().getAlgorithm();
            Date dateEnc = mEncryptKp.getPrivateKey().getPublicKeyPacket().getTime();

            dest.writeSerializable(privSign);
            dest.writeSerializable(pubSign);
            dest.writeInt(algoSign);
            dest.writeLong(dateSign.getTime());

            dest.writeSerializable(privEnc);
            dest.writeSerializable(pubEnc);
            dest.writeInt(algoEnc);
            dest.writeLong(dateEnc.getTime());
        }
        catch (Exception e) {
            Log.e("PersonalKey", "error writing key to parcel", e);
        }
    }

    public static final Parcelable.Creator<PersonalKey> CREATOR =
            new Parcelable.Creator<PersonalKey>() {
        public PersonalKey createFromParcel(Parcel source) {
            try {
                return new PersonalKey(source);
            }
            catch (PGPException e) {
                return null;
            }
        }

        @Override
        public PersonalKey[] newArray(int size) {
            return new PersonalKey[size];
        };
    };

}

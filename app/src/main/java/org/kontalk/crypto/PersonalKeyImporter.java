package org.kontalk.crypto;


import android.os.Environment;

import org.kontalk.crypto.PGP.PGPKeyPairRing;
import org.kontalk.util.MessageUtils;
import org.spongycastle.bcpg.ArmoredInputStream;
import org.spongycastle.openpgp.PGPException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * Importer for a personal key pack.
 * @author Daniele Ricci
 */
public class PersonalKeyImporter {

    private static final long MAX_KEY_SIZE = 102400; // 100 KB

    public static final String PUBLIC_KEY_FILENAME = "kontalk-public.asc";
    public static final String PRIVATE_KEY_FILENAME = "kontalk-private.asc";
    public static final String BRIDGE_CERT_FILENAME = "kontalk-login.crt";
    public static final String BRIDGE_KEY_FILENAME = "kontalk-login.key";
    public static final String BRIDGE_CERTPACK_FILENAME = "kontalk-login.p12";

    public static final String KEYPACK_FILENAME = "kontalk-keys.zip";

    public static final File DEFAULT_KEYPACK = new File(Environment
            .getExternalStorageDirectory(), KEYPACK_FILENAME);

    private ZipInputStream mKeyPack;
    private String mPassphrase;

    private ByteArrayOutputStream mPublicKey;
    private ByteArrayOutputStream mPrivateKey;

    public PersonalKeyImporter(ZipInputStream keypack, String passphrase) {
        mKeyPack = keypack;
        mPassphrase = passphrase;
    }

    public void load() throws IOException {
        ByteArrayOutputStream publicKey = null, privateKey = null;

        ZipEntry entry;
        while ((entry = mKeyPack.getNextEntry()) != null) {

            // PGP public key
            if (PUBLIC_KEY_FILENAME.equals(entry.getName())) {
                // I don't really know if this is good...
                publicKey = MessageUtils.readFully(mKeyPack, MAX_KEY_SIZE);
            }

            // PGP private key
            else if (PRIVATE_KEY_FILENAME.equals(entry.getName())) {
                // I don't really know if this is good...
                privateKey = MessageUtils.readFully(mKeyPack, MAX_KEY_SIZE);
            }

        }

        if (privateKey == null || publicKey == null)
            throw new IOException("invalid data");

        mPrivateKey = privateKey;
        mPublicKey = publicKey;
    }

    /** Releases all resources of imported data. */
    public void close() throws IOException {
        if (mPrivateKey != null) mPrivateKey.close();
        if (mPublicKey != null) mPublicKey.close();
    }

    /** Creates a {@link PersonalKey} out of the imported data, if possible. */
    public PersonalKey createPersonalKey() throws PGPException, NoSuchProviderException,
            CertificateException, IOException {
        if (mPrivateKey != null && mPublicKey != null)
            return PersonalKey.load(
                new ArmoredInputStream(new ByteArrayInputStream(mPrivateKey.toByteArray())),
                new ArmoredInputStream(new ByteArrayInputStream(mPublicKey.toByteArray())),
                mPassphrase, null);
        return null;
    }

    public PGPKeyPairRing createKeyPairRing() throws PGPException, NoSuchProviderException,
            CertificateException, IOException {
        if (mPrivateKey != null && mPublicKey != null)
            return PersonalKey.test(
                new ArmoredInputStream(new ByteArrayInputStream(mPrivateKey.toByteArray())),
                new ArmoredInputStream(new ByteArrayInputStream(mPublicKey.toByteArray())),
                mPassphrase, null);
        return null;
    }

    public byte[] getPrivateKeyData() {
        return mPrivateKey != null ? mPrivateKey.toByteArray() : null;
    }

    public byte[] getPublicKeyData() {
        return mPublicKey != null ? mPublicKey.toByteArray() : null;
    }

}

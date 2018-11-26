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

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.spongycastle.bcpg.ArmoredInputStream;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.operator.OperatorCreationException;

import org.kontalk.crypto.PGP.PGPKeyPairRing;
import org.kontalk.provider.Keyring;
import org.kontalk.util.ByteArrayInOutStream;
import org.kontalk.util.MessageUtils;


/**
 * Importer for a personal key pack.
 * @author Daniele Ricci
 */
public class PersonalKeyImporter implements PersonalKeyPack {

    private static final long MAX_KEY_SIZE = 102400; // 100 KB

    private ZipInputStream mKeyPack;
    private String mPassphrase;

    private ByteArrayInOutStream mPublicKey;
    private ByteArrayInOutStream mPrivateKey;
    private ByteArrayInOutStream mTrustedKeys;
    private ByteArrayInOutStream mAccountInfo;

    public PersonalKeyImporter(ZipInputStream keypack, String passphrase) {
        mKeyPack = keypack;
        mPassphrase = passphrase;
    }

    public void load() throws IOException {
        ByteArrayInOutStream publicKey = null, privateKey = null,
            trustedKeys = null, accountInfo = null;
        IOException zipException = null;

        ZipEntry entry;
        try {
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

                // trusted keys
                else if (TRUSTED_KEYS_FILENAME.equals(entry.getName())) {
                    // I don't really know if this is good...
                    trustedKeys = MessageUtils.readFully(mKeyPack, MAX_KEY_SIZE);
                }

                // account info
                else if (ACCOUNT_INFO_FILENAME.equals(entry.getName())) {
                    // I don't really know if this is good...
                    accountInfo = MessageUtils.readFully(mKeyPack, MAX_KEY_SIZE);
                }

            }
        }
        catch (IOException e) {
            // this is to workaround any problem
            // this exception will be logged if data is corrupted or not present
            zipException = e;
        }

        if (privateKey == null || publicKey == null) {
            throw new IOException("invalid data", zipException);
        }

        mPrivateKey = privateKey;
        mPublicKey = publicKey;
        mTrustedKeys = trustedKeys;
        mAccountInfo = accountInfo;
    }

    /** Releases all resources of imported data. */
    public void close() throws IOException {
        if (mPrivateKey != null) mPrivateKey.close();
        if (mPublicKey != null) mPublicKey.close();
    }

    /** Creates a {@link PersonalKey} out of the imported data, if possible. */
    public PersonalKey createPersonalKey() throws PGPException, NoSuchProviderException,
            CertificateException, IOException, OperatorCreationException, NoSuchAlgorithmException,
            InvalidKeyException, SignatureException {
        if (mPrivateKey != null && mPublicKey != null) {
            return importPersonalKey(mPrivateKey.getInputStream(),
                mPublicKey.getInputStream(), mPassphrase);
        }
        return null;
    }

    public PGPKeyPairRing createKeyPairRing() throws PGPException, NoSuchProviderException,
            CertificateException, IOException {
        if (mPrivateKey != null && mPublicKey != null)
            return PersonalKey.test(
                new ArmoredInputStream(mPrivateKey.getInputStream()),
                new ArmoredInputStream(mPublicKey.getInputStream()),
                mPassphrase, null);
        return null;
    }

    public byte[] getPrivateKeyData() {
        return mPrivateKey != null ? mPrivateKey.toByteArray() : null;
    }

    public byte[] getPublicKeyData() {
        return mPublicKey != null ? mPublicKey.toByteArray() : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Keyring.TrustedFingerprint> getTrustedKeys() throws IOException {
        if (mTrustedKeys != null) {
            Properties prop = new Properties();
            prop.load(mTrustedKeys.getInputStream());

            try {
                return Keyring.fromTrustedFingerprintMap((Map) prop);
            }
            catch (Exception ex) {
                throw new IOException("invalid trusted keys file", ex);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getAccountInfo() throws IOException {
        if (mAccountInfo != null) {
            Properties prop = new Properties();
            prop.load(mAccountInfo.getInputStream());
            return new HashMap<>((Map) prop);
        }

        return null;
    }

    public static PersonalKey importPersonalKey(byte[] privateKeyData, byte[] publicKeyData, String passphrase)
            throws PGPException, IOException, CertificateException, NoSuchAlgorithmException,
            OperatorCreationException, SignatureException, NoSuchProviderException, InvalidKeyException {
        PGP.PGPKeyPairRing ring;
        try {
            ring = PGP.PGPKeyPairRing.loadArmored(privateKeyData, publicKeyData);
        }
        catch (IOException e) {
            // try not armored
            ring = PGP.PGPKeyPairRing.load(privateKeyData, publicKeyData);
        }

        // bridge certificate for connection
        X509Certificate bridgeCert = X509Bridge.createCertificate(ring.publicKey,
            ring.secretKey.getSecretKey(), passphrase);

        return PersonalKey.load(ring.secretKey, ring.publicKey,
            passphrase, bridgeCert);
    }

    public static PersonalKey importPersonalKey(InputStream privateKeyData, InputStream publicKeyData, String passphrase)
        throws PGPException, IOException, CertificateException, NoSuchAlgorithmException,
        OperatorCreationException, SignatureException, NoSuchProviderException, InvalidKeyException {
        PGP.PGPKeyPairRing ring;
        try {
            ring = PGP.PGPKeyPairRing.loadArmored(privateKeyData, publicKeyData);
        }
        catch (IOException e) {
            // try not armored
            ring = PGP.PGPKeyPairRing.load(privateKeyData, publicKeyData);
        }

        return importPersonalKey(ring, passphrase);
    }

    private static PersonalKey importPersonalKey(PGP.PGPKeyPairRing ring, String passphrase)
            throws CertificateException, NoSuchAlgorithmException, IOException,
            NoSuchProviderException, SignatureException, PGPException, InvalidKeyException, OperatorCreationException {
        // bridge certificate for connection
        X509Certificate bridgeCert = X509Bridge.createCertificate(ring.publicKey,
            ring.secretKey.getSecretKey(), passphrase);

        return PersonalKey.load(ring.secretKey, ring.publicKey,
            passphrase, bridgeCert);
    }

}

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.spongycastle.bcpg.ArmoredOutputStream;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemWriter;

import org.kontalk.provider.Keyring;


/**
 * Exporter for a personal key.
 * @author Daniele Ricci
 */
public class PersonalKeyExporter implements PersonalKeyPack {

    public void save(byte[] privateKey, byte[] publicKey, OutputStream dest, String passphrase, String exportPassphrase, byte[] bridgeCert,
                     Map<String, Keyring.TrustedFingerprint> trustedKeys, String phoneNumber)
        throws PGPException, IOException, CertificateException, KeyStoreException, NoSuchAlgorithmException {

        // put everything in a zip file
        ZipOutputStream zip = new ZipOutputStream(dest);

        // custom export passphrase -- re-encrypt private key
        if (exportPassphrase != null) {
            privateKey = PGP.copySecretKeyRingWithNewPassword(privateKey,
                passphrase, exportPassphrase)
                .getEncoded();
        }
        else {
            // use provided passphrase for export
            exportPassphrase = passphrase;
        }

        OutputStream out;
        ByteArrayOutputStream stream;

        if (bridgeCert != null) {
            // export bridge certificate
            zip.putNextEntry(new ZipEntry(BRIDGE_CERT_FILENAME));
            stream = new ByteArrayOutputStream();
            PemWriter writer = new PemWriter(new OutputStreamWriter(stream));
            writer.writeObject(new PemObject(X509Bridge.PEM_TYPE_CERTIFICATE, bridgeCert));
            writer.close();
            stream.writeTo(zip);
            zip.closeEntry();

            // export bridge private key
            zip.putNextEntry(new ZipEntry(BRIDGE_KEY_FILENAME));
            PrivateKey bridgeKey = PGP.convertPrivateKey(privateKey, exportPassphrase);
            stream = new ByteArrayOutputStream();
            writer = new PemWriter(new OutputStreamWriter(stream));
            writer.writeObject(new PemObject(X509Bridge.PEM_TYPE_PRIVATE_KEY, bridgeKey.getEncoded()));
            writer.close();
            stream.writeTo(zip);
            zip.closeEntry();

            // certificate pack in PKCS#12
            zip.putNextEntry(new ZipEntry(BRIDGE_CERTPACK_FILENAME));
            X509Certificate certificate = X509Bridge.load(bridgeCert);
            KeyStore pkcs12 = X509Bridge.exportCertificate(certificate, bridgeKey);
            pkcs12.store(zip, exportPassphrase.toCharArray());
            zip.closeEntry();
        }

        // export public key
        zip.putNextEntry(new ZipEntry(PUBLIC_KEY_FILENAME));
        stream = new ByteArrayOutputStream();
        out = new ArmoredOutputStream(stream);
        out.write(publicKey);
        out.close();
        stream.writeTo(zip);
        zip.closeEntry();

        // export private key
        zip.putNextEntry(new ZipEntry(PRIVATE_KEY_FILENAME));
        stream = new ByteArrayOutputStream();
        out = new ArmoredOutputStream(stream);
        out.write(privateKey);
        out.close();
        stream.writeTo(zip);
        zip.closeEntry();

        if (trustedKeys != null) {
            // export trusted keys
            Properties prop = new Properties();
            for (Map.Entry<String, Keyring.TrustedFingerprint> tk : trustedKeys.entrySet())
                prop.put(tk.getKey(), tk.getValue().toString());
            zip.putNextEntry(new ZipEntry(TRUSTED_KEYS_FILENAME));
            prop.store(zip, null);
            zip.closeEntry();
        }

        // export account info
        Properties info = new Properties();
        info.setProperty("phoneNumber", phoneNumber);
        zip.putNextEntry(new ZipEntry(ACCOUNT_INFO_FILENAME));
        info.store(zip, null);
        zip.closeEntry();

        // finalize the zip file
        zip.close();
    }
}

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
package org.kontalk.service.msgcenter;

import android.widget.Toast;

import org.jivesoftware.smack.packet.Packet;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.util.MessageUtils;
import org.spongycastle.bcpg.ArmoredInputStream;
import org.spongycastle.jcajce.provider.asymmetric.X509;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.kontalk.authenticator.Authenticator.BRIDGE_CERT_FILENAME;
import static org.kontalk.authenticator.Authenticator.PRIVATE_KEY_FILENAME;
import static org.kontalk.authenticator.Authenticator.PUBLIC_KEY_FILENAME;


/** Listener and manager for a key pair import cycle. */
class ImportKeyPairListener extends RegisterKeyPairListener {

    private static final long MAX_KEY_SIZE = 102400; // 100 KB

    private ZipInputStream mKeyPack;

    public ImportKeyPairListener(MessageCenterService instance,
            ZipInputStream keypack, String passphrase) {
        super(instance, passphrase);

        mKeyPack = keypack;
    }

    public void run() throws CertificateException, SignatureException,
            PGPException, IOException, NoSuchProviderException {
        super.run();

        ArmoredInputStream publicKey = null, privateKey = null;
        ByteArrayInputStream bridgeCert = null;

        ZipEntry entry;
        while ((entry = mKeyPack.getNextEntry()) != null) {

            // PGP public key
            if (PUBLIC_KEY_FILENAME.equals(entry.getName())) {
                // I don't really know if this is good...
                byte[] publicKeyData = MessageUtils.readFully(mKeyPack, MAX_KEY_SIZE)
                    .toByteArray();
                publicKey = new ArmoredInputStream(new ByteArrayInputStream(publicKeyData));
            }

            // PGP private key
            else if (PRIVATE_KEY_FILENAME.equals(entry.getName())) {
                // I don't really know if this is good...
                byte[] privateKeyData = MessageUtils.readFully(mKeyPack, MAX_KEY_SIZE)
                        .toByteArray();
                privateKey = new ArmoredInputStream(new ByteArrayInputStream(privateKeyData));
            }

            // X.509 bridge certificate
            else if (BRIDGE_CERT_FILENAME.equals(entry.getName())) {
                byte[] bridgeCertData = MessageUtils.readFully(mKeyPack, MAX_KEY_SIZE)
                    .toByteArray();

                PemReader reader = new PemReader(new InputStreamReader(new ByteArrayInputStream(bridgeCertData)));
                PemObject object = reader.readPemObject();
                if (object != null && X509Bridge.PEM_TYPE_CERTIFICATE.equals(object.getType())) {
                    bridgeCert = new ByteArrayInputStream(object.getContent());
                }

                reader.close();
            }

        }

        if (privateKey == null || publicKey == null || bridgeCert == null)
            throw new IOException("invalid data");

        // try to load a PersonalKey out of the given data
        mKeyRing = PersonalKey
            .test(privateKey, publicKey, mPassphrase, bridgeCert);

        // if we are here, it means personal key is likely valid
        // proceed to send the public key to the server for approval

        // listen for connection events
        setupConnectedReceiver();
        // request connection status
        MessageCenterService.requestConnectionStatus(getContext());

        // CONNECTED listener will do the rest
    }

    public void abort() {
        super.abort();
    }

    // TODO

    @Override
    public void processPacket(Packet packet) {
        super.processPacket(packet);

        // we are done here
        endKeyPairImport();
    }

    @Override
    protected void finish() {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplication(),
                    R.string.msg_import_keypair_complete,
                    Toast.LENGTH_LONG).show();
            }
        });
    }

}

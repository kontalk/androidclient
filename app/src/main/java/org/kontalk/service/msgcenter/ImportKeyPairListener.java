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

package org.kontalk.service.msgcenter;

import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.crypto.PersonalKeyImporter;

import org.jivesoftware.smack.packet.Stanza;
import org.bouncycastle.openpgp.PGPException;

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.zip.ZipInputStream;


/** Listener and manager for a key pair import cycle. */
class ImportKeyPairListener extends RegisterKeyPairListener {

    private PersonalKeyImporter mImporter;

    public ImportKeyPairListener(MessageCenterService instance,
            ZipInputStream keypack, String passphrase) {
        super(instance, passphrase);

        mImporter = new PersonalKeyImporter(keypack, passphrase);
    }

    public void run() throws CertificateException, SignatureException,
            PGPException, IOException, NoSuchProviderException {
        super.run();

        try {
            mImporter.load();
            mKeyRing = mImporter.createKeyPairRing();
        }

        finally {
            try {
                mImporter.close();
            }
            catch (Exception e) {
                // ignored
            }
        }

        // if we are here, it means personal key is likely valid
        // proceed to send the public key to the server for approval

        // listen for connection events
        registerConnectionEvents();

        // CONNECTED listener will do the rest
    }

    public void abort() {
        super.abort();
    }

    // TODO

    @Override
    public void processStanza(Stanza packet) {
        super.processStanza(packet);

        // we are done here
        endKeyPairImport();
    }

    @Override
    protected void finish() {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(Kontalk.get(),
                    R.string.msg_import_keypair_complete,
                    Toast.LENGTH_LONG).show();
            }
        });
    }

}

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

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;

import org.jivesoftware.smack.packet.Stanza;
import org.kontalk.Kontalk;
import org.kontalk.authenticator.MyAccount;
import org.kontalk.util.XMPPUtils;
import org.bouncycastle.openpgp.PGPException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.service.KeyPairGeneratorService.KeyGeneratorReceiver;
import org.kontalk.service.KeyPairGeneratorService.PersonalKeyRunnable;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_REGENERATE_KEYPAIR;


/** Listener and manager for a key pair regeneration cycle. */
class RegenerateKeyPairListener extends RegisterKeyPairListener {
    private BroadcastReceiver mKeyReceiver;

    public RegenerateKeyPairListener(MessageCenterService instance, String passphrase) {
        super(instance, passphrase);
        if (passphrase == null)
            mPassphrase = Kontalk.get().getDefaultAccount().getPassphrase();
    }

    public void run() throws CertificateException, SignatureException,
            PGPException, IOException, NoSuchProviderException {
        // not calling super
        revokeCurrentKey();

        configure();
        setupKeyPairReceiver();

        // begin key pair generation
        generateKeyPair();
    }

    public void abort() {
        super.abort();

        if (mKeyReceiver != null) {
            unregisterReceiver(mKeyReceiver);
            mKeyReceiver = null;
        }
    }

    private void generateKeyPair() {
        Context ctx = getContext();
        if (ctx != null) {
            Intent i = new Intent(ctx, KeyPairGeneratorService.class);
            i.setAction(KeyPairGeneratorService.ACTION_GENERATE);
            i.putExtra(KeyPairGeneratorService.EXTRA_FOREGROUND, true);
            ctx.startService(i);
        }
    }

    private void setupKeyPairReceiver() {
        if (mKeyReceiver == null) {

            PersonalKeyRunnable action = new PersonalKeyRunnable() {
                public void run(PersonalKey key) {
                    Log.d(MessageCenterService.TAG, "keypair generation complete.");
                    // unregister the broadcast receiver
                    unregisterReceiver(mKeyReceiver);
                    mKeyReceiver = null;

                    // store the key
                    try {
                        MyAccount account = Kontalk.get().getDefaultAccount();

                        String userId = XMPPUtils.createLocalpart(account.getName());
                        mKeyRing = key.storeNetwork(userId, getServer().getNetwork(), account.getDisplayName(),
                            // TODO should we ask passphrase to the user?
                            account.getPassphrase());

                        // listen for connection events
                        registerConnectionEvents();

                        // CONNECTED listener will do the rest
                    }
                    catch (Exception e) {
                        // TODO notify user
                        Log.v(MessageCenterService.TAG, "error saving key", e);
                    }
                }
            };

            mKeyReceiver = new KeyGeneratorReceiver(getIdleHandler(), action);

            IntentFilter filter = new IntentFilter(KeyPairGeneratorService.ACTION_GENERATE);
            registerReceiver(mKeyReceiver, filter);
        }
    }

    @Override
    public void processStanza(Stanza packet) {
        super.processStanza(packet);

        // we are done here
        endKeyPairRegeneration();
    }

    @Override
    protected void finish() {
        sendBroadcast(new Intent(ACTION_REGENERATE_KEYPAIR));
        runOnUiThread(new Runnable() {
            public void run() {
                Context context = getContext();
                if (context != null) {
                    Toast.makeText(context.getApplicationContext(),
                        R.string.msg_gen_keypair_complete,
                        Toast.LENGTH_LONG).show();
                }
            }
        });
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.provider;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PGPCoder;
import org.kontalk.crypto.PersonalKey;


/**
 * Keyring management.
 * @author Daniele Ricci
 */
public class Keyring {

    private Keyring() {
    }

    /** Returns a {@link Coder} instance for encrypting data. */
    public static Coder getEncryptCoder(Context context, EndpointServer server, PersonalKey key, String[] recipients) {
        // get recipients public keys from users database
        PGPPublicKeyRing keys[] = new PGPPublicKeyRing[recipients.length];
        for (int i = 0; i < recipients.length; i++) {
            PGPPublicKeyRing ring = getPublicKey(context, recipients[i], MyUsers.Keys.TRUST_IGNORED);
            if (ring == null)
                throw new IllegalArgumentException("public key not found for user " + recipients[i]);

            keys[i] = ring;
        }

        return new PGPCoder(server, key, keys);
    }

    /** Returns a {@link Coder} instance for decrypting data. */
    public static Coder getDecryptCoder(Context context, EndpointServer server, PersonalKey key, String sender) {
        PGPPublicKeyRing senderKey = getPublicKey(context, sender, MyUsers.Keys.TRUST_IGNORED);
        return new PGPCoder(server, key, senderKey);
    }

    /** Returns a {@link Coder} instance for verifying data. */
    public static Coder getVerifyCoder(Context context, EndpointServer server, String sender) {
        PGPPublicKeyRing senderKey = getPublicKey(context, sender, MyUsers.Keys.TRUST_UNKNOWN);
        return new PGPCoder(server, null, senderKey);
    }

    /** Adds/updates a public key. */
    public static void setKey(Context context, String jid, byte[] keydata)
        throws IOException, PGPException {
        setKey(context, jid, keydata, -1);
    }

    /** Adds/updates a public key. */
    public static void setKey(Context context, String jid, byte[] keydata, int trustLevel)
            throws IOException, PGPException {
        PGPPublicKey pk = PGP.getMasterKey(keydata);
        String fingerprint = PGP.getFingerprint(pk);
        Date date = pk.getCreationTime();
        ContentValues values = new ContentValues(3);
        values.put(MyUsers.Keys.PUBLIC_KEY, keydata);
        values.put(MyUsers.Keys.TIMESTAMP, date.getTime());
        if (trustLevel >= 0)
            values.put(MyUsers.Keys.TRUST_LEVEL, trustLevel);
        context.getContentResolver().insert(MyUsers.Keys.getUri(jid, fingerprint), values);
    }

    /** Updates the fingerprint and the date (for fingerprint in presence). */
    public static void setKey(Context context, String jid, String fingerprint, Date date) {
        setKey(context, jid, fingerprint, date, -1);
    }

    /** Updates the fingerprint and the date (for fingerprint in presence). */
    public static void setKey(Context context, String jid, String fingerprint, Date date, int trustLevel) {
        ContentValues values = new ContentValues(2);
        values.put(MyUsers.Keys.TIMESTAMP, date.getTime());
        if (trustLevel >= 0)
            values.put(MyUsers.Keys.TRUST_LEVEL, trustLevel);
        context.getContentResolver().insert(MyUsers.Keys.getUri(jid, fingerprint), values);
    }

    /** Sets the trust level for the given key. */
    public static void setTrustLevel(Context context, String jid, String fingerprint, int trustLevel) {
        ContentValues values = new ContentValues(1);
        values.put(MyUsers.Keys.TRUST_LEVEL, trustLevel);
        context.getContentResolver().insert(MyUsers.Keys.getUri(jid, fingerprint), values);
    }

    /**
     * Retrieves the latest fingerprint with the minimum given trust level.
     * @param trustLevel the minimum trust level to consider
     */
    public static String getFingerprint(Context context, String jid, int trustLevel) {
        String fingerprint = null;

        Cursor c = queryLatestWithMinimumTrustLevel(context, jid, trustLevel, MyUsers.Keys.FINGERPRINT);
        if (c.moveToFirst())
            fingerprint = c.getString(0);
        c.close();

        return fingerprint;
    }

    /**
     * Retrieves the latest public key with the minimum given trust level.
     * @param trustLevel the minimum trust level to consider
     */
    public static PGPPublicKeyRing getPublicKey(Context context, String jid, int trustLevel) {
        TrustedPublicKeyData key = getPublicKeyData(context, jid, trustLevel);

        try {
            return PGP.readPublicKeyring(key.keyData);
        }
        catch (Exception e) {
            // ignored
        }

        return null;
    }

    /**
     * Retrieves the latest public key with the minimum given trust level.
     * @param trustLevel the minimum trust level to consider
     */
    public static TrustedPublicKeyData getPublicKeyData(Context context, String jid, int trustLevel) {
        TrustedPublicKeyData data = null;

        Cursor c = queryLatestWithMinimumTrustLevel(context, jid, trustLevel,
            MyUsers.Keys.PUBLIC_KEY, MyUsers.Keys.TRUST_LEVEL);
        if (c.moveToFirst()) {
            byte[] keydata = c.getBlob(0);
            int trustStatus = c.getInt(1);
            data = new TrustedPublicKeyData(keydata, trustStatus);
        }

        c.close();

        return data;
    }

    private static Cursor queryLatestWithMinimumTrustLevel(Context context, String jid, int trustLevel, String... columns) {
        return context.getContentResolver().query(MyUsers.Keys.getUri(jid), columns,
            MyUsers.Keys.TRUST_LEVEL + " >= " + trustLevel,
            null, MyUsers.Keys.TIMESTAMP + " DESC");
    }

    /** Returns a JID-fingerprint map of trusted keys. */
    public static Map<String, TrustedFingerprint> getTrustedKeys(Context context) {
        Cursor c = context.getContentResolver().query(MyUsers.Keys.CONTENT_URI, new String[] {
            MyUsers.Keys.JID,
            MyUsers.Keys.FINGERPRINT,
            MyUsers.Keys.TRUST_LEVEL,
        }, null, null, null);

        Map<String, TrustedFingerprint> list = new HashMap<>(c.getCount());
        while (c.moveToNext()) {
            list.put(c.getString(0), new TrustedFingerprint(c.getString(1), c.getInt(3)));
        }

        c.close();
        return list;
    }

    public static final class TrustedFingerprint {
        public final String fingerprint;
        public final int trustLevel;

        private TrustedFingerprint(String fingerprint, int trustLevel) {
            this.fingerprint = fingerprint;
            this.trustLevel = trustLevel;
        }

        @Override
        public String toString() {
            return fingerprint + "|" + trustLevel;
        }
    }

    public static final class TrustedPublicKeyData {
        public final byte[] keyData;
        public final int trustLevel;

        private TrustedPublicKeyData(byte[] keyData, int trustLevel) {
            this.keyData = keyData;
            this.trustLevel = trustLevel;
        }
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.Iterator;
import java.util.Map;

import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;

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

    /**
     * Special value used in the fingerprint column so the first key that comes
     * in is automatically trusted.
     */
    @VisibleForTesting
    static final String VALUE_AUTOTRUST = "<autotrust>";

    private Keyring() {
    }

    /** Returns a {@link Coder} instance for encrypting data. */
    public static Coder getEncryptCoder(Context context, EndpointServer server, PersonalKey key, String[] recipients) {
        // get recipients public keys from users database
        PGPPublicKeyRing keys[] = new PGPPublicKeyRing[recipients.length];
        for (int i = 0; i < recipients.length; i++) {
            PGPPublicKeyRing ring = getPublicKey(context, recipients[i], MyUsers.Keys.TRUST_UNKNOWN);
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

        int autoTrustedLevel = getAutoTrustedLevel(context, jid);

        ContentValues values = new ContentValues(3);
        values.put(MyUsers.Keys.PUBLIC_KEY, keydata);
        values.put(MyUsers.Keys.TIMESTAMP, date.getTime());
        if (trustLevel >= 0) {
            values.put(MyUsers.Keys.TRUST_LEVEL, trustLevel);
        }
        else if (autoTrustedLevel >= 0) {
            values.put(MyUsers.Keys.TRUST_LEVEL, autoTrustedLevel);
        }
        context.getContentResolver().insert(MyUsers.Keys.getUri(jid, fingerprint), values);

        if (autoTrustedLevel >= 0) {
            // delete the autotrust entry
            context.getContentResolver().delete(MyUsers.Keys.getUri(jid, VALUE_AUTOTRUST), null, null);
        }
    }

    /** Updates the fingerprint and the date (for fingerprint in presence). */
    public static void setKey(Context context, String jid, String fingerprint, Date date) {
        if (fingerprint == null)
            throw new NullPointerException("fingerprint");

        setKey(context, jid, fingerprint, date, -1);
    }

    /** Updates the fingerprint and the date (for fingerprint in presence). */
    public static void setKey(Context context, String jid, String fingerprint, Date date, int trustLevel) {
        if (fingerprint == null)
            throw new NullPointerException("fingerprint");

        ContentValues values = new ContentValues(2);
        values.put(MyUsers.Keys.TIMESTAMP, date.getTime());
        if (trustLevel >= 0)
            values.put(MyUsers.Keys.TRUST_LEVEL, trustLevel);
        context.getContentResolver().insert(MyUsers.Keys.getUri(jid, fingerprint)
            // since we are handling data from a presence, insert only if it doesn't exist
            .buildUpon().appendQueryParameter(MyUsers.Keys.INSERT_ONLY, "true").build(), values);
    }

    /** Sets the trust level for the given key. */
    public static void setTrustLevel(Context context, String jid, String fingerprint, int trustLevel) {
        if (fingerprint == null)
            throw new NullPointerException("fingerprint");

        ContentValues values = new ContentValues(2);
        values.put(MyUsers.Keys.TRUST_LEVEL, trustLevel);
        values.put(MyUsers.Keys.MANUAL_TRUST, true);
        context.getContentResolver().insert(MyUsers.Keys.getUri(jid, fingerprint), values);
    }

    public static void setAutoTrustLevel(Context context, String jid, int trustLevel) {
        ContentValues values = new ContentValues(1);
        values.put(MyUsers.Keys.TRUST_LEVEL, trustLevel);
        context.getContentResolver().insert(MyUsers.Keys.getUri(jid, VALUE_AUTOTRUST), values);
    }

    /**
     * Retrieves the latest fingerprint with the minimum given trust level.
     * @param trustLevel the minimum trust level to consider
     */
    public static String getFingerprint(Context context, String jid, int trustLevel) {
        String fingerprint = null;

        Cursor c = queryLatestWithMinimumTrustLevel(context, jid, trustLevel, MyUsers.Keys.FINGERPRINT);
        if (c.moveToFirst() && !c.isNull(0))
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
            MyUsers.Keys.PUBLIC_KEY, MyUsers.Keys.TRUST_LEVEL, MyUsers.Keys.MANUAL_TRUST);
        if (c.moveToFirst()) {
            byte[] keydata = c.getBlob(0);
            int trustStatus = c.getInt(1);
            boolean manualTrust = c.getInt(2) != 0;
            if (keydata != null)
                data = new TrustedPublicKeyData(keydata, trustStatus, manualTrust);
        }

        c.close();

        return data;
    }

    /**
     * Returns true if the contact is in advanced mode, i.e. the user has
     * manually verified one of the user's key.
     * This is called <b>advanced mode</b> and will enable stricter key checks.
     */
    public static boolean isAdvancedMode(Context context, String jid) {
        boolean result;
        Cursor c = context.getContentResolver().query(MyUsers.Keys.getUri(jid),
            new String[] { MyUsers.Keys.TRUST_LEVEL } ,
            MyUsers.Keys.FINGERPRINT + " <> ? AND " +
            MyUsers.Keys.MANUAL_TRUST + " <> 0",
            new String[] { VALUE_AUTOTRUST },
            null);
        result = c.moveToFirst();
        c.close();
        return result;
    }

    private static Cursor queryLatestWithMinimumTrustLevel(Context context, String jid, int trustLevel, String... columns) {
        return context.getContentResolver().query(MyUsers.Keys.getUri(jid), columns,
            MyUsers.Keys.TRUST_LEVEL + " >= " + trustLevel + " AND "+
            MyUsers.Keys.FINGERPRINT + " <> ?",
            new String[] { VALUE_AUTOTRUST }, MyUsers.Keys.TIMESTAMP + " DESC");
    }

    private static int getAutoTrustedLevel(Context context, String jid) {
        int result = -1;
        Cursor c = context.getContentResolver().query(MyUsers.Keys.getUri(jid),
            new String[] { MyUsers.Keys.TRUST_LEVEL },
            MyUsers.Keys.FINGERPRINT + " = ?",
            new String[] { VALUE_AUTOTRUST }, null);
        if (c != null) {
            if (c.moveToNext()) {
                result = c.getInt(0);
            }
            c.close();
        }
        return result;
    }

    /** Sets the trusted keys, deleting all previous entries. */
    public static int setTrustedKeys(Context context, Map<String, TrustedFingerprint> trustedKeys) {
        ContentValues[] values = new ContentValues[trustedKeys.size()];
        Iterator<Map.Entry<String, TrustedFingerprint>> entries = trustedKeys.entrySet().iterator();
        for (int i = 0; i < values.length; i++) {
            Map.Entry<String, TrustedFingerprint> e = entries.next();
            values[i] = new ContentValues(2);
            values[i].put(MyUsers.Keys.JID, e.getKey());
            values[i].put(MyUsers.Keys.FINGERPRINT, e.getValue().fingerprint);
            values[i].put(MyUsers.Keys.TRUST_LEVEL, e.getValue().trustLevel);
        }
        return context.getContentResolver().bulkInsert(MyUsers.Keys.CONTENT_URI, values);
    }

    /** Returns a JID-fingerprint map of trusted keys. */
    public static Map<String, TrustedFingerprint> getTrustedKeys(Context context) {
        Cursor c = context.getContentResolver().query(MyUsers.Keys.CONTENT_URI, new String[] {
            MyUsers.Keys.JID,
            MyUsers.Keys.FINGERPRINT,
            MyUsers.Keys.TRUST_LEVEL,
        },
            MyUsers.Keys.FINGERPRINT + " <> ?", new String[] { VALUE_AUTOTRUST },
            null);

        Map<String, TrustedFingerprint> list = new HashMap<>(c.getCount());
        while (c.moveToNext()) {
            String fpr = c.getString(1);
            if (fpr != null)
                list.put(c.getString(0), new TrustedFingerprint(fpr, c.getInt(2)));
        }

        c.close();
        return list;
    }

    /** Converts a raw-string trusted fingerprint map to a {@link TrustedFingerprint} map. */
    public static Map<String, TrustedFingerprint> fromTrustedFingerprintMap(Map<String, String> props) {
        Map<String, Keyring.TrustedFingerprint> keys = new HashMap<>(props.size());
        for (Map.Entry e : props.entrySet()) {
            TrustedFingerprint fingerprint = TrustedFingerprint
                .fromString((String) e.getValue());
            if (fingerprint != null) {
                keys.put((String) e.getKey(), fingerprint);
            }
        }
        return keys;
    }

    /** Converts a {@link TrustedFingerprint} map to a raw-string trusted fingerprint map. */
    public static Map<String, String> toTrustedFingerprintMap(Map<String, TrustedFingerprint> props) {
        Map<String, String> keys = new HashMap<>(props.size());
        for (Map.Entry<String, TrustedFingerprint> e : props.entrySet()) {
            TrustedFingerprint fingerprint = e.getValue();
            if (fingerprint != null) {
                keys.put(e.getKey(), e.toString());
            }
        }
        return keys;
    }

    public static final class TrustedFingerprint {
        public final String fingerprint;
        public final int trustLevel;

        TrustedFingerprint(String fingerprint, int trustLevel) {
            this.fingerprint = fingerprint;
            this.trustLevel = trustLevel;
        }

        @Override
        public String toString() {
            return fingerprint + "|" + trustLevel;
        }

        public static TrustedFingerprint fromString(String value) {
            if (!TextUtils.isEmpty(value)) {
                String[] parsed = value.split("\\|");
                String fingerprint = parsed[0];
                int trustLevel = MyUsers.Keys.TRUST_UNKNOWN;
                if (parsed.length > 1) {
                    String _trustLevel = parsed[1];
                    try {
                        trustLevel = Integer.parseInt(_trustLevel);
                    }
                    catch (NumberFormatException ignored) {
                    }
                }

                return new TrustedFingerprint(fingerprint, trustLevel);
            }
            return null;
        }
    }

    public static final class TrustedPublicKeyData {
        public final byte[] keyData;
        public final int trustLevel;
        public final boolean manualTrust;

        private TrustedPublicKeyData(byte[] keyData, int trustLevel, boolean manualTrust) {
            this.keyData = keyData;
            this.trustLevel = trustLevel;
            this.manualTrust = manualTrust;
        }
    }

}

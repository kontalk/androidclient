/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.data;

import java.io.IOException;
import java.io.InputStream;

import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.kontalk.crypto.PGP;
import org.kontalk.provider.MyUsers.Keys;
import org.kontalk.provider.MyUsers.Users;


/**
 * A simple contact.
 * @author Daniele Ricci
 */
public class Contact {
    private final static String TAG = Contact.class.getSimpleName();

    private final static String[] ALL_CONTACTS_PROJECTION = {
        Users._ID,
        Users.CONTACT_ID,
        Users.LOOKUP_KEY,
        Users.DISPLAY_NAME,
        Users.NUMBER,
        Users.JID,
        Users.REGISTERED,
        Users.STATUS,
        Users.FINGERPRINT,
        Users.BLOCKED,
        Keys.TRUSTED_PUBLIC_KEY,
    };

    public static final int COLUMN_ID = 0;
    public static final int COLUMN_CONTACT_ID = 1;
    public static final int COLUMN_LOOKUP_KEY = 2;
    public static final int COLUMN_DISPLAY_NAME = 3;
    public static final int COLUMN_NUMBER = 4;
    public static final int COLUMN_JID = 5;
    public static final int COLUMN_REGISTERED = 6;
    public static final int COLUMN_STATUS = 7;
    public static final int COLUMN_FINGERPRINT = 8;
    public static final int COLUMN_BLOCKED = 9;
    public static final int COLUMN_TRUSTED_PUBLIC_KEY = 10;

    /** The aggregated Contact id identified by this object. */
    private final long mContactId;

    private String mNumber;
    private String mName;
    private String mJID;

    private String mLookupKey;
    private Uri mContactUri;
    private boolean mRegistered;
    private String mStatus;

    private boolean mBlocked;

    private BitmapDrawable mAvatar;
    private byte [] mAvatarData;

    private String mFingerprint;
    private PGPPublicKeyRing mTrustedKeyRing;

    /** Timestamp the user was last seen. Not coming from the database. */
    private long mLastSeen;
    /** Version information. Not coming from the database. */
    private String mVersion;

    public interface ContactCallback {
        public void avatarLoaded(Contact contact, Drawable avatar);
    }

    /**
     * Contact cache.
     * @author Daniele Ricci
     */
    private final static class ContactCache extends LruCache<String, Contact> {
        private static final int MAX_ENTRIES = 20;

        public ContactCache() {
            super(MAX_ENTRIES);
        }

        public synchronized Contact get(Context context, String userId, String numberHint) {
            Contact c = get(userId);
            if (c == null) {
                c = _findByUserId(context, userId);
                if (c != null) {
                    // put the contact in the cache
                    put(userId, c);
                }
                // try system contacts lookup
                else if (numberHint != null) {
                    Log.v(TAG, "contact not found, trying with system contacts (" + numberHint + ")");
                    ContentResolver resolver = context.getContentResolver();
                    Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numberHint));
                    Cursor cur = resolver.query(uri, new String[] {
                                PhoneLookup.DISPLAY_NAME,
                                PhoneLookup.LOOKUP_KEY,
                                PhoneLookup._ID,
                            }, null, null, null);
                    if (cur.moveToFirst()) {
                        String name = cur.getString(0);
                        String lookupKey = cur.getString(1);
                        long cid = cur.getLong(2);

                        c = new Contact(cid, lookupKey, name, numberHint, userId, false);
                        put(userId, c);

                        // insert result into users database immediately
                        ContentValues values = new ContentValues(6);
                        values.put(Users.HASH, XmppStringUtils.parseLocalpart(userId));
                        values.put(Users.NUMBER, numberHint);
                        values.put(Users.DISPLAY_NAME, name);
                        values.put(Users.JID, userId);
                        values.put(Users.LOOKUP_KEY, lookupKey);
                        values.put(Users.CONTACT_ID, cid);
                        resolver.insert(Users.CONTENT_URI, values);
                    }
                    cur.close();
                }
            }

            return c;
        }
    }

    private final static ContactCache cache = new ContactCache();

    private Contact(long contactId, String lookupKey, String name, String number, String jid, boolean blocked) {
        mContactId = contactId;
        mLookupKey = lookupKey;
        mName = name;
        mNumber = number;
        mJID = jid;
        mBlocked = blocked;
    }

    /** Returns the {@link Contacts} {@link Uri} identified by this object. */
    public Uri getUri() {
        if (mContactUri == null) {
            if (mLookupKey != null)
                mContactUri = ContactsContract.Contacts.getLookupUri(mContactId, mLookupKey);
            else
                mContactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, mContactId);
        }
        return mContactUri;
    }

    public long getId() {
        return mContactId;
    }

    public String getNumber() {
        return mNumber;
    }

    public String getName() {
        return mName;
    }

    public String getJID() {
        return mJID;
    }

    public boolean isRegistered() {
        return mRegistered;
    }

    public String getStatus() {
        return mStatus;
    }

    public boolean isBlocked() {
        return mBlocked;
    }

    public PGPPublicKeyRing getTrustedPublicKeyRing() {
        return mTrustedKeyRing;
    }

    public String getFingerprint() {
        return mFingerprint;
    }

    public long getLastSeen() {
        return mLastSeen;
    }

    public void setLastSeen(long lastSeen) {
        mLastSeen = lastSeen;
    }

    public String getVersion() {
        return mVersion;
    }

    public void setVersion(String version) {
        mVersion = version;
    }

    public void getAvatarAsync(final Context context, final ContactCallback callback) {
        if (mAvatar != null) {
            callback.avatarLoaded(this, mAvatar);
        }
        else {
            // start async load
            new Thread(new Runnable() {
                public void run() {
                    try {
                        Drawable avatar = getAvatar(context, null);
                        callback.avatarLoaded(Contact.this, avatar);
                    }
                    catch (Exception e) {
                        // do not throw any exception while loading
                        Log.w(TAG, "error while loading avatar", e);
                    }
                }
            }).start();
        }
    }

    public synchronized Drawable getAvatar(Context context, Drawable defaultValue) {
        if (mAvatar == null) {
            if (mAvatarData == null)
                mAvatarData = loadAvatarData(context, getUri());

            if (mAvatarData != null) {
                Bitmap b = BitmapFactory.decodeByteArray(mAvatarData, 0, mAvatarData.length);
                mAvatar = new BitmapDrawable(context.getResources(), b);
            }
        }
        return mAvatar != null ? mAvatar : defaultValue;
    }

    private void clear() {
        mLastSeen = 0;
        mVersion = null;
    }

    public static void invalidate(String userId) {
        cache.remove(userId);
    }

    public static void invalidate() {
        cache.evictAll();
    }

    /** Invalidates cached data for all contacts. Does not delete contact information. */
    public static void invalidateData() {
        synchronized (cache) {
            for (Contact c : cache.snapshot().values()) {
                c.clear();
            }
        }
    }

    /** Builds a contact from a UsersProvider cursor. */
    public static Contact fromUsersCursor(Context context, Cursor cursor) {
        // try the cache
        String jid = cursor.getString(COLUMN_JID);
        Contact c = cache.get(jid);
        if (c == null) {
            // don't let the cache fetch contact data again - we'll populate it
            final long contactId = cursor.getLong(COLUMN_CONTACT_ID);
            final String key = cursor.getString(COLUMN_LOOKUP_KEY);
            final String name = cursor.getString(COLUMN_DISPLAY_NAME);
            final String number = cursor.getString(COLUMN_NUMBER);
            final boolean registered = (cursor.getInt(COLUMN_REGISTERED) != 0);
            final String status = cursor.getString(COLUMN_STATUS);
            final String fingerprint = cursor.getString(COLUMN_FINGERPRINT);
            final boolean blocked = (cursor.getInt(COLUMN_BLOCKED) != 0);
            final byte[] trustedKeyring = cursor.getBlob(COLUMN_TRUSTED_PUBLIC_KEY);

            c = new Contact(contactId, key, name, number, jid, blocked);
            c.mRegistered = registered;
            c.mStatus = status;
            c.mFingerprint = fingerprint;
            try {
                if (trustedKeyring != null)
                    c.mTrustedKeyRing = PGP.readPublicKeyring(trustedKeyring);
            }
            catch (Exception e) {
                // ignored for now
                Log.w(TAG, "unable to load public keyring", e);
            }

            cache.put(jid, c);
        }
        return c;
    }

    public static String numberByUserId(Context context, String userId) {
        Cursor c = null;
        try {
            ContentResolver cres = context.getContentResolver();
            c = cres.query(Uri.withAppendedPath(Users.CONTENT_URI, userId),
                    new String[] { Users.NUMBER },
                    null, null, null);

            if (c.moveToFirst())
                return c.getString(0);
        }
        finally {
            if (c != null)
                c.close();
        }

        return null;
    }

    public static Contact findByUserId(Context context, String userId) {
        return findByUserId(context, userId, null);
    }

    public static Contact findByUserId(Context context, String userId, String numberHint) {
        return cache.get(context, userId, numberHint);
    }

    private static Contact _findByUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Cursor c = cres.query(Uri.withAppendedPath(Users.CONTENT_URI, userId),
            new String[] {
                Users.NUMBER,
                Users.DISPLAY_NAME,
                Users.LOOKUP_KEY,
                Users.CONTACT_ID,
                Users.REGISTERED,
                Users.STATUS,
                Users.FINGERPRINT,
                Users.BLOCKED,
                Keys.TRUSTED_PUBLIC_KEY,
            }, null, null, null);

        if (c.moveToFirst()) {
            final String number = c.getString(0);
            final String name = c.getString(1);
            final String key = c.getString(2);
            final long cid = c.getLong(3);
            final boolean registered = (c.getInt(4) != 0);
            final String status = c.getString(5);
            final String fingerprint = c.getString(6);
            final boolean blocked = (c.getInt(7) != 0);
            final byte[] trustedKeyring = c.getBlob(8);
            c.close();

            Contact contact = new Contact(cid, key, name, number, userId, blocked);
            contact.mRegistered = registered;
            contact.mStatus = status;
            contact.mFingerprint = fingerprint;
            try {
                if (trustedKeyring != null)
                    contact.mTrustedKeyRing = PGP.readPublicKeyring(trustedKeyring);
            }
            catch (Exception e) {
                // ignored for now
                Log.w(TAG, "unable to load public keyring", e);
            }

            return contact;
        }
        c.close();
        return null;
    }

    private static byte[] loadAvatarData(Context context, Uri contactUri) {
        byte[] data = null;

        Uri uri;
        try {
            long cid = ContentUris.parseId(contactUri);
            uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cid);
        }
        catch (Exception e) {
            uri = contactUri;
        }

        InputStream avatarDataStream = Contacts.openContactPhotoInputStream(
                    context.getContentResolver(), uri);
        if (avatarDataStream != null) {
            try {
                    data = new byte[avatarDataStream.available()];
                    avatarDataStream.read(data, 0, data.length);
            }
            catch (IOException e) {
                Log.e(TAG, "cannot retrieve contact avatar", e);
            }
            finally {
                try {
                    avatarDataStream.close();
                }
                catch (IOException e) {}
            }
        }

        return data;
    }

    public static Cursor queryContacts(Context context) {
        return context.getContentResolver().query(Users.CONTENT_URI, ALL_CONTACTS_PROJECTION,
            Users.REGISTERED + " <> 0", null, Users.DISPLAY_NAME);
    }

}

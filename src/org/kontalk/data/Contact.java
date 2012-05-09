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

package org.kontalk.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.sync.Syncer;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;


/**
 * A simple contact.
 * @author Daniele Ricci
 * @version 1.0
 */
public class Contact {
    private final static String TAG = Contact.class.getSimpleName();

    /** The aggregated Contact id identified by this object. */
    private final long mContactId;
    /** The Kontalk RawContact id identified by this object. */
    private long mRawContactId;
    private String mNumber;
    private String mName;
    private String mHash;

    private String mLookupKey;
    private Uri mContactUri;

    private BitmapDrawable mAvatar;
    private byte [] mAvatarData;

    /**
     * Contact cache.
     * @author Daniele Ricci
     */
    private final static class ContactCache extends HashMap<String, Contact> {
        private static final long serialVersionUID = 2788447346920511692L;

        private final class ContactsObserver extends ContentObserver {
            private Context mContext;
            private String mUserId;

            public ContactsObserver(Context context, String userId) {
                super(null);
                mContext = context;
                mUserId = userId;
            }

            @Override
            public void onChange(boolean selfChange) {
                synchronized (ContactCache.this) {
                    remove(mContext, mUserId);
                    get(mContext, mUserId);
                }
            }
        }

        private Map<String, ContactsObserver> mObservers;

        public ContactCache() {
            mObservers = new HashMap<String, ContactsObserver>();
        }

        public Contact get(Context context, String userId) {
            Contact c = get(userId);
            if (c == null) {
                c = _findByUserId(context, userId);
                if (c != null) {
                    // retrieve a previous observer if present
                    ContactsObserver observer = mObservers.get(userId);
                    if (observer == null) {
                        // create a new observer
                        observer = new ContactsObserver(
                                context.getApplicationContext(),userId);
                        mObservers.put(userId, observer);
                    }
                    // register for changes
                    context.getContentResolver()
                        .registerContentObserver(c.getUri(), false, observer);

                    // put the contact in the cache
                    put(userId, c);
                }
            }

            return c;
        }

        public Contact remove(Context context, String userId) {
            Contact c = remove(userId);
            if (c != null) {
                ContactsObserver observer = mObservers.remove(userId);
                if (observer != null)
                    context.getContentResolver()
                        .unregisterContentObserver(observer);
            }

            return c;
        }
    }

    private final static ContactCache cache = new ContactCache();

    private Contact(long contactId, String lookupKey, String name, String number, String hash) {
        mContactId = contactId;
        mLookupKey = lookupKey;
        mName = name;
        mNumber = number;
        mHash = hash;
    }

    /** Returns the {@link Contacts} {@link Uri} identified by this object. */
    public Uri getUri() {
        if (mContactUri == null)
            mContactUri = ContactsContract.Contacts.getLookupUri(mContactId, mLookupKey);
        return mContactUri;
    }

    public long getId() {
        return mContactId;
    }

    /** Retrieves the raw contact id if needed and returns it. */
    public long getRawContactId(Context context) {
        if (mRawContactId <= 0) {
            Account acc = Authenticator.getDefaultAccount(context);
            Cursor c = context.getContentResolver()
                .query(RawContacts.CONTENT_URI,
                    new String[] {
                        RawContacts._ID
                    },
                    RawContacts.ACCOUNT_NAME + " = ? AND " +
                    RawContacts.ACCOUNT_TYPE + " = ? AND " +
                    Syncer.RAW_COLUMN_PHONE + " = ?",
                    new String[] {
                        acc.name,
                        acc.type,
                        mNumber
                    }, null);

            if (c.moveToFirst()) {
                mRawContactId = c.getLong(0);
            }
            c.close();
        }

        return mRawContactId;
    }

    public long getRawContactId() {
        return mRawContactId;
    }

    public String getNumber() {
        return mNumber;
    }

    public String getName() {
        return mName;
    }

    public String getHash() {
        return mHash;
    }

    public synchronized Drawable getAvatar(Context context, Drawable defaultValue) {
        if (mAvatar == null) {
            if (mAvatarData != null) {
                Bitmap b = BitmapFactory.decodeByteArray(mAvatarData, 0, mAvatarData.length);
                mAvatar = new BitmapDrawable(context.getResources(), b);
            }
        }
        return mAvatar != null ? mAvatar : defaultValue;
    }

    /** Builds a contact from a UsersProvider cursor. */
    public static Contact fromUsersCursor(Context context, Cursor cursor) {
        final long contactId = cursor.getLong(cursor.getColumnIndex(Users.CONTACT_ID));
        final String key = cursor.getString(cursor.getColumnIndex(Users.LOOKUP_KEY));
        final String name = cursor.getString(cursor.getColumnIndex(Users.DISPLAY_NAME));
        final String number = cursor.getString(cursor.getColumnIndex(Users.NUMBER));
        final String hash = cursor.getString(cursor.getColumnIndex(Users.HASH));

        Contact c = new Contact(contactId, key, name, number, hash);
        c.mAvatarData = loadAvatarData(context, c.getUri());
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
        return cache.get(context, userId);
    }

    private static Contact _findByUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Cursor c = cres.query(Uri.withAppendedPath(Users.CONTENT_URI, userId),
            new String[] {
                Users.NUMBER,
                Users.DISPLAY_NAME,
                Users.LOOKUP_KEY,
                Users.CONTACT_ID,
                Users.HASH
            }, null, null, null);

        if (c.moveToFirst()) {
            String number = c.getString(0);
            String name = c.getString(1);
            String key = c.getString(2);
            long cid = c.getLong(3);
            String hash = c.getString(4);
            c.close();

            Contact contact = new Contact(cid, key, name, number, hash);
            contact.mAvatarData = loadAvatarData(context, contact.getUri());
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

    public static String getUserId(Context context, Uri rawContactUri) {
        Cursor c = context.getContentResolver().query(rawContactUri,
                new String[] {
                    RawContacts.SYNC3
                }, null, null, null);

        if (c.moveToFirst()) {
            return c.getString(0);
        }

        return null;
    }


}

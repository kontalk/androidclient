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
import org.kontalk.sync.SyncAdapter;

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
    private final long mRawContactId;
    private String mNumber;
    private String mName;

    private Uri mContactUri;
    private Uri mRawContactUri;

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

    private Contact(Uri uri, long rawContactId, String name, String number) {
        mContactId = ContentUris.parseId(uri);
        mRawContactId = rawContactId;
        mContactUri = uri;
        mName = name;
        mNumber = number;
    }

    private Contact(long contactId, long rawContactId, String name, String number) {
        mContactId = contactId;
        mRawContactId = rawContactId;
        mName = name;
        mNumber = number;
    }

    /** Returns the {@link Contacts} {@link Uri} identified by this object. */
    public Uri getUri() {
        if (mContactUri == null)
            mContactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, mContactId);
        return mContactUri;
    }

    public long getId() {
        return mContactId;
    }

    public long getRawContactId() {
        return mRawContactId;
    }

    /** Returns the {@link RawContacts} {@link Uri} identified by this object. */
    public Uri getRawContactUri() {
        if (mRawContactUri == null)
            mRawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, mRawContactId);
        return mRawContactUri;
    }

    public String getNumber() {
        return mNumber;
    }

    public String getName() {
        return mName;
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

    /**
     * Builds a contact from a RawContact cursor
     * (e.g. a cursor querying only Kontalk RawContacts).
     * @param cursor
     * @return
     */
    public static Contact fromRawContactCursor(Context context, Cursor cursor) {
        final long contactId = cursor.getLong(cursor.getColumnIndex(RawContacts.CONTACT_ID));
        final long rawContactId = cursor.getLong(cursor.getColumnIndex(RawContacts._ID));
        final String name = cursor.getString(cursor.getColumnIndex(SyncAdapter.RAW_COLUMN_DISPLAY_NAME));
        final String number = cursor.getString(cursor.getColumnIndex(SyncAdapter.RAW_COLUMN_PHONE));

        Contact c = new Contact(contactId, rawContactId, name, number);
        c.mAvatarData = loadAvatarData(context, c.getUri());
        return c;
    }

    // TODO convert to UsersProvider
    public static String numberByUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Account acc = Authenticator.getDefaultAccount(context);

        Cursor c = cres.query(RawContacts.CONTENT_URI,
                new String[] {
                    SyncAdapter.RAW_COLUMN_PHONE
                },
                RawContacts.ACCOUNT_NAME + " = ? AND " +
                RawContacts.ACCOUNT_TYPE + " = ? AND " +
                RawContacts.SYNC3        + " = ?",
                new String[] {
                    acc.name,
                    acc.type,
                    userId
                }, null);

        if (c.moveToFirst()) {
            String number = c.getString(0);
            c.close();
            return number;
        }

        c.close();
        return null;
    }

    public static Contact findByUserId(Context context, String userId) {
        return cache.get(context, userId);
    }

    private static Contact _findByUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Cursor c = cres.query(Uri.withAppendedPath(Users.CONTENT_URI, userId),
                new String[] { Users.NUMBER, Users.LOOKUP_KEY },
                null, null, null);

        if (c.moveToFirst()) {
            String number = c.getString(0);
            String key = c.getString(1);
            c.close();

            Contact contact = _lookupByKey(context, key, number);
            if (contact != null)
                return contact;
        }
        c.close();

        // fallback to RawContacts
        return _lookupByUserId(context, userId);
    }

    private static Contact _lookupByKey(Context context, String lookupKey, String number) {
        Uri lookupUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, lookupKey);
        Cursor c = context.getContentResolver().query(lookupUri,
                new String[] {
                    Contacts._ID,
                    Contacts.DISPLAY_NAME,
                }, null, null, null);
        if (c.moveToFirst()) {
            long id = c.getLong(0);
            String name = c.getString(1);
            c.close();

            // create contact
            Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
            Log.v(TAG, "found contact " + uri + " (lookup)");

            // RawContact ID not available here
            Contact contact = new Contact(uri, -1, name, number);

            // load avatar (if any)
            contact.mAvatarData = loadAvatarData(context, uri);

            return contact;
        }

        c.close();
        return null;
    }

    private static Contact _lookupByUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Account acc = Authenticator.getDefaultAccount(context);

        Cursor c = cres.query(RawContacts.CONTENT_URI,
                new String[] {
                    RawContacts._ID,
                    RawContacts.CONTACT_ID,
                    SyncAdapter.RAW_COLUMN_DISPLAY_NAME,
                    SyncAdapter.RAW_COLUMN_PHONE
                },
                RawContacts.ACCOUNT_NAME + " = ? AND " +
                RawContacts.ACCOUNT_TYPE + " = ? AND " +
                RawContacts.SYNC3        + " = ?",
                new String[] {
                    acc.name,
                    acc.type,
                    userId
                }, null);

        if (c.moveToFirst()) {
            long rid = c.getLong(0);
            long id = c.getLong(1);
            String name = c.getString(2);
            String number = c.getString(3);
            c.close();

            // create contact
            Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
            Log.v(TAG, "found contact " + uri + " (native)");
            Contact contact = new Contact(uri, rid, name, number);
            // load avatar (if any)
            contact.mAvatarData = loadAvatarData(context, uri);

            return contact;
        }

        c.close();
        return null;
    }

    private static byte[] loadAvatarData(Context context, Uri contactUri) {
        byte[] data = null;

        InputStream avatarDataStream = Contacts.openContactPhotoInputStream(
                    context.getContentResolver(), contactUri);
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

    // TODO convert to UsersProvider
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

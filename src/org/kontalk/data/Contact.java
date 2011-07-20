package org.kontalk.data;

import java.io.IOException;
import java.io.InputStream;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.sync.SyncAdapter;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
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

    private Contact(Uri uri, long rawContactId, String name, String number) {
        mContactId = ContentUris.parseId(uri);
        mContactUri = uri;
        mRawContactId = rawContactId;
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

    public static Contact findbyUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Account acc = Authenticator.getDefaultAccount(context);

        Cursor c = cres.query(RawContacts.CONTENT_URI,
                new String[] {
                    RawContacts._ID,
                    RawContacts.CONTACT_ID,
                    RawContacts.SYNC1,
                    RawContacts.SYNC2
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

            // create contact
            Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
            Log.i(TAG, "found contact " + uri);
            Contact contact = new Contact(uri, rid, name, number);
            // load avatar (if any)
            contact.mAvatarData = loadAvatarData(context, uri);

            return contact;
        }

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

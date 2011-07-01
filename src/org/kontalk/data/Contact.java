package org.kontalk.data;

import java.io.IOException;
import java.io.InputStream;

import org.kontalk.authenticator.Authenticator;

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

    private final Uri mUri;
    private String mNumber;
    private String mName;

    private BitmapDrawable mAvatar;
    private byte [] mAvatarData;

    private Contact(Uri uri, String name, String number) {
        mUri = uri;
        mName = name;
        mNumber = number;
    }

    public Uri getUri() {
        return mUri;
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

    public static Contact findbyUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Account acc = Authenticator.getDefaultAccount(context);

        Cursor c = cres.query(RawContacts.CONTENT_URI,
                new String[] {
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
            long id = c.getLong(0);
            String name = c.getString(1);
            String number = c.getString(2);

            // create contact
            Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
            Log.i(TAG, "found contact " + uri);
            Contact contact = new Contact(uri, name, number);
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

    public static String getUserId(Context context, Uri contactUri) {
        Account account = Authenticator.getDefaultAccount(context);
        Cursor c = context.getContentResolver().query(RawContacts.CONTENT_URI,
                new String[] {
                    RawContacts.SYNC3
                },
                RawContacts.ACCOUNT_NAME + " = ? AND " +
                RawContacts.ACCOUNT_TYPE + " = ? AND " +
                RawContacts.CONTACT_ID   + " = ?",
                new String[] {
                    account.name,
                    account.type,
                    String.valueOf(ContentUris.parseId(contactUri))
                }, null);

        if (c.moveToFirst()) {
            return c.getString(0);
        }

        return null;
    }


}

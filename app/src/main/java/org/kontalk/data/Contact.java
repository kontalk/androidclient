/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.support.annotation.NonNull;
import android.support.v4.util.LruCache;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PGPLazyPublicKeyRingLoader;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MyUsers.Keys;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;


/**
 * A simple contact.
 * @author Daniele Ricci
 */
public class Contact {
    final static String TAG = Contact.class.getSimpleName();

    private final static String[] ALL_CONTACTS_PROJECTION = {
        Users._ID,
        Users.CONTACT_ID,
        Users.LOOKUP_KEY,
        Users.DISPLAY_NAME,
        Users.NUMBER,
        Users.JID,
        Users.REGISTERED,
        Users.STATUS,
        Users.BLOCKED,
    };

    public static final int COLUMN_ID = 0;
    public static final int COLUMN_CONTACT_ID = 1;
    public static final int COLUMN_LOOKUP_KEY = 2;
    public static final int COLUMN_DISPLAY_NAME = 3;
    public static final int COLUMN_NUMBER = 4;
    public static final int COLUMN_JID = 5;
    public static final int COLUMN_REGISTERED = 6;
    public static final int COLUMN_STATUS = 7;
    public static final int COLUMN_BLOCKED = 8;

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

    private Drawable mAvatar;
    private byte [] mAvatarData;

    private String mFingerprint;
    private PGPLazyPublicKeyRingLoader mTrustedKeyRing;
    // trust level for the above trusted keyring
    private int mTrustedLevel;

    /** Timestamp the user was last seen. Not coming from the database. */
    private long mLastSeen;

    /** Cached name information from system contacts. It will override our internal name. */
    private StructuredName mStructuredName;

    private static final class StructuredName {
        public final String displayName;
        public final String givenName;
        public final String middleName;
        public final String familyName;

        public StructuredName(String displayName, String givenName, String middleName, String familyName) {
            this.displayName = displayName;
            this.givenName = givenName;
            this.middleName = middleName;
            this.familyName = familyName;
        }
    }

    public interface ContactCallback {
        void avatarLoaded(Contact contact, Drawable avatar);
    }

    public interface ContactChangeListener {
        void onContactInvalidated(String userId);
    }

    private static final Set<ContactChangeListener> sListeners = new HashSet<>();

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
                        Uri contactUri = c.getUri();
                        if (contactUri != null) {
                            c.loadStructuredNameAsync(context);
                        }

                        put(userId, c);

                        // insert result into users database immediately
                        ContentValues values = new ContentValues(5);
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

    /** Stores volatile and connection-time information about a contact. */
    private static final class ContactState {
        private final String mJID;
        private boolean mTyping;
        /** Version information. Not coming from the database. */
        private String mVersion;

        ContactState(String jid) {
            mJID = jid;
        }

        public boolean isTyping() {
            return mTyping;
        }

        public void setTyping(boolean typing) {
            mTyping = typing;
        }

        public String getVersion() {
            return mVersion;
        }

        public void setVersion(String version) {
            mVersion = version;
        }

        @Override
        public boolean equals(Object o) {
            return o == this ||
                (o instanceof ContactState &&
                    ((ContactState) o).mJID.equals(mJID));
        }

        @Override
        public int hashCode() {
            return mJID.hashCode();
        }
    }

    // keys is the full JID because typing is not a global but a device state
    private static final Map<String, ContactState> sStates = new HashMap<>();

    public static void init(Context context, Handler handler) {
        context.getContentResolver().registerContentObserver(Contacts.CONTENT_URI, false,
            new ContentObserver(handler) {
                @Override
                public void onChange(boolean selfChange) {
                    invalidate();
                }
            }
        );
    }

    private static ContactState getContactState(String jid) {
        ContactState state = sStates.get(jid);
        if (state == null) {
            state = new ContactState(jid);
            sStates.put(jid, state);
        }
        return state;
    }

    public static void setTyping(String jid, boolean typing) {
        getContactState(jid).setTyping(typing);
    }

    public static boolean isTyping(String jid) {
        ContactState state = sStates.get(jid);
        return state != null && state.isTyping();
    }

    public static void setVersion(String jid, String version) {
        getContactState(jid).setVersion(version);
    }

    public static String getVersion(String jid) {
        ContactState state = sStates.get(jid);
        return state != null ? state.getVersion() : null;
    }

    public static void clearState(String jid) {
        sStates.remove(jid);
    }

    Contact(long contactId, String lookupKey, String name, String number, String jid, boolean blocked) {
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
            if (mLookupKey != null) {
                mContactUri = ContactsContract.Contacts.getLookupUri(mContactId, mLookupKey);
            }
            else if (mContactId > 0) {
                mContactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, mContactId);
            }
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
        return mStructuredName != null && mStructuredName.displayName != null ?
            mStructuredName.displayName : mName;
    }

    /** Returns a visible and readable name that can be used across the UI. */
    public String getDisplayName() {
        String name = getName();
        if (name != null && name.length() > 0)
            return name;
        if (mNumber != null && mNumber.length() > 0)
            return mNumber;
        return mJID;
    }

    /**
     * Return a visible and readable name in a short form (e.g. given name).
     * @return the short name (e.g. given name). If not available, return {@link #getDisplayName()}.
     */
    public String getShortDisplayName() {
        if (mStructuredName != null && mStructuredName.givenName != null)
            return mStructuredName.givenName;
        return getDisplayName();
    }

    private void loadStructuredNameAsync(final Context context) {
        // avoid keeping a local context from an unrelated thread
        final Context globalContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                mStructuredName = loadStructuredName(globalContext, getUri());
                if (mStructuredName != null && mStructuredName.displayName != null &&
                    !mStructuredName.displayName.equals(mName)) {

                    // name changed, update user immediately
                    mName = mStructuredName.displayName;

                    ContentValues values = new ContentValues(1);
                    values.put(Users.DISPLAY_NAME, mName);
                    globalContext.getContentResolver().update(Users.CONTENT_URI,
                        values, Users.JID + "=?", new String[] { mJID });

                    Contact.invalidate(mJID);
                }
            }
        }).start();
    }

    private static StructuredName loadStructuredName(Context context, Uri uri) {
        Cursor nameQuery = null;
        try {
            nameQuery = context.getContentResolver().query(uri.buildUpon()
                        .appendPath(Contacts.Data.CONTENT_DIRECTORY).build(), new String[] {
                    ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME,
                    ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                },
                ContactsContract.Data.MIMETYPE + "=? AND " + Contacts.DISPLAY_NAME_PRIMARY + "="
                    + ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME,
                new String[] { ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE },
                null);
            if (nameQuery != null && nameQuery.moveToFirst()) {
                String displayName = nameQuery.getString(0);
                String givenName = nameQuery.getString(1);
                String middleName = nameQuery.getString(2);
                String familyName = nameQuery.getString(3);
                return new StructuredName(displayName, givenName, middleName, familyName);
            }
        }
        catch (Exception ignored) {
            Log.e("CONTACT", "error loading contact data", ignored);
        }
        finally {
            if (nameQuery != null) {
                try {
                    nameQuery.close();
                }
                catch (Exception ignored) {
                }
            }
        }

        return null;
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
        try {
            if (mTrustedKeyRing != null)
                return mTrustedKeyRing.getPublicKeyRing();
        }
        catch (Exception e) {
            // ignored for now
            Log.w(TAG, "unable to load public keyring", e);
        }
        return null;
    }

    public String getTrustedFingerprint() {
        try {
            if (mTrustedKeyRing != null)
                return mTrustedKeyRing.getFingerprint();
        }
        catch (Exception e) {
            // ignored for now
            Log.w(TAG, "unable to load public keyring", e);
        }
        return null;
    }

    public int getTrustedLevel() {
        return mTrustedLevel;
    }

    public String getFingerprint() {
        return mFingerprint;
    }

    /** Returns true if the key is unknown, i.e. no key was trusted yet. */
    public boolean isKeyUnknown() {
        return mTrustedKeyRing == null;
    }

    /** Returns true if the key has changed and not approved yet. */
    public boolean isKeyChanged() {
        String trustedFingerprint = getTrustedFingerprint();
        return (trustedFingerprint == null || mFingerprint == null) ||
            !mFingerprint.equals(trustedFingerprint);
    }

    public long getLastSeen() {
        return mLastSeen;
    }

    public void setLastSeen(long lastSeen) {
        mLastSeen = lastSeen;
    }

    public boolean isSelf(Context context) {
        try {
            return Authenticator.isSelfJID(context, JidCreate.bareFrom(mJID));
        }
        catch (XmppStringprepException e) {
            return false;
        }
    }

    @NonNull
    private static Drawable generateRandomAvatar(Context context, Contact contact) {
        String letter = (contact.mName != null && contact.mName.length() > 0) ?
            contact.mName : contact.mJID;
        int size = context.getResources().getDimensionPixelSize(R.dimen.avatar_size);

        return TextDrawable.builder()
            .beginConfig()
            .width(size)
            .height(size)
            .endConfig()
            .buildRect(letter.substring(0, 1).toUpperCase(Locale.US),
                ColorGenerator.MATERIAL.getColor(contact.mJID));
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
                        Drawable avatar = getAvatar(context);
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

    public synchronized Drawable getAvatar(Context context) {
        if (mAvatar == null) {
            Bitmap b = loadAvatarBitmap(context);
            if (b != null) {
                mAvatar = new BitmapDrawable(context.getResources(), b);
            }
        }

        if (mAvatar == null)
            mAvatar = generateRandomAvatar(context, this);

        return mAvatar;
    }

    private synchronized Bitmap loadAvatarBitmap(Context context) {
        if (mAvatarData == null) {
            Uri uri = getUri();
            if (uri != null)
                mAvatarData = loadAvatarData(context, uri);
        }

        if (mAvatarData != null)
            return BitmapFactory.decodeByteArray(mAvatarData, 0, mAvatarData.length);

        return null;
    }

    /**
     * Public version of {@link #loadAvatarBitmap} which includes the random
     * avatar generation.
     * @param context a context
     * @param resizeForNotification true for resizing the avatar to the large icon size 128x128
     * @return a newly-allocated {@link Bitmap}
     */
    @NonNull
    public synchronized Bitmap getAvatarBitmap(Context context, boolean resizeForNotification) {
        Bitmap avatar = loadAvatarBitmap(context);
        if (avatar == null) {
            Drawable d = generateRandomAvatar(context, this);
            avatar = MessageUtils.drawableToBitmap(d);
        }

        if (resizeForNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            // Contact bitmaps are 96x96 so we have to scale 'em up to 128x128 to fill the whole notification large icon.
            // inspired by the AOSP Mms app
            final Resources res = context.getResources();
            final int idealIconHeight =
                res.getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
            final int idealIconWidth =
                res.getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
            if (avatar.getHeight() < idealIconHeight) {
                // Scale this image to fit the intended size
                Bitmap scaledAvatar = Bitmap.createScaledBitmap(
                    avatar, idealIconWidth, idealIconHeight, true);
                if (scaledAvatar != null) {
                    if (scaledAvatar != avatar)
                        avatar.recycle();
                    avatar = scaledAvatar;
                }
            }
        }

        return avatar;
    }

    /**
     * Public version of {@link #loadAvatarBitmap} which includes the random
     * avatar generation.
     * @return a newly-allocated {@link Bitmap}
     */
    @NonNull
    public synchronized Bitmap getAvatarBitmap(Context context) {
        return getAvatarBitmap(context, false);
    }

    private void clear() {
        mLastSeen = 0;
    }

    public static void invalidate(String userId) {
        cache.remove(userId);
        fireContactInvalidated(userId);
    }

    public static void invalidate() {
        cache.evictAll();
        fireContactInvalidated(null);
    }

    /** Invalidates cached data for all contacts. Does not delete contact information. */
    public static void invalidateData() {
        synchronized (cache) {
            for (Contact c : cache.snapshot().values()) {
                c.clear();
            }
        }
        // invalidate contact state
        sStates.clear();
    }

    /** Invalidates cached data for the given contact. Does not delete contact information. */
    public static void invalidateData(String userId) {
        Contact c = cache.get(XmppStringUtils.parseBareJid(userId));
        if (c != null)
            c.clear();
        // invalidate contact state
        clearState(userId);
    }

    public static void registerContactChangeListener(ContactChangeListener l) {
        sListeners.add(l);
    }

    public static void unregisterContactChangeListener(ContactChangeListener l) {
        sListeners.remove(l);
    }

    private static void fireContactInvalidated(String userId) {
        for (ContactChangeListener l : sListeners) {
            l.onContactInvalidated(userId);
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
            final boolean blocked = (cursor.getInt(COLUMN_BLOCKED) != 0);

            c = new Contact(contactId, key, name, number, jid, blocked);
            c.mRegistered = registered;
            c.mStatus = status;

            Uri uri = c.getUri();
            if (uri != null) {
                c.loadStructuredNameAsync(context);
            }

            retrieveKeyInfo(context, c);

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

    public static Contact findByUserId(Context context, @NonNull String userId) {
        return findByUserId(context, userId, null);
    }

    @NonNull
    public static Contact findByUserId(Context context, @NonNull String userId, String numberHint) {
        String normalizedUserId = XmppStringUtils.parseBareJid(userId);
        Contact c = cache.get(context, normalizedUserId, numberHint);
        // build dummy contact if not found
        if (c == null) {
            c = new Contact(-1, null, normalizedUserId, numberHint, userId, false);
            // try to retrieve the key from the keyring
            // We may find one for pending subscription users which have
            // disappeared from the users table after a resync
            retrieveKeyInfo(context, c);
        }
        return c;
    }

    private static void retrieveKeyInfo(Context context, Contact c) {
        // trusted key
        Keyring.TrustedPublicKeyData trustedKeyring = Keyring.getPublicKeyData(context, c.getJID(), Keys.TRUST_IGNORED);
        // latest (possibly unknown) fingerprint
        c.mFingerprint = Keyring.getFingerprint(context, c.getJID(), Keys.TRUST_UNKNOWN);
        if (trustedKeyring != null) {
            c.mTrustedKeyRing = new PGPLazyPublicKeyRingLoader(trustedKeyring.keyData);
            c.mTrustedLevel = trustedKeyring.trustLevel;
        }
    }

    static Contact _findByUserId(Context context, String userId) {
        ContentResolver cres = context.getContentResolver();
        Cursor c = cres.query(Uri.withAppendedPath(Users.CONTENT_URI, userId),
            new String[] {
                Users.NUMBER,
                Users.DISPLAY_NAME,
                Users.LOOKUP_KEY,
                Users.CONTACT_ID,
                Users.REGISTERED,
                Users.STATUS,
                Users.BLOCKED,
            }, null, null, null);

        if (c.moveToFirst()) {
            final String number = c.getString(0);
            final String name = c.getString(1);
            final String key = c.getString(2);
            final long cid = c.getLong(3);
            final boolean registered = (c.getInt(4) != 0);
            final String status = c.getString(5);
            final boolean blocked = (c.getInt(6) != 0);
            c.close();

            Contact contact = new Contact(cid, key, name, number, userId, blocked);
            contact.mRegistered = registered;
            contact.mStatus = status;

            Uri uri = contact.getUri();
            if (uri != null) {
                contact.loadStructuredNameAsync(context);
            }

            retrieveKeyInfo(context, contact);

            return contact;
        }
        c.close();
        return null;
    }

    private static byte[] loadAvatarData(Context context, Uri contactUri) {
        byte[] data = null;

        InputStream avatarDataStream;
        try {
            avatarDataStream = Contacts.openContactPhotoInputStream(
                context.getContentResolver(), contactUri);
        }
        catch (Exception e) {
            // fallback to old behaviour
            try {
                long cid = ContentUris.parseId(contactUri);
                Uri uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, cid);
                avatarDataStream = Contacts.openContactPhotoInputStream(
                    context.getContentResolver(), uri);
            }
            catch (Exception ignored) {
                // no way of getting avatar, sorry
                return null;
            }

        }

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
                catch (IOException ignored) {
                }
            }
        }

        return data;
    }

    public static Cursor queryContacts(Context context) {
        String selection = Users.REGISTERED + " <> 0";
        if (!Preferences.getShowBlockedUsers(context)) {
            selection += " AND " + Users.BLOCKED + " = 0";
        }

        return context.getContentResolver().query(Users.CONTENT_URI.buildUpon()
                .appendQueryParameter(Users.EXTRA_INDEX, "true").build(),
            ALL_CONTACTS_PROJECTION,
            selection, null,
            Users.DISPLAY_NAME + " COLLATE NOCASE," + Users.NUMBER + " COLLATE NOCASE");
    }

}

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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.android.providers.contacts.ContactLocaleUtils;
import com.android.providers.contacts.FastScrollingIndexCache;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.RawContacts;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.database.DatabaseUtilsCompat;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.NumberValidator;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyUsers.Keys;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.XMPPUtils;


/**
 * The users provider. Also stores the key trust database.
 * Fast scrolling cache from Google AOSP.
 * @author Daniele Ricci
 */
public class UsersProvider extends ContentProvider {
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".users";

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    static final int DATABASE_VERSION = 10;
    private static final String DATABASE_NAME = "users.db";
    private static final String TABLE_USERS = "users";
    private static final String TABLE_USERS_OFFLINE = "users_offline";
    private static final String TABLE_KEYS = "keys";

    private static final int USERS = 1;
    private static final int USERS_JID = 2;
    private static final int KEYS = 3;
    private static final int KEYS_JID = 4;
    private static final int KEYS_JID_FINGERPRINT = 5;

    private long mLastResync;

    private FastScrollingIndexCache mFastScrollingIndexCache;
    private ContactLocaleUtils mLocaleUtils;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> usersProjectionMap;
    private static HashMap<String, String> keysProjectionMap;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String CREATE_TABLE_USERS = "(" +
            "_id INTEGER PRIMARY KEY," +
            "jid TEXT NOT NULL UNIQUE," +
            "number TEXT NOT NULL UNIQUE," +
            "display_name TEXT," +
            "lookup_key TEXT," +
            "contact_id INTEGER," +
            "registered INTEGER NOT NULL DEFAULT 0," +
            "status TEXT," +
            "last_seen INTEGER," +
            "blocked INTEGER NOT NULL DEFAULT 0" +
            ")";

        /** This table will contain all the users in contact list .*/
        private static final String SCHEMA_USERS =
            "CREATE TABLE " + TABLE_USERS + " " + CREATE_TABLE_USERS;

        private static final String SCHEMA_USERS_OFFLINE =
            "CREATE TABLE " + TABLE_USERS_OFFLINE + CREATE_TABLE_USERS;

        private static final String CREATE_TABLE_KEYS = "(" +
            "jid TEXT NOT NULL," +
            "fingerprint TEXT NOT NULL," +
            "trust_level INTEGER NOT NULL DEFAULT 0," +
            "timestamp INTEGER NOT NULL," +  // key creation timestamp
            "public_key BLOB," +
            "PRIMARY KEY (jid, fingerprint)" +
            ")";

        /** This table will contain keys verified (and trusted) by the user. */
        private static final String SCHEMA_KEYS =
            "CREATE TABLE " + TABLE_KEYS + " " + CREATE_TABLE_KEYS;

        private static final String[] SCHEMA_UPGRADE_V7 = {
            SCHEMA_KEYS,
            "INSERT INTO " + TABLE_KEYS + " SELECT jid, public_key, fingerprint FROM " + TABLE_USERS,
        };

        private static final String[] SCHEMA_UPGRADE_V8 = {
            // online table
            "CREATE TABLE users_backup " + CREATE_TABLE_USERS,
            "INSERT INTO users_backup SELECT _id, jid, number, display_name, lookup_key, contact_id, registered, status, last_seen, public_key, fingerprint, blocked FROM " + TABLE_USERS,
            "DROP TABLE " + TABLE_USERS,
            "ALTER TABLE users_backup RENAME TO " + TABLE_USERS,
            // offline table
            "CREATE TABLE users_backup " + CREATE_TABLE_USERS,
            "INSERT INTO users_backup SELECT _id, jid, number, display_name, lookup_key, contact_id, registered, status, last_seen, public_key, fingerprint, blocked FROM " + TABLE_USERS_OFFLINE,
            "DROP TABLE " + TABLE_USERS_OFFLINE,
            "ALTER TABLE users_backup RENAME TO " + TABLE_USERS_OFFLINE,
        };

        private static final String[] SCHEMA_UPGRADE_V9 = {
            // online table
            "CREATE TABLE users_backup " + CREATE_TABLE_USERS,
            "INSERT INTO users_backup SELECT _id, jid, number, display_name, lookup_key, contact_id, registered, status, last_seen, blocked FROM " + TABLE_USERS,
            "DROP TABLE " + TABLE_USERS,
            "ALTER TABLE users_backup RENAME TO " + TABLE_USERS,
            // offline table
            "CREATE TABLE users_backup " + CREATE_TABLE_USERS,
            "INSERT INTO users_backup SELECT _id, jid, number, display_name, lookup_key, contact_id, registered, status, last_seen, blocked FROM " + TABLE_USERS_OFFLINE,
            "DROP TABLE " + TABLE_USERS_OFFLINE,
            "ALTER TABLE users_backup RENAME TO " + TABLE_USERS_OFFLINE,
            // keys table
            "CREATE TABLE keys_backup " + CREATE_TABLE_KEYS,
            "INSERT INTO keys_backup SELECT jid, fingerprint, "+Keys.TRUST_VERIFIED+", strftime('%s')*1000, public_key FROM " + TABLE_KEYS + " WHERE fingerprint IS NOT NULL",
            "DROP TABLE " + TABLE_KEYS,
            "ALTER TABLE keys_backup RENAME TO " + TABLE_KEYS,
        };

        // any upgrade - just replace the table
        private static final String[] SCHEMA_UPGRADE = {
            "DROP TABLE IF EXISTS " + TABLE_USERS,
            SCHEMA_USERS,
            "DROP TABLE IF EXISTS " + TABLE_USERS_OFFLINE,
            SCHEMA_USERS_OFFLINE,
        };

        private Context mContext;

        /** This will be set to true when database is new. */
        private boolean mNew;
        /** A read-only connection to the database. */
        private SQLiteDatabase dbReader;

        protected DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mContext = context;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_USERS);
            db.execSQL(SCHEMA_USERS_OFFLINE);
            db.execSQL(SCHEMA_KEYS);
            mNew = true;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            switch (oldVersion) {
                case 7:
                    // create keys table and trust anyone
                    for (String sql : SCHEMA_UPGRADE_V7)
                        db.execSQL(sql);
                    // go on with next version
                case 8:
                    for (String sql : SCHEMA_UPGRADE_V8)
                        db.execSQL(sql);
                    // go on with next version
                case 9:
                    // new keys management
                    for (String sql : SCHEMA_UPGRADE_V9)
                        db.execSQL(sql);
                    break;
                default:
                    for (String sql : SCHEMA_UPGRADE)
                        db.execSQL(sql);
                    mNew = true;
            }
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            String path = mContext.getDatabasePath(DATABASE_NAME).getPath();
            dbReader = SQLiteDatabase.openDatabase(path, null, 0);
        }

        public boolean isNew() {
            return mNew;
        }

        @Override
        public synchronized void close() {
            try {
                dbReader.close();
            }
            catch (Exception e) {
                // ignored
            }
            dbReader = null;
            super.close();
        }

        @Override
        public synchronized SQLiteDatabase getReadableDatabase() {
            return (dbReader != null) ? dbReader : super.getReadableDatabase();
        }
    }

    @Override
    public boolean onCreate() {
        dbHelper = new DatabaseHelper(getContext());
        mFastScrollingIndexCache = FastScrollingIndexCache.getInstance(getContext());
        mLocaleUtils = ContactLocaleUtils.getInstance();
        return true;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case USERS:
                return Users.CONTENT_TYPE;
            case USERS_JID:
                return Users.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    private void invalidateFastScrollingIndexCache() {
        mFastScrollingIndexCache.invalidate();
    }

    private static final class Counter {
        private int value;

        public Counter(int start) {
            this.value = start;
        }

        public void inc() {
            value++;
        }
    }

    /**
     * Computes counts by the address book index labels and returns it as {@link Bundle} which
     * will be appended to a {@link Cursor} as extras.
     */
    private Bundle getFastScrollingIndexExtras(Cursor cursor) {
        try {
            LinkedHashMap<String, Counter> groups = new LinkedHashMap<>();
            int count = cursor.getCount();

            for (int i = 0; i < count; i++) {
                cursor.moveToNext();
                String source = cursor.getString(Contact.COLUMN_DISPLAY_NAME);
                // use phone number if we don't have a display name
                if (source == null)
                    source = cursor.getString(Contact.COLUMN_NUMBER);
                String label = mLocaleUtils.getLabel(source);
                Counter counter = groups.get(label);
                if (counter == null) {
                    counter = new Counter(1);
                    groups.put(label, counter);
                }
                else {
                    counter.inc();
                }
            }

            int numLabels = groups.size();
            String labels[] = new String[numLabels];
            int counts[] = new int[numLabels];
            int i = 0;
            for (Map.Entry<String, Counter> entry : groups.entrySet()) {
                labels[i] = entry.getKey();
                counts[i] = entry.getValue().value;
                i++;
            }

            return FastScrollingIndexCache.buildExtraBundle(labels, counts);
        } finally {
            // reset the cursor
            cursor.move(-1);
        }
    }

    /**
     * Add the "fast scrolling index" bundle, generated by {@link #getFastScrollingIndexExtras},
     * to a cursor as extras.  It first checks {@link FastScrollingIndexCache} to see if we
     * already have a cached result.
     */
    @SuppressLint("NewApi")
    private void bundleFastScrollingIndexExtras(UsersCursor cursor, Uri queryUri,
        final SQLiteDatabase db, SQLiteQueryBuilder qb, String selection,
        String[] selectionArgs, String sortOrder, String countExpression) {

        Bundle b;
        // Note even though FastScrollingIndexCache is thread-safe, we really need to put the
        // put-get pair in a single synchronized block, so that even if multiple-threads request the
        // same index at the same time (which actually happens on the phone app) we only execute
        // the query once.
        //
        // This doesn't cause deadlock, because only reader threads get here but not writer
        // threads.  (Writer threads may call invalidateFastScrollingIndexCache(), but it doesn't
        // synchronize on mFastScrollingIndexCache)
        //
        // All reader and writer threads share the single lock object internally in
        // FastScrollingIndexCache, but the lock scope is limited within each put(), get() and
        // invalidate() call, so it won't deadlock.

        // Synchronizing on a non-static field is generally not a good idea, but nobody should
        // modify mFastScrollingIndexCache once initialized, and it shouldn't be null at this point.
        synchronized (mFastScrollingIndexCache) {
            b = mFastScrollingIndexCache.get(
                queryUri, selection, selectionArgs, sortOrder, countExpression);

            if (b == null) {
                // Not in the cache.  Generate and put.
                b = getFastScrollingIndexExtras(cursor);

                mFastScrollingIndexCache.put(queryUri, selection, selectionArgs, sortOrder,
                    countExpression, b);
            }
        }
        cursor.setExtras(b);
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean offline = Boolean.parseBoolean(uri.getQueryParameter(Users.OFFLINE));

        int match = sUriMatcher.match(uri);
        if (match == USERS || match == USERS_JID) {
            // use the same table name as an alias
            String table = offline ? (TABLE_USERS_OFFLINE + " " + TABLE_USERS) :
                TABLE_USERS;
            qb.setTables(table);
            qb.setProjectionMap(usersProjectionMap);
        }
        else if (match == KEYS || match == KEYS_JID || match == KEYS_JID_FINGERPRINT) {
            qb.setTables(TABLE_KEYS);
            qb.setProjectionMap(keysProjectionMap);
        }

        switch (match) {
            case USERS:
                // nothing to do
                break;

            case USERS_JID: {
                // TODO append to selection
                String userId = uri.getPathSegments().get(1);
                selection = TABLE_USERS + "." + Users.JID + " = ?";
                selectionArgs = new String[] { userId };
                break;
            }

            case KEYS:
                // nothing to do
                break;

            case KEYS_JID:
            case KEYS_JID_FINGERPRINT:
                String userId = uri.getPathSegments().get(1);
                selection = DatabaseUtilsCompat.concatenateWhere(selection, Keys.JID + "=?");
                selectionArgs = DatabaseUtilsCompat.appendSelectionArgs(selectionArgs, new String[] { userId });
                // TODO support for fingerprint in Uri
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if ((match == USERS || match == USERS_JID) && c.getCount() == 0 &&
                (match != USERS_JID || !XMPPUtils.isDomainJID(uri.getPathSegments().get(1)))) {
            // empty result set and sync requested
            SyncAdapter.requestSync(getContext(), false);
        }
        if (Boolean.parseBoolean(uri.getQueryParameter(Users.EXTRA_INDEX)) && c.getCount() > 0) {
            UsersCursor uc = new UsersCursor(c);
            bundleFastScrollingIndexExtras(uc, uri, db, qb, selection, selectionArgs, sortOrder, null);
            c = uc;
        }

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    /** Reverse-lookup a userId hash to insert a new record to users table.
     * FIXME this method could take a very long time to complete.
    private void newRecord(SQLiteDatabase db, String matchHash) {
        // lookup all phone numbers until our hash matches
        Context context = getContext();
        final Cursor phones = context.getContentResolver().query(Phone.CONTENT_URI,
            new String[] { Phone.NUMBER, Phone.DISPLAY_NAME, Phone.LOOKUP_KEY, Phone.CONTACT_ID },
            null, null, null);

        try {
            while (phones.moveToNext()) {
                String number = phones.getString(0);

                // a phone number with less than 4 digits???
                if (number.length() < 4)
                    continue;

                // fix number
                try {
                    number = NumberValidator.fixNumber(context, number,
                            Authenticator.getDefaultAccountName(context), null);
                }
                catch (Exception e) {
                    Log.e(TAG, "unable to normalize number: " + number + " - skipping", e);
                    // skip number
                    continue;
                }

                try {
                    String hash = MessageUtils.sha1(number);
                    if (hash.equalsIgnoreCase(matchHash)) {
                        ContentValues values = new ContentValues();
                        values.put(Users.HASH, matchHash);
                        values.put(Users.NUMBER, number);
                        values.put(Users.DISPLAY_NAME, phones.getString(1));
                        values.put(Users.LOOKUP_KEY, phones.getString(2));
                        values.put(Users.CONTACT_ID, phones.getLong(3));
                        db.insert(TABLE_USERS, null, values);
                        break;
                    }
                }
                catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "unable to generate SHA-1 hash for " + number + " - skipping", e);
                }
                catch (SQLiteConstraintException sqe) {
                    // skip duplicate number
                    break;
                }
            }
        }
        finally {
            phones.close();
        }

    }
    */

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        try {
            boolean isResync = Boolean.parseBoolean(uri.getQueryParameter(Users.RESYNC));
            boolean bootstrap = Boolean.parseBoolean(uri.getQueryParameter(Users.BOOTSTRAP));
            boolean commit = Boolean.parseBoolean(uri.getQueryParameter(Users.COMMIT));

            if (isResync) {
                // we keep this synchronized to allow for the initial resync by the
                // registration activity
                synchronized (this) {
                    long diff = System.currentTimeMillis() - mLastResync;
                    if (diff > 1000 && (!bootstrap || dbHelper.isNew())) {
                        if (commit) {
                            commit();
                            return 0;
                        }
                        else {
                            return resync();
                        }
                    }

                    mLastResync = System.currentTimeMillis();
                    return 0;
                }
            }

            // simple update
            int match = sUriMatcher.match(uri);
            switch (match) {
                case USERS:
                case USERS_JID:
                    return updateUser(values, Boolean.parseBoolean(uri
                        .getQueryParameter(Users.OFFLINE)), selection, selectionArgs);

                case KEYS:
                case KEYS_JID:
                case KEYS_JID_FINGERPRINT:
                    throw new IllegalArgumentException("use insert for keys");

                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }
        finally {
            invalidateFastScrollingIndexCache();
        }
    }

    private int updateUser(ContentValues values, boolean offline, String selection, String[] selectionArgs) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rc = db.update(offline ? TABLE_USERS_OFFLINE : TABLE_USERS, values, selection, selectionArgs);
        if (rc == 0) {
            ContentValues insertValues = new ContentValues(values);
            // insert new record
            insertValues.put(Users.JID, selectionArgs[0]);
            insertValues.put(Users.NUMBER, selectionArgs[0]);
            /*
            if (!values.containsKey(Users.DISPLAY_NAME))
                insertValues.put(Users.DISPLAY_NAME, selectionArgs[0]);
             */
            insertValues.put(Users.REGISTERED, true);

            try {
                db.insert(offline ? TABLE_USERS_OFFLINE : TABLE_USERS, null, insertValues);
                return 1;
            }
            catch (SQLiteConstraintException e) {
                // nothing was updated but the row exists
                return 0;
            }
        }

        return rc;
    }

    /** Commits the offline table to the online table. */
    private void commit() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // begin transaction
        beginTransaction(db);
        boolean success = false;

        try {
            // copy contents from offline
            db.execSQL("DELETE FROM " + TABLE_USERS);
            db.execSQL("INSERT INTO " + TABLE_USERS + " SELECT * FROM " + TABLE_USERS_OFFLINE);
            success = setTransactionSuccessful(db);
        }
        catch (SQLException e) {
            // ops :)
            Log.i(SyncAdapter.TAG, "users table commit failed - already committed?", e);
        }
        finally {
            endTransaction(db, success);
            // time to invalidate contacts cache
            Contact.invalidate();
        }
    }

    /** Triggers a complete resync of the users database. */
    private int resync() {
        Context context = getContext();
        ContentResolver cr = context.getContentResolver();
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // begin transaction
        beginTransaction(db);
        boolean success = false;

        int count = 0;

        // delete old users content
        try {
            db.execSQL("DELETE FROM " + TABLE_USERS_OFFLINE);
        }
        catch (SQLException e) {
            // table might not exist - create it! (shouldn't happen since version 4)
            db.execSQL(DatabaseHelper.SCHEMA_USERS_OFFLINE);
        }

        // we are trying to be fast here
        SQLiteStatement stm = db.compileStatement("INSERT INTO " + TABLE_USERS_OFFLINE +
            " (number, jid, display_name, lookup_key, contact_id, registered)" +
            " VALUES(?, ?, ?, ?, ?, ?)");

        // these two statements are used to immediately update data in the online table
        // even if the data is dummy, it will be soon replaced by sync or by manual request
        SQLiteStatement onlineUpd = db.compileStatement("UPDATE " + TABLE_USERS +
            " SET number = ?, display_name = ?, lookup_key = ?, contact_id = ? WHERE jid = ?");
        SQLiteStatement onlineIns = db.compileStatement("INSERT INTO " + TABLE_USERS +
            " (number, jid, display_name, lookup_key, contact_id, registered)" +
            " VALUES(?, ?, ?, ?, ?, ?)");

        Cursor phones = null;
        String dialPrefix = Preferences.getDialPrefix();
        int dialPrefixLen = dialPrefix != null ? dialPrefix.length() : 0;

        try {
            String where = !Preferences.getSyncInvisibleContacts(context) ?
                ContactsContract.Contacts.IN_VISIBLE_GROUP + "=1 AND " :
                "";

            // query for phone numbers
            phones = cr.query(Phone.CONTENT_URI,
                new String[] { Phone.NUMBER, Phone.DISPLAY_NAME, Phone.LOOKUP_KEY, Phone.CONTACT_ID, RawContacts.ACCOUNT_TYPE },
                where + " (" +
                // this will filter out RawContacts from Kontalk
                RawContacts.ACCOUNT_TYPE + " IS NULL OR " +
                RawContacts.ACCOUNT_TYPE + " NOT IN (?, ?))",
                new String[] {
                    Authenticator.ACCOUNT_TYPE, Authenticator.ACCOUNT_TYPE_LEGACY
                }, null);

            if (phones != null) {
                while (phones.moveToNext()) {
                    String number = phones.getString(0);
                    String name = phones.getString(1);

                    // buggy provider - skip entry
                    if (name == null || number == null)
                        continue;

                    // remove dial prefix first
                    if (dialPrefix != null && number.startsWith(dialPrefix))
                        number = number.substring(dialPrefixLen);

                    // a phone number with less than 4 digits???
                    if (number.length() < 4)
                        continue;

                    // fix number
                    try {
                        number = NumberValidator.fixNumber(context, number,
                            Authenticator.getDefaultAccountName(context), 0);
                    }
                    catch (Exception e) {
                        Log.e(SyncAdapter.TAG, "unable to normalize number: " + number + " - skipping", e);
                        // skip number
                        continue;
                    }

                    try {
                        String hash = MessageUtils.sha1(number);
                        String lookupKey = phones.getString(2);
                        long contactId = phones.getLong(3);
                        String jid = XMPPUtils.createLocalJID(getContext(), hash);

                        addResyncContact(db, stm, onlineUpd, onlineIns,
                            number, jid, name,
                            lookupKey, contactId, false);
                        count++;
                    }
                    catch (IllegalArgumentException iae) {
                        Log.w(SyncAdapter.TAG, "doing sync with no server?");
                    }
                    catch (SQLiteConstraintException sqe) {
                        // skip duplicate number
                    }
                }

                phones.close();
            }
            else {
                Log.e(SyncAdapter.TAG, "query to contacts failed!");
            }

            if (Preferences.getSyncSIMContacts(getContext())) {
                // query for SIM contacts
                // column selection doesn't work because of a bug in Android
                // TODO this is a bit unclear...
                try {
                    phones = cr.query(Uri.parse("content://icc/adn/"),
                        null, null, null, null);
                }
                catch (Exception e) {
                    /*
                    On some phones:
                    java.lang.NullPointerException
                        at android.os.Parcel.readException(Parcel.java:1431)
                        at android.database.DatabaseUtils.readExceptionFromParcel(DatabaseUtils.java:185)
                        at android.database.DatabaseUtils.readExceptionFromParcel(DatabaseUtils.java:137)
                        at android.content.ContentProviderProxy.query(ContentProviderNative.java:366)
                        at android.content.ContentResolver.query(ContentResolver.java:372)
                        at android.content.ContentResolver.query(ContentResolver.java:315)
                     */
                    Log.w(SyncAdapter.TAG, "unable to retrieve SIM contacts", e);
                    phones = null;
                }

                if (phones != null) {
                    while (phones.moveToNext()) {
                        String name = phones.getString(phones.getColumnIndex("name"));
                        String number = phones.getString(phones.getColumnIndex("number"));
                        // buggy firmware - skip entry
                        if (name == null || number == null)
                            continue;

                        // remove dial prefix first
                        if (dialPrefix != null && number.startsWith(dialPrefix))
                            number = number.substring(dialPrefixLen);

                        // a phone number with less than 4 digits???
                        if (number.length() < 4)
                            continue;

                        // fix number
                        try {
                            number = NumberValidator.fixNumber(context, number,
                                    Authenticator.getDefaultAccountName(context), 0);
                        }
                        catch (Exception e) {
                            Log.e(SyncAdapter.TAG, "unable to normalize number: " + number + " - skipping", e);
                            // skip number
                            continue;
                        }

                        try {
                            String hash = MessageUtils.sha1(number);
                            String jid = XMPPUtils.createLocalJID(getContext(), hash);
                            long contactId = phones.getLong(phones.getColumnIndex(BaseColumns._ID));

                            addResyncContact(db, stm, onlineUpd, onlineIns,
                                number, jid, name,
                                null, contactId,
                                false);
                            count++;
                        }
                        catch (IllegalArgumentException iae) {
                            Log.w(SyncAdapter.TAG, "doing sync with no server?");
                        }
                        catch (SQLiteConstraintException sqe) {
                            // skip duplicate number
                        }
                    }
                }
            }

            // try to add account number with display name
            String ownNumber = Authenticator.getDefaultAccountName(getContext());
            if (ownNumber != null) {
                String ownName = Authenticator.getDefaultDisplayName(getContext());
                String fingerprint = null;
                byte[] publicKeyData = null;
                try {
                    PersonalKey myKey = Kontalk.get(getContext()).getPersonalKey();
                    if (myKey != null) {
                        fingerprint = myKey.getFingerprint();
                        publicKeyData = myKey.getEncodedPublicKeyRing();
                    }
                }
                catch (Exception e) {
                    Log.w(SyncAdapter.TAG, "unable to load personal key", e);
                }
                try {
                    String hash = MessageUtils.sha1(ownNumber);
                    String jid = XMPPUtils.createLocalJID(getContext(), hash);

                    addResyncContact(db, stm, onlineUpd, onlineIns,
                        ownNumber, jid, ownName,
                        null, null,
                        true);
                    insertOrUpdateKey(jid, fingerprint, publicKeyData, false);
                    count++;
                }
                catch (IllegalArgumentException iae) {
                    Log.w(SyncAdapter.TAG, "doing sync with no server?");
                }
                catch (SQLiteConstraintException sqe) {
                    // skip duplicate number
                }
            }

            success = setTransactionSuccessful(db);
        }
        finally {
            endTransaction(db, success);
            if (phones != null)
                phones.close();
            stm.close();

            // time to invalidate contacts cache (because of updates to online)
            Contact.invalidate();
        }
        return count;
    }

    private void addResyncContact(SQLiteDatabase db, SQLiteStatement stm, SQLiteStatement onlineUpd, SQLiteStatement onlineIns,
        String number, String jid, String displayName, String lookupKey,
        Long contactId, boolean registered) {

        int i = 0;

        stm.clearBindings();
        stm.bindString(++i, number);
        stm.bindString(++i, jid);
        if (displayName != null)
            stm.bindString(++i, displayName);
        else
            stm.bindNull(++i);
        if (lookupKey != null)
            stm.bindString(++i, lookupKey);
        else
            stm.bindNull(++i);
        if (contactId != null)
            stm.bindLong(++i, contactId);
        else
            stm.bindNull(++i);
        stm.bindLong(++i, registered ? 1 : 0);
        stm.executeInsert();

        // update online entry
        i = 0;
        onlineUpd.clearBindings();
        onlineUpd.bindString(++i, number);
        if (displayName != null)
            onlineUpd.bindString(++i, displayName);
        else
            onlineUpd.bindNull(++i);
        if (lookupKey != null)
            onlineUpd.bindString(++i, lookupKey);
        else
            onlineUpd.bindNull(++i);
        if (contactId != null)
            onlineUpd.bindLong(++i, contactId);
        else
            onlineUpd.bindNull(++i);
        onlineUpd.bindString(++i, jid);
        int rows = executeUpdateDelete(db, onlineUpd);

        // no contact found, insert a new dummy one
        if (rows <= 0) {
            i = 0;
            onlineIns.clearBindings();
            onlineIns.bindString(++i, number);
            onlineIns.bindString(++i, jid);
            if (displayName != null)
                onlineIns.bindString(++i, displayName);
            else
                onlineIns.bindNull(++i);
            if (lookupKey != null)
                onlineIns.bindString(++i, lookupKey);
            else
                onlineIns.bindNull(++i);
            if (contactId != null)
                onlineIns.bindLong(++i, contactId);
            else
                onlineIns.bindNull(++i);
            onlineIns.bindLong(++i, registered ? 1 : 0);
            onlineIns.executeInsert();
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        try {
            int match = sUriMatcher.match(uri);
            switch (match) {
                case USERS:
                case USERS_JID:
                    return insertUser(values, Boolean.parseBoolean(uri
                        .getQueryParameter(Users.OFFLINE)), Boolean.parseBoolean(uri
                        .getQueryParameter(Users.DISCARD_NAME)));

                case KEYS:
                case KEYS_JID:
                case KEYS_JID_FINGERPRINT:
                    List<String> segs = uri.getPathSegments();
                    String jid, fingerprint;
                    if (segs.size() >= 2) {
                        // Uri-based insert/update
                        jid = segs.get(1);
                        fingerprint = segs.get(2);
                    }
                    else {
                        // take jid and fingerprint from values
                        jid = values.getAsString(Keys.JID);
                        fingerprint = values.getAsString(Keys.FINGERPRINT);
                    }

                    return insertOrUpdateKey(jid, fingerprint, values,
                        Boolean.parseBoolean(uri.getQueryParameter(Keys.INSERT_ONLY)));

                default:
                    throw new IllegalArgumentException("Unknown URI " + uri);
            }
        }
        finally {
            invalidateFastScrollingIndexCache();
        }
    }

    private Uri insertUser(ContentValues values, boolean offline, boolean discardName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String table = offline ? TABLE_USERS_OFFLINE : TABLE_USERS;
        long id = 0;

        try {
            id = db.insertOrThrow(table, null, values);
        }
        catch (SQLException e) {
            String jid = values.getAsString(Users.JID);
            if (jid != null) {
                // discard display_name if requested
                if (discardName) {
                    values.remove(Users.DISPLAY_NAME);
                    values.remove(Users.NUMBER);
                }

                db.update(table, values, Users.JID + "=?", new String[] { jid });
            }
        }

        if (id >= 0)
            return ContentUris.withAppendedId(Users.CONTENT_URI, id);
        return null;
    }

    private Uri insertOrUpdateKey(String jid, String fingerprint, byte[] keyData, boolean insertOnly) {
        if (jid == null || fingerprint == null)
            throw new IllegalArgumentException("either JID or fingerprint not provided");

        ContentValues values = new ContentValues(1);
        values.put(Keys.PUBLIC_KEY, keyData);
        return insertOrUpdateKey(jid, fingerprint, values, insertOnly);
    }

    private Uri insertOrUpdateKey(String jid, String fingerprint, ContentValues values, boolean insertOnly) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if (jid == null || fingerprint == null)
            throw new IllegalArgumentException("either JID or fingerprint not provided");

        int rows = 0;

        try {
            // try to insert the key with the provided values
            ContentValues insertValues = new ContentValues(values);
            insertValues.put(Keys.JID, jid);
            insertValues.put(Keys.FINGERPRINT, fingerprint);
            // use current timestamp if the caller didn't provide any
            long timestamp = values.containsKey(Keys.TIMESTAMP) ?
                values.getAsLong(Keys.TIMESTAMP) : System.currentTimeMillis();
            insertValues.put(Keys.TIMESTAMP, timestamp);
            db.insertOrThrow(TABLE_KEYS, null, insertValues);
            rows = 1;
        }
        catch (SQLiteConstraintException e) {
            if (!insertOnly) {
                // we got a duplicated key, update the requested values
                rows = db.update(TABLE_KEYS, values,
                    Keys.JID + "=? AND " + Keys.FINGERPRINT + "=?",
                    new String[]{ jid, fingerprint });
            }
        }

        if (rows >= 0)
            return Keys.CONTENT_URI.buildUpon()
                    .appendPath(jid)
                    .appendPath(fingerprint)
                    .build();
        return null;
    }

    private int insertKeys(ContentValues[] values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int rows = 0;
        SQLiteStatement stm = db.compileStatement("INSERT OR REPLACE INTO " +
            TABLE_KEYS + " (" + Keys.JID + ", " + Keys.FINGERPRINT + ") VALUES(?, ?)");

        for (ContentValues v : values) {
            try {
                stm.bindString(1, v.getAsString(Keys.JID));
                stm.bindString(2, v.getAsString(Keys.FINGERPRINT));
                stm.executeInsert();
                rows++;
            }
            catch (SQLException e) {
                Log.w(SyncAdapter.TAG, "error inserting trusted key [" + v + "]", e);
            }
        }

        return rows;
    }

    @Override
    public int bulkInsert(@NonNull Uri uri, @NonNull ContentValues[] values) {
        int match = sUriMatcher.match(uri);
        switch (match) {
            case KEYS:
                return insertKeys(values);

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        throw new SQLException("delete not supported.");
    }

    // avoid recreating the same object over and over
    private static ContentValues registeredValues;

    /** Marks a user as registered. */
    public static void markRegistered(Context context, String jid) {
        if (registeredValues == null) {
            registeredValues = new ContentValues(1);
            registeredValues.put(Users.REGISTERED, 1);
        }
        // TODO Uri.withAppendedPath(Users.CONTENT_URI, msg.getSender(true))
        context.getContentResolver().update(Users.CONTENT_URI,
            registeredValues, Users.JID+"=?", new String[] { jid });
    }

    /** Retrieves the last seen timestamp for a user. */
    public static long getLastSeen(Context context, String jid) {
        long timestamp = -1;
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Users.CONTENT_URI.buildUpon()
            .appendPath(jid).build(), new String[] { Users.LAST_SEEN },
            null, null, null);

        if (c.moveToFirst())
            timestamp = c.getLong(0);

        c.close();

        return timestamp;
    }

    /** Sets the last seen timestamp for a user. */
    public static void setLastSeen(Context context, String jid, long time) {
        ContentValues values = new ContentValues(1);
        values.put(Users.LAST_SEEN, time);
        context.getContentResolver().update(Users.CONTENT_URI,
            values, Users.JID + "=?", new String[] { jid });
    }

    public static void setBlockStatus(Context context, String jid, boolean blocked) {
        ContentValues values = new ContentValues(1);
        values.put(Users.BLOCKED, blocked);
        context.getContentResolver().update(Users.CONTENT_URI,
            values, Users.JID + "=?", new String[] { jid });
    }

    // FIXME what is this doing here? Using Messages Uri
    public static int setRequestStatus(Context context, String jid, int status) {
        ContentValues values = new ContentValues(1);
        values.put(MyMessages.Threads.REQUEST_STATUS, status);

        // FIXME this won't work on new threads
        return context.getContentResolver().update(MyMessages.Threads.Requests.CONTENT_URI,
            values, MyMessages.CommonColumns.PEER + "=?",
            new String[] { jid });
    }

    public static int updateDisplayNameIfEmpty(Context context, String jid, String displayName) {
        ContentValues values = new ContentValues(1);
        values.put(Users.DISPLAY_NAME, displayName);
        return context.getContentResolver().update(Users.CONTENT_URI,
            values, Users.JID + " = ? AND (" + Users.DISPLAY_NAME + " IS NULL OR LENGTH(" + Users.DISPLAY_NAME + ") = 0)",
            new String[] { jid });
    }

    public static int resync(Context context) {
        // update users database
        Uri uri = Users.CONTENT_URI.buildUpon()
            .appendQueryParameter(Users.RESYNC, "true")
            .build();
        return context.getContentResolver().update(uri, new ContentValues(), null, null);
    }

    /* Transactions compatibility layer */

    @TargetApi(android.os.Build.VERSION_CODES.HONEYCOMB)
    private void beginTransaction(SQLiteDatabase db) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
            db.beginTransactionNonExclusive();
        else
            // this is because API < 11 doesn't have beginTransactionNonExclusive()
            db.execSQL("BEGIN IMMEDIATE");
    }

    private boolean setTransactionSuccessful(SQLiteDatabase db) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
            db.setTransactionSuccessful();
        return true;
    }

    private void endTransaction(SQLiteDatabase db, boolean success) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
            db.endTransaction();
        else
            db.execSQL(success ? "COMMIT" : "ROLLBACK");
    }

    private int executeUpdateDelete(SQLiteDatabase db, SQLiteStatement stm) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
            return stm.executeUpdateDelete();
        }
        else {
            stm.execute();
            SQLiteStatement changes = db.compileStatement("SELECT changes()");
            try {
                return (int) changes.simpleQueryForLong();
            }
            finally {
                changes.close();
            }
        }
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_USERS, USERS);
        sUriMatcher.addURI(AUTHORITY, TABLE_USERS + "/*", USERS_JID);
        sUriMatcher.addURI(AUTHORITY, TABLE_KEYS, KEYS);
        sUriMatcher.addURI(AUTHORITY, TABLE_KEYS + "/*", KEYS_JID);
        sUriMatcher.addURI(AUTHORITY, TABLE_KEYS + "/*/*", KEYS_JID_FINGERPRINT);

        usersProjectionMap = new HashMap<>();
        usersProjectionMap.put(Users._ID, Users._ID);
        usersProjectionMap.put(Users.NUMBER, Users.NUMBER);
        usersProjectionMap.put(Users.DISPLAY_NAME, Users.DISPLAY_NAME);
        usersProjectionMap.put(Users.JID, Users.JID);
        usersProjectionMap.put(Users.LOOKUP_KEY, Users.LOOKUP_KEY);
        usersProjectionMap.put(Users.CONTACT_ID, Users.CONTACT_ID);
        usersProjectionMap.put(Users.REGISTERED, Users.REGISTERED);
        usersProjectionMap.put(Users.STATUS, Users.STATUS);
        usersProjectionMap.put(Users.LAST_SEEN, Users.LAST_SEEN);
        usersProjectionMap.put(Users.BLOCKED, Users.BLOCKED);

        // only for direct access to the keys table (for optimization)
        keysProjectionMap = new HashMap<>();
        keysProjectionMap.put(Keys.JID, Keys.JID);
        keysProjectionMap.put(Keys.FINGERPRINT, Keys.FINGERPRINT);
        keysProjectionMap.put(Keys.PUBLIC_KEY, Keys.PUBLIC_KEY);
        keysProjectionMap.put(Keys.TIMESTAMP, Keys.TIMESTAMP);
        keysProjectionMap.put(Keys.TRUST_LEVEL, Keys.TRUST_LEVEL);
    }

}

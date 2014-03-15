/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PGPCoder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MessageUtils;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

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
import android.provider.BaseColumns;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;


public class UsersProvider extends ContentProvider {
    private static final String TAG = UsersProvider.class.getSimpleName();
    public static final String AUTHORITY = "org.kontalk.users";

    private static final int DATABASE_VERSION = 5;
    private static final String DATABASE_NAME = "users.db";
    private static final String TABLE_USERS = "users";
    private static final String TABLE_USERS_OFFLINE = "users_offline";

    private static final int USERS = 1;
    private static final int USERS_HASH = 2;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> usersProjectionMap;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String CREATE_TABLE_USERS = "(" +
            "_id INTEGER PRIMARY KEY," +
            "hash TEXT NOT NULL UNIQUE," +
            "number TEXT NOT NULL UNIQUE," +
            "display_name TEXT," +
            "lookup_key TEXT," +
            "contact_id INTEGER," +
            "registered INTEGER NOT NULL DEFAULT 0," +
            "status TEXT," +
            "last_seen INTEGER," +
            "public_key BLOB," +
            "fingerprint TEXT" +
            ")";

        /** This table will contain all the users in contact list .*/
        private static final String SCHEMA_USERS =
            "CREATE TABLE " + TABLE_USERS + " " + CREATE_TABLE_USERS;

        private static final String SCHEMA_USERS_OFFLINE =
            "CREATE TABLE " + TABLE_USERS_OFFLINE + CREATE_TABLE_USERS;

        // version 2 - just replace the table
        private static final String[] SCHEMA_V1_TO_V2 = {
            "DROP TABLE IF EXISTS " + TABLE_USERS,
            SCHEMA_USERS
        };
        // version 3 - add status column
        private static final String[] SCHEMA_V2_TO_V3 = {
            "ALTER TABLE " + TABLE_USERS + " ADD COLUMN status TEXT"
        };
        // version 4 - create users_offline
        private static final String[] SCHEMA_V3_TO_V4 = {
            "DROP TABLE IF EXISTS " + TABLE_USERS_OFFLINE,
            SCHEMA_USERS_OFFLINE
        };
        // version 5 - add public_key and fingerprint columns
        private static final String[] SCHEMA_V4_TO_V5 = {
            "ALTER TABLE " + TABLE_USERS + " ADD COLUMN public_key BLOB",
            "ALTER TABLE " + TABLE_USERS + " ADD COLUMN fingerprint TEXT",
            "ALTER TABLE " + TABLE_USERS_OFFLINE + " ADD COLUMN public_key BLOB",
            "ALTER TABLE " + TABLE_USERS_OFFLINE + " ADD COLUMN fingerprint TEXT",
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
            mNew = true;
        }

        /** TODO simplify upgrade process based on org.kontalk database schema */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1) {
                for (String sql : SCHEMA_V1_TO_V2)
                    db.execSQL(sql);
                mNew = true;
            }
            else if (oldVersion == 2) {
                for (String sql : SCHEMA_V2_TO_V3)
                    db.execSQL(sql);
                // upgrade for versions 4 and 5 too
                for (String sql : SCHEMA_V3_TO_V4)
                    db.execSQL(sql);
                for (String sql : SCHEMA_V4_TO_V5)
                    db.execSQL(sql);
            }
            else if (oldVersion == 3) {
                for (String sql : SCHEMA_V3_TO_V4)
                    db.execSQL(sql);
                // upgrade for version 4 too
                for (String sql : SCHEMA_V4_TO_V5)
                    db.execSQL(sql);
            }
            else if (oldVersion == 4) {
                for (String sql : SCHEMA_V4_TO_V5)
                    db.execSQL(sql);
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
        return true;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case USERS:
                return Users.CONTENT_TYPE;
            case USERS_HASH:
                return Users.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        boolean offline = Boolean.parseBoolean(uri.getQueryParameter(Users.OFFLINE));
        String userId = null;

        switch (sUriMatcher.match(uri)) {
            case USERS:
                qb.setTables(offline ? TABLE_USERS_OFFLINE : TABLE_USERS);
                qb.setProjectionMap(usersProjectionMap);
                break;

            case USERS_HASH:
                qb.setTables(offline ? TABLE_USERS_OFFLINE : TABLE_USERS);
                qb.setProjectionMap(usersProjectionMap);
                // TODO append to selection
                userId = uri.getPathSegments().get(1);
                selection = Users.HASH + " = ?";
                selectionArgs = new String[] { userId };
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (c.getCount() == 0) {
            // request sync
            SyncAdapter.requestSync(getContext(), false);
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
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        boolean isResync = Boolean.parseBoolean(uri.getQueryParameter(Users.RESYNC));
        boolean bootstrap = Boolean.parseBoolean(uri.getQueryParameter(Users.BOOTSTRAP));
        boolean commit = Boolean.parseBoolean(uri.getQueryParameter(Users.COMMIT));

        if (isResync) {
            if (bootstrap ? dbHelper.isNew() : true)
                return resync(commit);
            return 0;
        }

        // simple update
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        boolean offline = Boolean.parseBoolean(uri.getQueryParameter(Users.OFFLINE));
        return db.update(offline ? TABLE_USERS_OFFLINE : TABLE_USERS, values, selection, selectionArgs);
    }

    /** Triggers a complete resync of the users database. */
    private int resync(boolean commit) {
        Context context = getContext();
        ContentResolver cr = context.getContentResolver();
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // begin transaction
        beginTransaction(db);
        boolean success = false;

        if (commit) {
            try {
                // copy contents from offline
                db.execSQL("DELETE FROM " + TABLE_USERS);
                db.execSQL("INSERT INTO " + TABLE_USERS + " SELECT * FROM " + TABLE_USERS_OFFLINE);
                // time to invalidate contacts cache
                Contact.invalidate();
                success = setTransactionSuccessful(db);
            }
            catch (SQLException e) {
                // ops :)
                Log.i(TAG, "users table commit failed - already committed?", e);
            }
            finally {
                endTransaction(db, success);
            }

            return 0;
        }
        else {
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
                " (hash, number, display_name, lookup_key, contact_id) VALUES(?, ?, ?, ?, ?)");

            Cursor phones = null;
            String dialPrefix = MessagingPreferences.getDialPrefix(context);
            int dialPrefixLen = dialPrefix != null ? dialPrefix.length() : 0;

            try {
                // query for phone numbers
            	// FIXME this might return null on some devices
                phones = cr.query(Phone.CONTENT_URI,
                    new String[] { Phone.NUMBER, Phone.DISPLAY_NAME, Phone.LOOKUP_KEY, Phone.CONTACT_ID, RawContacts.ACCOUNT_TYPE },
                    // this will filter out RawContacts from Kontalk
                    RawContacts.ACCOUNT_TYPE + " IS NULL OR " +
                    RawContacts.ACCOUNT_TYPE + "<> ?",
                    new String[] { Authenticator.ACCOUNT_TYPE }, null);

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
                        Log.e(TAG, "unable to normalize number: " + number + " - skipping", e);
                        // skip number
                        continue;
                    }

                    try {
                        String hash = MessageUtils.sha1(number);

                        stm.clearBindings();
                        stm.bindString(1, hash);
                        stm.bindString(2, number);
                        stm.bindString(3, name);
                        stm.bindString(4, phones.getString(2));
                        stm.bindLong(5, phones.getLong(3));
                        stm.executeInsert();
                        count++;
                    }
                    catch (SQLiteConstraintException sqe) {
                        // skip duplicate number
                    }
                }

                phones.close();

                if (MessagingPreferences.getSyncSIMContacts(getContext())) {
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
                        Log.w(TAG, "unable to retrieve SIM contacts", e);
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
                                Log.e(TAG, "unable to normalize number: " + number + " - skipping", e);
                                // skip number
                                continue;
                            }

                            try {
                                String hash = MessageUtils.sha1(number);

                                stm.clearBindings();
                                stm.bindString(1, hash);
                                stm.bindString(2, number);
                                stm.bindString(3, name);
                                stm.bindNull(4);
                                stm.bindLong(5, phones.getLong(phones.getColumnIndex(BaseColumns._ID)));
                                stm.executeInsert();
                                count++;
                            }
                            catch (SQLiteConstraintException sqe) {
                                // skip duplicate number
                            }
                        }
                    }
                }

                success = setTransactionSuccessful(db);
            }
            finally {
                endTransaction(db, success);
                if (phones != null)
                    phones.close();
                stm.close();
            }
            return count;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        boolean offline = Boolean.parseBoolean(uri.getQueryParameter(Users.OFFLINE));

        String table = offline ? TABLE_USERS_OFFLINE : TABLE_USERS;
        long id = 0;

        try {
	        id = db.insertOrThrow(table, null, values);
        }
        catch (SQLException e) {
        	String hash = values.getAsString(Users.HASH);
        	if (hash != null) {
        		// discard display_name if requested
        		boolean discardName = Boolean.parseBoolean(uri
        				.getQueryParameter(Users.DISCARD_NAME));
        		if (discardName)
        			values.remove(Users.DISPLAY_NAME);

        		db.update(table, values, "hash=?", new String[] { hash });
        	}
        }

        if (id >= 0)
            return ContentUris.withAppendedId(Users.CONTENT_URI, id);
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new SQLException("manual delete from users table not supported.");
    }

    // avoid recreating the same object over and over
    private static ContentValues registeredValues;

    /** Marks a user as registered. */
    public static void markRegistered(Context context, String userId) {
        if (registeredValues == null) {
            registeredValues = new ContentValues(1);
            registeredValues.put(Users.REGISTERED, 1);
        }
        // TODO Uri.withAppendedPath(Users.CONTENT_URI, msg.getSender(true))
        context.getContentResolver().update(Users.CONTENT_URI, registeredValues,
            Users.HASH + "=?", new String[] { userId });
    }

    /** Returns a {@link Coder} instance for encrypting data. */
    public static Coder getEncryptCoder(Context context, EndpointServer server, PersonalKey key, String[] recipients) {
        // get recipients public keys from users database
        PGPPublicKey keys[] = new PGPPublicKey[recipients.length];
        for (int i = 0; i < recipients.length; i++) {
            String rcpt = StringUtils.parseName(recipients[i]);

            PGPPublicKeyRing ring = getPublicKey(context, rcpt);
            if (ring == null)
                throw new IllegalArgumentException("public key not found for user " + rcpt);

        	keys[i] = PGP.getEncryptionKey(ring);
        	if (keys[i] == null)
                throw new IllegalArgumentException("public key not found for user " + rcpt);
        }

        return new PGPCoder(server, key, keys);
    }

    /** Returns a {@link Coder} instance for decrypting data. */
    public static Coder getDecryptCoder(Context context, EndpointServer server, PersonalKey key, String sender) {
        String rcpt = StringUtils.parseName(sender);

        PGPPublicKeyRing ring = getPublicKey(context, rcpt);
        if (ring == null)
            throw new IllegalArgumentException("public key not found for user " + rcpt);

    	PGPPublicKey senderKey = PGP.getMasterKey(ring);
    	if (senderKey == null)
            throw new IllegalArgumentException("public key not found for user " + rcpt);

        return new PGPCoder(server, key, senderKey);
    }

    /** Retrieves the public key for a user. */
    public static PGPPublicKeyRing getPublicKey(Context context, String userId) {
        byte[] keydata = null;
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Users.CONTENT_URI,
                new String[] { Users.PUBLIC_KEY },
                Users.HASH + "=?",
                new String[] { userId },
                null);

        if (c.moveToFirst())
            keydata = c.getBlob(0);

        c.close();

        try {
            return PGP.readPublicKeyring(keydata);
        }
        catch (Exception e) {
            // ignored
        }

        return null;
    }

    /** Updates a user public key. */
    public static void setUserKey(Context context, String userId, byte[] keydata, String fingerprint) {
        ContentValues values = new ContentValues(1);
        values.put(Users.PUBLIC_KEY, keydata);
        values.put(Users.FINGERPRINT, fingerprint);
        context.getContentResolver().update(Users.CONTENT_URI, values,
            Users.HASH + "=?", new String[] { userId });
    }

    /* Transactions compatibility layer */

    @TargetApi(11)
    private void beginTransaction(SQLiteDatabase db) {
        if (android.os.Build.VERSION.SDK_INT >= 11)
            db.beginTransactionNonExclusive();
        else
            // this is because API < 11 doesn't have beginTransactionNonExclusive()
            db.execSQL("BEGIN IMMEDIATE");
    }

    private boolean setTransactionSuccessful(SQLiteDatabase db) {
        if (android.os.Build.VERSION.SDK_INT >= 11)
            db.setTransactionSuccessful();
        return true;
    }

    private void endTransaction(SQLiteDatabase db, boolean success) {
        if (android.os.Build.VERSION.SDK_INT >= 11)
            db.endTransaction();
        else
            db.execSQL(success ? "COMMIT" : "ROLLBACK");
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_USERS, USERS);
        sUriMatcher.addURI(AUTHORITY, TABLE_USERS + "/*", USERS_HASH);

        usersProjectionMap = new HashMap<String, String>();
        usersProjectionMap.put(Users._ID, Users._ID);
        usersProjectionMap.put(Users.HASH, Users.HASH);
        usersProjectionMap.put(Users.NUMBER, Users.NUMBER);
        usersProjectionMap.put(Users.DISPLAY_NAME, Users.DISPLAY_NAME);
        usersProjectionMap.put(Users.LOOKUP_KEY, Users.LOOKUP_KEY);
        usersProjectionMap.put(Users.CONTACT_ID, Users.CONTACT_ID);
        usersProjectionMap.put(Users.REGISTERED, Users.REGISTERED);
        usersProjectionMap.put(Users.STATUS, Users.STATUS);
        usersProjectionMap.put(Users.LAST_SEEN, Users.LAST_SEEN);
        usersProjectionMap.put(Users.PUBLIC_KEY, Users.PUBLIC_KEY);
        usersProjectionMap.put(Users.FINGERPRINT, Users.FINGERPRINT);
    }

}

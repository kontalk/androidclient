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

package org.kontalk.provider;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.NumberValidator;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.util.MessageUtils;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;


public class UsersProvider extends ContentProvider {
    private static final String TAG = UsersProvider.class.getSimpleName();
    public static final String AUTHORITY = "org.kontalk.users";

    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "users.db";
    private static final String TABLE_USERS = "users";

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
            "last_seen INTEGER" +
            ")";

        /** This table will contain all the users in contact list .*/
        private static final String SCHEMA_USERS =
            "CREATE TABLE " + TABLE_USERS + " " + CREATE_TABLE_USERS;

        private static final String SCHEMA_USERS_TEMP =
            "CREATE TEMPORARY TABLE " + TABLE_USERS + "_TEMP AS SELECT * FROM " + TABLE_USERS;

        // version 2 - just replace the table
        private static final String[] SCHEMA_V1_TO_V2 = {
            "DROP TABLE IF EXISTS " + TABLE_USERS,
            SCHEMA_USERS
        };

        /** This will be set to true when database is new. */
        private boolean mNew;

        protected DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_USERS);
            mNew = true;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1) {
                for (String sql : SCHEMA_V1_TO_V2)
                    db.execSQL(sql);
                mNew = true;
            }
        }

        public boolean isNew() {
            return mNew;
        }

        public void mirror(SQLiteDatabase db) {
            db.execSQL(SCHEMA_USERS_TEMP);
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

        switch (sUriMatcher.match(uri)) {
            case USERS:
                qb.setTables(TABLE_USERS);
                qb.setProjectionMap(usersProjectionMap);
                break;

            case USERS_HASH:
                qb.setTables(TABLE_USERS);
                qb.setProjectionMap(usersProjectionMap);
                // TODO append to selection
                selection = Users.HASH + " = ?";
                selectionArgs = new String[] { uri.getPathSegments().get(1) };
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        boolean isResync = Boolean.parseBoolean(uri.getQueryParameter(Users.RESYNC));
        boolean bootstrap = Boolean.parseBoolean(uri.getQueryParameter(Users.BOOTSTRAP));

        if (isResync) {
            if (bootstrap ? dbHelper.isNew() : true)
                return resync();
            return 0;
        }

        // simple update
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.update(TABLE_USERS, values, selection, selectionArgs);
    }

    /** Triggers a complete resync of the users database. */
    private int resync() {
        Context context = getContext();
        ContentResolver cr = context.getContentResolver();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = 0;

        // query for phone numbers
        final Cursor phones = cr.query(Phone.CONTENT_URI,
            new String[] { Phone.NUMBER, Phone.DISPLAY_NAME, Phone.LOOKUP_KEY, Phone.CONTACT_ID },
            null, null, null);

        // begin transaction
        if (android.os.Build.VERSION.SDK_INT >= 11)
            db.beginTransactionNonExclusive();
        else
            // this is because API < 11 doesn't have beginTransactionNonExclusive()
            db.execSQL("BEGIN IMMEDIATE");

        // create a temporary copy of the users table
        // TODO are we sure this is the best choice for performance?
        dbHelper.mirror(db);

        // delete old users
        db.execSQL("DELETE FROM " + TABLE_USERS);

        // we are trying to be fast here
        SQLiteStatement stm = db.compileStatement("INSERT INTO " + TABLE_USERS +
            " (hash, number, display_name, lookup_key, contact_id, registered) VALUES(?, ?, ?, ?, ?, ?)");
        SQLiteStatement select = db.compileStatement("SELECT registered FROM " + TABLE_USERS + "_TEMP WHERE hash = ?");

        try {
            while (phones.moveToNext()) {
                String number = phones.getString(0);

                // a phone number with less than 4 digits???
                if (number.length() < 4)
                    continue;

                // fix number
                try {
                	number = NumberValidator.fixNumber(context, number,
                	        Authenticator.getDefaultAccountName(context));
                }
                catch (Exception e) {
                	Log.e(TAG, "unable to normalize number: " + number + " - skipping", e);
                	// skip number
                	continue;
                }

                try {
                    String hash = MessageUtils.sha1(number);

                    // retrieve old registered value
                    select.clearBindings();
                    select.bindString(1, hash);
                    long registered;
                    try {
                        registered = select.simpleQueryForLong();
                    }
                    catch (Exception e) {
                        registered = 0;
                    }

                    stm.clearBindings();
                    stm.bindString(1, hash);
                    stm.bindString(2, number);
                    stm.bindString(3, phones.getString(1));
                    stm.bindString(4, phones.getString(2));
                    stm.bindLong(5, phones.getLong(3));
                    stm.bindLong(6, registered);
                    stm.executeInsert();
                    count++;
                }
                catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "unable to generate SHA-1 hash for " + number + " - skipping", e);
                }
                catch (SQLiteConstraintException sqe) {
                    // skip duplicate number
                }
            }

            db.execSQL("DROP TABLE " + TABLE_USERS + "_TEMP");

            if (android.os.Build.VERSION.SDK_INT >= 11)
                db.setTransactionSuccessful();
        }
        finally {
            // commit!
            if (android.os.Build.VERSION.SDK_INT >= 11)
                db.endTransaction();
            else
                db.execSQL("COMMIT");
            phones.close();
            stm.close();
            select.close();
        }
        return count;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO
        return 0;
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
        usersProjectionMap.put(Users.LAST_SEEN, Users.LAST_SEEN);
    }

}

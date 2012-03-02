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

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "users.db";
    private static final String TABLE_USERS = "users";

    private static final int USERS = 1;
    private static final int USERS_HASH = 2;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> usersProjectionMap;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        /** This table will contain all the users in contact list .*/
        private static final String SCHEMA_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
            "hash TEXT PRIMARY KEY, " +
            "number TEXT NOT NULL UNIQUE," +
            "lookup_key TEXT" +
            ");";

        /** This will be set to true when database is new. */
        private boolean mNew;

        protected DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
            mNew = !context.getDatabasePath(DATABASE_NAME).isFile();
        }

        @Override
        public void onOpen(SQLiteDatabase db) {
            // reset new flag since we are opening the database
            mNew = false;
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_USERS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // no need to update - version 1 :)
        }

        public boolean isNew() {
            return mNew;
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

        if (isResync && (bootstrap ? dbHelper.isNew() : true))
            return resync();

        // TODO
        return 0;
    }

    /** Triggers a complete resync of the users database. */
    private int resync() {
        Context context = getContext();
        ContentResolver cr = context.getContentResolver();
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int count = 0;

        // query for phone numbers
        final Cursor phones = cr.query(Phone.CONTENT_URI,
            new String[] { Phone.NUMBER, Phone.LOOKUP_KEY },
            null, null, null);

        // begin transaction
        if (android.os.Build.VERSION.SDK_INT >= 11)
            db.beginTransactionNonExclusive();
        else
            // this is because API < 11 doesn't have beginTransactionNonExclusive()
            db.execSQL("BEGIN IMMEDIATE;");

        // we are trying to be fast here
        SQLiteStatement stm = db.compileStatement("REPLACE INTO " + TABLE_USERS + " VALUES(?, ?, ?)");

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

                    stm.bindString(1, hash);
                    stm.bindString(2, number);
                    stm.bindString(3, phones.getString(1));
                    stm.executeInsert();
                    stm.clearBindings();
                    count++;
                }
                catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "unable to generate SHA-1 hash for " + number + " - skipping", e);
                }
            }

            if (android.os.Build.VERSION.SDK_INT >= 11)
                db.setTransactionSuccessful();
        }
        finally {
            // commit!
            if (android.os.Build.VERSION.SDK_INT >= 11)
                db.endTransaction();
            else
                db.execSQL("COMMIT;");
            phones.close();
            stm.close();
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
        usersProjectionMap.put(Users.HASH, Users.HASH);
        usersProjectionMap.put(Users.NUMBER, Users.NUMBER);
        usersProjectionMap.put(Users.LOOKUP_KEY, Users.LOOKUP_KEY);
    }

}

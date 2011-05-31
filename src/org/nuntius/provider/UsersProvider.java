package org.nuntius.provider;

import java.util.HashMap;

import org.nuntius.provider.MyUsers.Users;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

public class UsersProvider extends ContentProvider {
    private static final String TAG = MessagesProvider.class.getSimpleName();
    public static final String AUTHORITY = "org.nuntius.users";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "users.db";
    private static final String TABLE_USERS = "users";

    private static final int USERS = 1;
    private static final int USERS_HASH = 2;
    private static final int USERS_NUMBER = 3;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> usersProjectionMap;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String SCHEMA_USERS =
            "CREATE TABLE " + TABLE_USERS + " (" +
            "hash TEXT PRIMARY KEY, " +
            "number TEXT NOT NULL UNIQUE" +
            ");";

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_USERS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // no upgrade for now (this is version 1 :)
        }
    }

    @Override
    public boolean onCreate() {
        dbHelper = new DatabaseHelper(getContext());
        return true;
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
                qb.appendWhere(Users.HASH + "='" + uri.getPathSegments().get(1) + "'");
                break;

            case USERS_NUMBER:
                qb.setTables(TABLE_USERS);
                qb.setProjectionMap(usersProjectionMap);
                qb.appendWhere(Users.NUMBER + "='" + uri.getPathSegments().get(1) + "'");
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
    public Uri insert(Uri uri, ContentValues initialValues) {
        if (sUriMatcher.match(uri) != USERS) { throw new IllegalArgumentException("Unknown URI " + uri); }
        if (initialValues == null) { throw new IllegalArgumentException("No data"); }

        ContentValues values = new ContentValues(initialValues);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = db.insert(TABLE_USERS, null, values);

        if (rowId > 0) {
            Uri newUri = Users.getUri(values.getAsString(Users.HASH));
            getContext().getContentResolver().notifyChange(newUri, null);
            Log.w(TAG, "users table inserted, id = " + rowId);

            return newUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        // TODO update
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO update
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case USERS:
                return Users.CONTENT_TYPE;
            case USERS_HASH:
            case USERS_NUMBER:
                return Users.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_USERS, USERS);
        sUriMatcher.addURI(AUTHORITY, TABLE_USERS + "/*", USERS_HASH);
        sUriMatcher.addURI(AUTHORITY, TABLE_USERS + "/+#", USERS_NUMBER);

        usersProjectionMap = new HashMap<String, String>();
        //usersProjectionMap.put(Users._ID, Users._ID);
        usersProjectionMap.put(Users.HASH, Users.HASH);
        usersProjectionMap.put(Users.NUMBER, Users.NUMBER);
    }
}

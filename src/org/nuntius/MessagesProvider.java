package org.nuntius;

import java.util.HashMap;

import org.nuntius.provider.Messages;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;


/**
 * The message storage provider.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessagesProvider extends ContentProvider {

    public static final String AUTHORITY = "org.nuntius.MessageStorageProvider";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "messages.db";
    private static final String TABLE_MESSAGES = "messages";

    private static final int THREADS = 1;
    private static final int MESSAGES = 2;
    private static final int MESSAGES_ID = 3;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> messagesProjectionMap;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String SCHEMA_MESSAGES =
            "CREATE TABLE " + TABLE_MESSAGES + " (" +
            "_id TEXT NOT NULL PRIMARY KEY, " +
            "peer TEXT, " +
            "mime TEXT NOT NULL, " +
            "content TEXT" +
            ");";

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_MESSAGES);
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
            case MESSAGES:
                qb.setTables(TABLE_MESSAGES);
                qb.setProjectionMap(messagesProjectionMap);
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
        if (sUriMatcher.match(uri) != MESSAGES) { throw new IllegalArgumentException("Unknown URI " + uri); }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = db.insert(TABLE_MESSAGES, null, values);
        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(Messages.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
            String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MESSAGES:
                return Messages.CONTENT_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES, MESSAGES);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/#", MESSAGES_ID);
        sUriMatcher.addURI(AUTHORITY, "threads", THREADS);

        messagesProjectionMap = new HashMap<String, String>();
        messagesProjectionMap.put(Messages.Message.MESSAGE_ID, Messages.Message.MESSAGE_ID);
        messagesProjectionMap.put(Messages.Message.PEER, Messages.Message.PEER);
        messagesProjectionMap.put(Messages.Message.MIME, Messages.Message.MIME);
        messagesProjectionMap.put(Messages.Message.CONTENT, Messages.Message.CONTENT);
    }
}

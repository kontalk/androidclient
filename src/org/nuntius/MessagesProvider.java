package org.nuntius;

import java.util.HashMap;

import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.provider.MyMessages.Threads;

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


/**
 * The message storage provider.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessagesProvider extends ContentProvider {

    public static final String TAG = MessagesProvider.class.getSimpleName();
    public static final String AUTHORITY = "org.nuntius.MessagesProvider";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "messages.db";
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_THREADS = "threads";

    private static final int THREADS = 1;
    private static final int THREADS_PEER = 2;
    private static final int MESSAGES = 3;
    private static final int MESSAGES_ID = 4;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> messagesProjectionMap;
    private static HashMap<String, String> threadsProjectionMap;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        // this table will contain all the messages
        private static final String SCHEMA_MESSAGES =
            "CREATE TABLE " + TABLE_MESSAGES + " (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "msgid TEXT NOT NULL UNIQUE, " +
            "peer TEXT, " +
            "mime TEXT NOT NULL, " +
            "content TEXT" +
            ");";
        // this table will contain the latest message from each conversation
        private static final String SCHEMA_THREADS =
            "CREATE TABLE " + TABLE_THREADS + " (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "msgid TEXT NOT NULL UNIQUE, " +
            "peer TEXT NOT NULL UNIQUE, " +
            "direction INTEGER, " +
            "unread INTEGER, " +
            "mime TEXT NOT NULL, " +
            "content TEXT, " +
            "timestamp INTEGER" +
            ");";

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_MESSAGES);
            db.execSQL(SCHEMA_THREADS);
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

            case MESSAGES_ID:
                qb.setTables(TABLE_MESSAGES);
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(Messages.MESSAGE_ID + "='" + uri.getPathSegments().get(1) + "'");
                break;

            case THREADS:
                qb.setTables(TABLE_THREADS);
                qb.setProjectionMap(threadsProjectionMap);
                break;

            case THREADS_PEER:
                qb.setTables(TABLE_THREADS);
                qb.setProjectionMap(threadsProjectionMap);
                qb.appendWhere(Threads.PEER + "='" + uri.getPathSegments().get(1) + "'");
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
        if (initialValues == null) { throw new IllegalArgumentException("No data"); }

        ContentValues values = new ContentValues(initialValues);
        if (values.get(Threads.UNREAD) != null)
            values.remove(Threads.UNREAD);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = db.insert(TABLE_MESSAGES, null, values);

        if (rowId > 0) {
            Uri msgUri = Messages.getUri(values.getAsString(Messages.MESSAGE_ID));
            getContext().getContentResolver().notifyChange(msgUri, null);
            Log.w(TAG, "messages table inserted, id = " + rowId);

            updateThreads(db, initialValues);
            return msgUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * TODO needs some fixes
     * @param db
     * @param values
     * @return
     */
    private boolean updateThreads(SQLiteDatabase db, ContentValues values) {
        String peer = values.getAsString(Threads.PEER);

        // update threads table
        values.put(Threads.DIRECTION, new Integer(Threads.DIRECTION_IN));
        values.put(Threads.TIMESTAMP, System.currentTimeMillis());

        // try to insert
        try {
            db.insertOrThrow(TABLE_THREADS, null, values);
            Log.w(TAG, "threads table inserted");
        }
        catch (SQLException e) {
            db.update(TABLE_THREADS, values, "peer = ?", new String[] { peer });
            Log.w(TAG, "threads table updated");
        }

        // notify changes
        Uri threadUri = Threads.getUri(peer);
        getContext().getContentResolver().notifyChange(threadUri, null);
        return true;
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
            case MESSAGES:
                return Messages.CONTENT_TYPE;
            case MESSAGES_ID:
                return Messages.CONTENT_ITEM_TYPE;
            case THREADS:
                return Threads.CONTENT_TYPE;
            case THREADS_PEER:
                return Threads.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS, THREADS);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS + "/#", THREADS_PEER);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES, MESSAGES);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/#", MESSAGES_ID);

        messagesProjectionMap = new HashMap<String, String>();
        messagesProjectionMap.put(Messages._ID, Messages._ID);
        messagesProjectionMap.put(Messages.MESSAGE_ID, Messages.MESSAGE_ID);
        messagesProjectionMap.put(Messages.PEER, Messages.PEER);
        messagesProjectionMap.put(Messages.MIME, Messages.MIME);
        messagesProjectionMap.put(Messages.CONTENT, Messages.CONTENT);

        threadsProjectionMap = new HashMap<String, String>();
        threadsProjectionMap.put(Threads._ID, Threads._ID);
        threadsProjectionMap.put(Threads.MESSAGE_ID, Threads.MESSAGE_ID);
        threadsProjectionMap.put(Threads.PEER, Threads.PEER);
        threadsProjectionMap.put(Threads.DIRECTION, Threads.DIRECTION);
        threadsProjectionMap.put(Threads.UNREAD, Threads.UNREAD);
        threadsProjectionMap.put(Threads.MIME, Threads.MIME);
        threadsProjectionMap.put(Threads.CONTENT, Threads.CONTENT);
        threadsProjectionMap.put(Threads.TIMESTAMP, Threads.TIMESTAMP);
    }
}

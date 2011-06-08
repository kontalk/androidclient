package org.nuntius.provider;

import java.util.ArrayList;
import java.util.HashMap;

import org.nuntius.provider.MyMessages.Messages;
import org.nuntius.provider.MyMessages.Threads;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
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

    private static final String TAG = MessagesProvider.class.getSimpleName();
    public static final String AUTHORITY = "org.nuntius.messages";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "messages.db";
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_THREADS = "threads";

    private static final int THREADS = 1;
    private static final int THREADS_ID = 2;
    private static final int THREADS_PEER = 3;
    private static final int MESSAGES = 4;
    private static final int MESSAGES_ID = 5;
    private static final int MESSAGES_SERVERID = 6;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> messagesProjectionMap;
    private static HashMap<String, String> threadsProjectionMap;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        /** This table will contain all the messages .*/
        private static final String SCHEMA_MESSAGES =
            "CREATE TABLE " + TABLE_MESSAGES + " (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "thread_id INTEGER NOT NULL, " +
            "msg_id TEXT NOT NULL UNIQUE, " +
            "peer TEXT, " +
            "mime TEXT NOT NULL, " +
            "content TEXT," +
            "direction INTEGER, " +
            "unread INTEGER, " +
            "timestamp INTEGER," +
            "status INTEGER" +
            ");";

        /** This table will contain the latest message from each conversation. */
        private static final String SCHEMA_THREADS =
            "CREATE TABLE " + TABLE_THREADS + " (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "msg_id TEXT NOT NULL UNIQUE, " +
            "peer TEXT NOT NULL UNIQUE, " +
            "direction INTEGER, " +
            "count INTEGER, " +
            "unread INTEGER, " +
            "mime TEXT NOT NULL, " +
            "content TEXT, " +
            "timestamp INTEGER" +
            ");";

        /** Updates the thread messages count. */
        private static final String UPDATE_MESSAGES_COUNT_NEW =
            "UPDATE " + TABLE_THREADS + " SET count = (" +
            "SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = new.thread_id" +
            ") WHERE _id = new.thread_id";
        private static final String UPDATE_MESSAGES_COUNT_OLD =
            "UPDATE " + TABLE_THREADS + " SET count = (" +
            "SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = old.thread_id" +
            ") WHERE _id = old.thread_id";

        /** Updates the thread unread count. */
        private static final String UPDATE_UNREAD_COUNT_NEW =
            "UPDATE " + TABLE_THREADS + " SET unread = (" +
            "SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = new.thread_id " +
            "AND unread <> 0) WHERE _id = new.thread_id";
        private static final String UPDATE_UNREAD_COUNT_OLD =
            "UPDATE " + TABLE_THREADS + " SET unread = (" +
            "SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = old.thread_id " +
            "AND unread <> 0) WHERE _id = old.thread_id";

        /** This trigger will update the threads table counters on INSERT. */
        private static final String TRIGGER_THREADS_INSERT_COUNT =
            "CREATE TRIGGER update_thread_on_insert AFTER INSERT ON " + TABLE_MESSAGES +
            " BEGIN " + UPDATE_MESSAGES_COUNT_NEW + ";" + UPDATE_UNREAD_COUNT_NEW + "; END;";

        /** This trigger will update the threads table counters on DELETE. */
        private static final String TRIGGER_THREADS_DELETE_COUNT =
            "CREATE TRIGGER update_thread_on_delete AFTER DELETE ON " + TABLE_MESSAGES +
            " BEGIN " + UPDATE_MESSAGES_COUNT_OLD + "; " + UPDATE_UNREAD_COUNT_OLD + "; END;";

        // TODO UPDATE triggers

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_MESSAGES);
            db.execSQL(SCHEMA_THREADS);
            db.execSQL(TRIGGER_THREADS_INSERT_COUNT);
            db.execSQL(TRIGGER_THREADS_DELETE_COUNT);
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
                qb.appendWhere(Messages._ID + "=" + uri.getPathSegments().get(1));
                break;

            case MESSAGES_SERVERID:
                qb.setTables(TABLE_MESSAGES);
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(Messages.MESSAGE_ID + "='" + DatabaseUtils.sqlEscapeString(uri.getPathSegments().get(1)) + "'");
                break;

            case THREADS:
                qb.setTables(TABLE_THREADS);
                qb.setProjectionMap(threadsProjectionMap);
                break;

            case THREADS_ID:
                qb.setTables(TABLE_THREADS);
                qb.setProjectionMap(threadsProjectionMap);
                qb.appendWhere(Threads._ID + "=" + uri.getPathSegments().get(1));
                break;

            case THREADS_PEER:
                qb.setTables(TABLE_THREADS);
                qb.setProjectionMap(threadsProjectionMap);
                qb.appendWhere(Threads.PEER + "='" + DatabaseUtils.sqlEscapeString(uri.getPathSegments().get(1)) + "'");
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

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // create the thread first
        long threadId = updateThreads(db, values);
        values.put(Messages.THREAD_ID, threadId);

        // insert the new message now!
        long rowId = db.insert(TABLE_MESSAGES, null, values);

        if (rowId > 0) {
            Uri msgUri = ContentUris.withAppendedId(uri, rowId);
            getContext().getContentResolver().notifyChange(msgUri, null);
            Log.w(TAG, "messages table inserted, id = " + rowId);

            updateThreads(db, initialValues);
            db.close();
            return msgUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Updates the threads table, returning the thread id to associate with the new message.
     * @param db
     * @param values
     * @return the thread id
     */
    private long updateThreads(SQLiteDatabase db, ContentValues initialValues) {
        ContentValues values = new ContentValues(initialValues);
        String peer = values.getAsString(Threads.PEER);

        long threadId = 0;
        if (values.containsKey(Messages.THREAD_ID)) {
            threadId = values.getAsLong(Messages.THREAD_ID);
            values.remove(Messages.THREAD_ID);
        }

        // this will be recalculated by the trigger
        values.remove(Messages.UNREAD);
        values.remove(Messages.STATUS);

        // try to insert
        try {
            threadId = db.insertOrThrow(TABLE_THREADS, null, values);
            Log.w(TAG, "threads table inserted");
        }
        catch (SQLException e) {
            db.update(TABLE_THREADS, values, "peer = ?", new String[] { peer });
            Log.w(TAG, "threads table updated");

            // retrieve the thread id
            if (threadId <= 0) {
                Cursor c = db.query(TABLE_THREADS, new String[] { Threads._ID }, "peer = ?", new String[] { peer }, null, null, null);
                if (c.moveToFirst())
                    threadId = c.getLong(0);
                c.close();
            }
        }

        // notify changes
        Uri threadUri = ContentUris.withAppendedId(Threads.CONTENT_URI, threadId);
        getContext().getContentResolver().notifyChange(threadUri, null);
        return threadId;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (values == null) { throw new IllegalArgumentException("No data"); }

        String where;
        String[] args;

        switch (sUriMatcher.match(uri)) {
            case MESSAGES:
                where = selection;
                args = selectionArgs;
                break;

            case MESSAGES_ID:
                long _id = ContentUris.parseId(uri);
                where = "_id = ?";
                args = new String[] { String.valueOf(_id) };
                break;

            case MESSAGES_SERVERID:
                String sid = uri.getPathSegments().get(1);
                where = "msg_id = ?";
                args = new String[] { String.valueOf(sid) };
                break;

            // TODO cases for threads table

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.update(TABLE_MESSAGES, values, where, args);
        db.close();

        getContext().getContentResolver().notifyChange(uri, null);
        Log.w(TAG, "messages table updated, affected: " + rows);
        return rows;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String table;
        String where;
        String[] args;
        long _id;

        switch (sUriMatcher.match(uri)) {
            case MESSAGES:
                table = TABLE_MESSAGES;
                where = selection;
                args = selectionArgs;
                break;

            case MESSAGES_ID:
                table = TABLE_MESSAGES;
                _id = ContentUris.parseId(uri);
                where = "_id = ?";
                args = new String[] { String.valueOf(_id) };
                break;

            case MESSAGES_SERVERID:
                table = TABLE_MESSAGES;
                String sid = uri.getPathSegments().get(1);
                where = "msg_id = ?";
                args = new String[] { String.valueOf(sid) };
                break;

            case THREADS:
                table = TABLE_THREADS;
                where = selection;
                args = selectionArgs;
                break;

            case THREADS_ID:
                table = TABLE_THREADS;
                _id = ContentUris.parseId(uri);
                where = "_id = ?";
                args = new String[] { String.valueOf(_id) };
                break;

            // TODO cases for threads table

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete(table, where, args);

        getContext().getContentResolver().notifyChange(uri, null);
        Log.w(TAG, "table " + table + " deleted, affected: " + rows);

        // check for empty threads
        if (table.equals(TABLE_MESSAGES))
            deleteEmptyThreads(db);

        db.close();
        return rows;
    }

    private void deleteEmptyThreads(SQLiteDatabase db) {
        int rows = db.delete(TABLE_THREADS, "\"" + Threads.COUNT + "\"" + " = 0", null);
        Log.i(TAG, "checking for empty threads: " + rows);
        if (rows > 0)
            getContext().getContentResolver().notifyChange(Threads.CONTENT_URI, null);
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

    public static boolean deleteDatabase(Context ctx) {
        ContentResolver c = ctx.getContentResolver();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>(2);
        ContentProviderOperation.Builder b;
        b = ContentProviderOperation.newDelete(Messages.CONTENT_URI);
        ops.add(b.build());
        b = ContentProviderOperation.newDelete(Threads.CONTENT_URI);
        ops.add(b.build());

        try {
            c.applyBatch(AUTHORITY, ops);
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, "error during database delete!", e);
            return false;
        }
    }

    public static boolean deleteThread(Context ctx, long id) {
        ContentResolver c = ctx.getContentResolver();
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>(2);
        ContentProviderOperation.Builder b;
        b = ContentProviderOperation.newDelete(Messages.CONTENT_URI);
        b.withSelection(Messages.THREAD_ID + " = ?", new String[] { String.valueOf(id) });
        ops.add(b.build());
        b = ContentProviderOperation.newDelete(ContentUris.withAppendedId(Threads.CONTENT_URI, id));
        ops.add(b.build());

        try {
            c.applyBatch(AUTHORITY, ops);
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, "error during thread delete!", e);
            return false;
        }
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS, THREADS);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS + "/#", THREADS_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS + "/*", THREADS_PEER);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES, MESSAGES);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/#", MESSAGES_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/*", MESSAGES_SERVERID);

        messagesProjectionMap = new HashMap<String, String>();
        messagesProjectionMap.put(Messages._ID, Messages._ID);
        messagesProjectionMap.put(Messages.THREAD_ID, Messages.THREAD_ID);
        messagesProjectionMap.put(Messages.MESSAGE_ID, Messages.MESSAGE_ID);
        messagesProjectionMap.put(Messages.PEER, Messages.PEER);
        messagesProjectionMap.put(Messages.MIME, Messages.MIME);
        messagesProjectionMap.put(Messages.CONTENT, Messages.CONTENT);
        messagesProjectionMap.put(Messages.UNREAD, Messages.UNREAD);
        messagesProjectionMap.put(Messages.DIRECTION, Messages.DIRECTION);
        messagesProjectionMap.put(Messages.TIMESTAMP, Messages.TIMESTAMP);
        messagesProjectionMap.put(Messages.STATUS, Messages.STATUS);

        threadsProjectionMap = new HashMap<String, String>();
        threadsProjectionMap.put(Threads._ID, Threads._ID);
        threadsProjectionMap.put(Threads.MESSAGE_ID, Threads.MESSAGE_ID);
        threadsProjectionMap.put(Threads.PEER, Threads.PEER);
        threadsProjectionMap.put(Threads.DIRECTION, Threads.DIRECTION);
        threadsProjectionMap.put(Threads.COUNT, Threads.COUNT);
        threadsProjectionMap.put(Threads.UNREAD, Threads.UNREAD);
        threadsProjectionMap.put(Threads.MIME, Threads.MIME);
        threadsProjectionMap.put(Threads.CONTENT, Threads.CONTENT);
        threadsProjectionMap.put(Threads.TIMESTAMP, Threads.TIMESTAMP);
    }
}

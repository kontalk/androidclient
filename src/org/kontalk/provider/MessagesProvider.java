package org.kontalk.provider;

import java.util.ArrayList;
import java.util.HashMap;

import org.kontalk.R;
import org.kontalk.client.PlainTextMessage;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Messages.Fulltext;
import org.kontalk.provider.MyMessages.Threads.Conversations;


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
 * The messages storage provider.
 * @author Daniele Ricci
 */
public class MessagesProvider extends ContentProvider {

    private static final String TAG = MessagesProvider.class.getSimpleName();
    public static final String AUTHORITY = "org.kontalk.messages";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "messages.db";
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_FULLTEXT = "fulltext";
    private static final String TABLE_THREADS = "threads";

    private static final int THREADS = 1;
    private static final int THREADS_ID = 2;
    private static final int THREADS_PEER = 3;
    private static final int MESSAGES = 4;
    private static final int MESSAGES_ID = 5;
    private static final int MESSAGES_SERVERID = 6;
    private static final int CONVERSATIONS_ID = 7;
    private static final int FULLTEXT_ID = 8;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> messagesProjectionMap;
    private static HashMap<String, String> threadsProjectionMap;
    private static HashMap<String, String> fulltextProjectionMap;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        /** This table will contain all the messages .*/
        private static final String SCHEMA_MESSAGES =
            "CREATE TABLE " + TABLE_MESSAGES + " (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "thread_id INTEGER NOT NULL, " +
            "msg_id TEXT NOT NULL UNIQUE, " +
            "real_id TEXT, " +
            "peer TEXT, " +
            "mime TEXT NOT NULL, " +
            "content BLOB," +
            "direction INTEGER, " +
            "unread INTEGER, " +
            // this the sent/received timestamp
            "timestamp INTEGER," +
            // this the timestamp of the latest status change
            "status_changed INTEGER," +
            "status INTEGER," +
            "fetch_url TEXT," +
            "fetched INTEGER," +
            "local_uri TEXT," +
            "encrypted INTEGER, " +
            "encrypt_key TEXT" +
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
            // this the sent/received timestamp
            "timestamp INTEGER," +
            // this the timestamp of the latest status change
            "status_changed INTEGER," +
            "status INTEGER," +
            "draft TEXT" +
            ");";

        /** This table will contain every text message to speed-up full text searches. */
        private static final String SCHEMA_FULLTEXT =
            "CREATE VIRTUAL TABLE " + TABLE_FULLTEXT + " USING fts3 (" +
            "thread_id INTEGER NOT NULL, " +
            "content TEXT" +
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

        /** Updates the thread status reflected by the latest message. */
        /*
        private static final String UPDATE_STATUS_OLD =
            "UPDATE " + TABLE_THREADS + " SET status = (" +
            "SELECT status FROM " + TABLE_MESSAGES + " WHERE thread_id = old.thread_id ORDER BY timestamp DESC LIMIT 1)" +
            " WHERE _id = old.thread_id";
        */
        private static final String UPDATE_STATUS_NEW =
            "UPDATE " + TABLE_THREADS + " SET status = (" +
            "SELECT status FROM " + TABLE_MESSAGES + " WHERE thread_id = new.thread_id ORDER BY timestamp DESC LIMIT 1)" +
            " WHERE _id = new.thread_id";

        /** This trigger will update the threads table counters on INSERT. */
        private static final String TRIGGER_THREADS_INSERT_COUNT =
            "CREATE TRIGGER update_thread_on_insert AFTER INSERT ON " + TABLE_MESSAGES +
            " BEGIN " +
            UPDATE_MESSAGES_COUNT_NEW + ";" +
            UPDATE_UNREAD_COUNT_NEW   + ";" +
            UPDATE_STATUS_NEW         + ";" +
            "END;";

        /** This trigger will update the threads table counters on UPDATE. */
        private static final String TRIGGER_THREADS_UPDATE_COUNT =
            "CREATE TRIGGER update_thread_on_update AFTER UPDATE ON " + TABLE_MESSAGES +
            " BEGIN " +
            UPDATE_MESSAGES_COUNT_NEW + ";" +
            UPDATE_UNREAD_COUNT_NEW   + ";" +
            UPDATE_STATUS_NEW         + ";" +
            "END;";


        /** This trigger will update the threads table counters on DELETE. */
        private static final String TRIGGER_THREADS_DELETE_COUNT =
            "CREATE TRIGGER update_thread_on_delete AFTER DELETE ON " + TABLE_MESSAGES +
            " BEGIN " +
            UPDATE_MESSAGES_COUNT_OLD + ";" +
            UPDATE_UNREAD_COUNT_OLD   + ";" +
            // do not call this here -- UPDATE_STATUS_OLD         + ";" +
            "END;";

        protected DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_MESSAGES);
            db.execSQL(SCHEMA_THREADS);
            db.execSQL(SCHEMA_FULLTEXT);
            db.execSQL(TRIGGER_THREADS_INSERT_COUNT);
            db.execSQL(TRIGGER_THREADS_UPDATE_COUNT);
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

            case CONVERSATIONS_ID:
                qb.setTables(TABLE_MESSAGES);
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(Messages.THREAD_ID + "=" + uri.getPathSegments().get(1));
                break;

            case FULLTEXT_ID:
                qb.setTables(TABLE_FULLTEXT);
                qb.setProjectionMap(fulltextProjectionMap);
                qb.appendWhere(Messages.CONTENT + " MATCH ?");
                selectionArgs = new String[] { uri.getQueryParameter("pattern") };
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
        // only messages table can be inserted
        if (sUriMatcher.match(uri) != MESSAGES) { throw new IllegalArgumentException("Unknown URI " + uri); }
        if (initialValues == null) { throw new IllegalArgumentException("No data"); }

        ContentResolver cr = getContext().getContentResolver();

        // if this flag is true, we'll insert the thread only
        String draft = initialValues.getAsString(Threads.DRAFT);

        ContentValues values = new ContentValues(initialValues);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // create the thread first
        long threadId = updateThreads(db, values);

        if (draft != null) {
            // notify thread change
            cr.notifyChange(
                    ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
                    null);
            // notify conversation change
            cr.notifyChange(
                    ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId),
                    null);

            Log.d(TAG, "draft thread created");
            return null;
        }

        values.put(Messages.THREAD_ID, threadId);

        // insert the new message now!
        long rowId = db.insert(TABLE_MESSAGES, null, values);

        if (rowId > 0) {
            // update fulltext table
            Boolean encrypted = values.getAsBoolean(Messages.ENCRYPTED);
            String mime = values.getAsString(Messages.MIME);
            if ((encrypted == null || !encrypted.booleanValue()) && PlainTextMessage.MIME_TYPE.equals(mime)) {
                byte[] content = values.getAsByteArray(Messages.CONTENT);
                updateFulltext(db, rowId, threadId, content);
            }


            Uri msgUri = ContentUris.withAppendedId(uri, rowId);
            cr.notifyChange(msgUri, null);
            Log.w(TAG, "messages table inserted, id = " + rowId);

            // notify thread change
            cr.notifyChange(
                    ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
                    null);
            // notify conversation change
            cr.notifyChange(
                    ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId),
                    null);

            return msgUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * Updates the threads table, returning the thread id to associate with the new message.
     * A thread is created for the given message if not found.
     * @param db
     * @param values
     * @return the thread id
     */
    private long updateThreads(SQLiteDatabase db, ContentValues initialValues) {
        ContentValues values = new ContentValues(initialValues);
        String peer = values.getAsString(Threads.PEER);

        long threadId = -1;
        if (values.containsKey(Messages.THREAD_ID)) {
            threadId = values.getAsLong(Messages.THREAD_ID);
            values.remove(Messages.THREAD_ID);
        }

        // this will be calculated by the trigger
        values.remove(Messages.UNREAD);
        // remove some other column
        values.remove(Messages.REAL_ID);
        values.remove(Messages.FETCH_URL);
        values.remove(Messages.FETCHED);
        values.remove(Messages.LOCAL_URI);
        values.remove(Messages.ENCRYPTED);
        values.remove(Messages.ENCRYPT_KEY);

        // use text content in threads instead of binary content
        Boolean encrypted = values.getAsBoolean(Messages.ENCRYPTED);
        if (encrypted != null && encrypted.booleanValue()) {
            values.put(Threads.CONTENT, getContext().getResources().getString(R.string.text_encrypted));
        }
        else {
            // use the binary content converted to string
            byte[] content = values.getAsByteArray(Messages.CONTENT);
            values.put(Threads.CONTENT, new String(content));
        }

        // insert new thread
        long resThreadId = db.insert(TABLE_THREADS, null, values);
        if (resThreadId < 0) {
            // clear draft (since we are inserting a new message here)
            values.putNull(Threads.DRAFT);

            db.update(TABLE_THREADS, values, "peer = ?", new String[] { peer });
            // the client did not pass the thread id, query for it manually
            if (threadId < 0) {
                Cursor c = db.query(TABLE_THREADS, new String[] { Threads._ID }, "peer = ?", new String[] { peer }, null, null, null);
                if (c.moveToFirst())
                    threadId = c.getLong(0);
                c.close();
            }
            Log.w(TAG, "thread " + threadId + " updated");
        }
        else {
            threadId = resThreadId;
            Log.w(TAG, "new thread inserted with id " + threadId);

            // notify newly created thread by userid
            // this will be used for fixing ticket #18
            getContext().getContentResolver()
                .notifyChange(Threads.getUri(peer), null);
        }

        return threadId;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (values == null) { throw new IllegalArgumentException("No data"); }

        String table;
        String where;
        String[] args;
        String messageId = null;

        switch (sUriMatcher.match(uri)) {
            case MESSAGES:
                table = TABLE_MESSAGES;
                where = selection;
                args = selectionArgs;
                break;

            case MESSAGES_ID: {
                long _id = ContentUris.parseId(uri);
                table = TABLE_MESSAGES;
                where = Messages._ID + " = ?";
                /*
                 TODO args copy
                if (selection != null)
                    where += " AND (" + selection + ")";
                 */
                args = new String[] { String.valueOf(_id) };
                break;
            }

            case MESSAGES_SERVERID:
                messageId = uri.getPathSegments().get(1);
                table = TABLE_MESSAGES;
                where = Messages.MESSAGE_ID + " = ?";
                /*
                 TODO args copy
                if (selection != null)
                    where += " AND (" + selection + ")";
                 */
                args = new String[] { String.valueOf(messageId) };
                break;

            case THREADS_ID: {
                long _id = ContentUris.parseId(uri);
                table = TABLE_THREADS;
                where = Threads._ID + " = ?";
                /*
                 TODO args copy
                if (selection != null)
                    where += " AND (" + selection + ")";
                 */
                args = new String[] { String.valueOf(_id) };
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // retrieve old data for notifying.
        // This was done because of the update call could make the old where
        // condition not working any more.
        String[] msgIdList = null;
        if (table.equals(TABLE_MESSAGES)) {
            // preserve a list of the matching messages for notification and
            // fulltext update later
            Cursor old = db.query(TABLE_MESSAGES, new String[] { Messages._ID },
                    where, args, null, null, null);
            msgIdList = new String[old.getCount()];
            int i = 0;
            while (old.moveToNext()) {
                msgIdList[i] = old.getString(0);
                i++;
            }

            old.close();
        }

        int rows = db.update(table, values, where, args);

        Log.w(TAG, "messages table updated, affected: " + rows);

        // notify change only if rows are actually affected
        if (rows > 0) {
            getContext().getContentResolver().notifyChange(uri, null);

            if (table.equals(TABLE_MESSAGES)) {
                // update fulltext only if content actually changed
                boolean doUpdateFulltext;
                String[] projection;

                byte[] oldContent = values.getAsByteArray(Messages.CONTENT);
                if (oldContent != null) {
                    doUpdateFulltext = true;
                    projection = new String[] { Messages.THREAD_ID, Messages._ID,
                            Messages.DIRECTION, Messages.MIME,
                            Messages.ENCRYPTED, Messages.CONTENT };
                }
                else {
                    doUpdateFulltext = false;
                    projection = new String[] { Messages.THREAD_ID };
                }

                // build new IN where condition
                if (msgIdList.length > 0) {
                    StringBuilder whereBuilder = new StringBuilder(Messages._ID + " IN (?");
                    for (int i = 1; i < msgIdList.length; i++)
                        whereBuilder.append(",?");
                    whereBuilder.append(")");

                    Cursor c = db.query(TABLE_MESSAGES, projection,
                            whereBuilder.toString(), msgIdList, null, null, null);

                    while (c.moveToNext()) {
                        long threadId = c.getLong(0);
                        updateThreadInfo(db, threadId);

                        // update fulltext if necessary
                        if (doUpdateFulltext) {
                            int direction = c.getInt(2);
                            String mime = c.getString(3);
                            int encrypted = c.getInt(4);
                            if (((direction == Messages.DIRECTION_IN) ? (encrypted == 0) : true) &&
                                    PlainTextMessage.MIME_TYPE.equals(mime))
                                updateFulltext(db, c.getLong(1), threadId, c.getBlob(5));
                        }
                    }

                    c.close();
                }
            }
        }

        return rows;
    }

    private void updateFulltext(SQLiteDatabase db, long id, long threadId, byte[] content) {
        // use the binary content converted to string
        String text = new String(content);

        ContentValues fulltext = new ContentValues();
        fulltext.put(Fulltext._ID, id);
        fulltext.put(Messages.THREAD_ID, threadId);
        fulltext.put(Messages.CONTENT, text);
        db.replace(TABLE_FULLTEXT, null, fulltext);
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

            case THREADS_PEER:
                table = TABLE_THREADS;
                where = "peer = ?";
                args = new String[] { uri.getLastPathSegment() };
                break;

            // special case: conversations
            case CONVERSATIONS_ID: {
                int rows = deleteConversation(uri);
                if (rows > 0) {
                    ContentResolver cr = getContext().getContentResolver();
                    // first of all, notify conversation
                    cr.notifyChange(uri, null);
                    // then notify thread itself
                    long threadId = ContentUris.parseId(uri);
                    cr.notifyChange(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), null);
                }
                return rows;
            }

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long threadId = -1;
        if (table.equals(TABLE_MESSAGES)) {
            // retrieve the thread id for later use by updateThreadInfo(), and
            // also update fulltext table
            Cursor c = db.query(TABLE_MESSAGES, new String[] {
                    Messages.THREAD_ID,
                    Messages._ID,
                    Messages.DIRECTION,
                    Messages.MIME,
                    Messages.ENCRYPTED
                },
                where, args, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    // FIXME this way we'll get only one threadId...
                    threadId = c.getLong(0);

                    // update fulltext
                    int direction = c.getInt(2);
                    String mime = c.getString(3);
                    int encrypted = c.getInt(4);
                    if (((direction == Messages.DIRECTION_IN) ? (encrypted == 0) : true) &&
                            PlainTextMessage.MIME_TYPE.equals(mime))
                        db.delete(TABLE_FULLTEXT, Fulltext._ID + " = " + c.getLong(1), null);
                }

                c.close();
            }
        }

        // DELETE!
        int rows = db.delete(table, where, args);

        // notify change only if rows are actually affected
        if (rows > 0)
            getContext().getContentResolver().notifyChange(uri, null);
        Log.w(TAG, "table " + table + " deleted, affected: " + rows);

        if (table.equals(TABLE_MESSAGES)) {
            // check for empty threads
            deleteEmptyThreads(db);
            // update thread with latest info and status
            if (threadId > 0) {
                if (updateThreadInfo(db, threadId) < 0) {
                    ContentResolver cr = getContext().getContentResolver();
                    cr.notifyChange(
                            ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), null);
                    cr.notifyChange(
                            ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId), null);
                }
            }
            else
                Log.e(TAG, "unable to update thread metadata (threadId not found)");
            // change notifications get triggered by previous method calls
        }

        return rows;
    }

    private int deleteConversation(Uri uri) {
        long threadId = ContentUris.parseId(uri);
        if (threadId > 0) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            try {
                int num = 0;

                db.beginTransaction();
                num += db.delete(TABLE_THREADS, Threads._ID + " = " + threadId, null);

                // query all messages first because we need to notify changes
                Cursor c = db.query(TABLE_MESSAGES, new String[] { Messages._ID },
                        Messages.THREAD_ID + " = " + threadId,
                        null, null, null, null);
                long[] messageList = new long[c.getCount()];
                int i = 0;
                while (c.moveToNext())
                    messageList[i++] = c.getLong(0);
                c.close();

                num += db.delete(TABLE_MESSAGES, Messages.THREAD_ID + " = " + threadId, null);
                // update fulltext
                db.delete(TABLE_FULLTEXT, Messages.THREAD_ID + " = " + threadId, null);

                // commit!
                db.setTransactionSuccessful();

                // notify change for every message :(
                ContentResolver cr = getContext().getContentResolver();
                for (i = 0; i < messageList.length; i++) {
                    cr.notifyChange(ContentUris
                            .withAppendedId(Messages.CONTENT_URI, messageList[i]),
                            null);
                }

                return num;
            }
            finally {
                db.endTransaction();
            }
        }

        return -1;
    }

    /** Updates metadata of a given thread. */
    private int updateThreadInfo(SQLiteDatabase db, long threadId) {
        Cursor c = db.query(TABLE_MESSAGES, new String[] {
                Messages.MESSAGE_ID,
                Messages.DIRECTION,
                Messages.MIME,
                Messages.STATUS,
                Messages.CONTENT,
                Messages.TIMESTAMP,
                Messages.ENCRYPTED
            }, Messages.THREAD_ID + " = ?", new String[] { String.valueOf(threadId) },
            null, null, Messages.INVERTED_SORT_ORDER, "1");

        int rc = -1;
        if (c != null) {
            if (c.moveToFirst()) {
                ContentValues v = new ContentValues();
                v.put(Threads.MESSAGE_ID, c.getString(0));
                v.put(Threads.DIRECTION, c.getInt(1));
                String mime = c.getString(2);
                v.put(Threads.MIME, mime);
                v.put(Threads.STATUS, c.getInt(3));
                int encrypted = c.getInt(6);

                // check if message is encrypted
                String content;
                if (encrypted > 0)
                    content = getContext().getResources().getString(R.string.text_encrypted);
                else {
                    // convert to string...
                    byte[] buf = c.getBlob(4);
                    content = new String(buf);
                }

                v.put(Threads.CONTENT, content);
                v.put(Threads.TIMESTAMP, c.getLong(5));
                rc = db.update(TABLE_THREADS, v, Threads._ID + " = ?", new String[] { String.valueOf(threadId) });
                if (rc > 0) {
                    ContentResolver cres = getContext().getContentResolver();
                    cres.notifyChange(
                            ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), null);
                    cres.notifyChange(
                            ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId), null);
                }
            }
            c.close();
        }

        return rc;
    }

    private int deleteEmptyThreads(SQLiteDatabase db) {
        int rows = db.delete(TABLE_THREADS, "\"" + Threads.COUNT + "\"" + " = 0 AND " +
                Threads.DRAFT + " IS NULL", null);
        Log.i(TAG, "deleting empty threads: " + rows);
        if (rows > 0)
            getContext().getContentResolver().notifyChange(Threads.CONTENT_URI, null);
        return rows;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MESSAGES:
            case CONVERSATIONS_ID:
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
        return (c.delete(ContentUris.withAppendedId(Conversations.CONTENT_URI, id), null, null) > 0);
    }

    /**
     * Marks all messages of the given thread as read.
     * @param context used to request a {@link ContentResolver}
     * @param id the thread id
     * @return the number of rows affected in the messages table
     */
    public static int markThreadAsRead(Context context, long id) {
        ContentResolver c = context.getContentResolver();
        ContentValues values = new ContentValues(1);
        values.put(Messages.UNREAD, Boolean.FALSE);
        return c.update(Messages.CONTENT_URI, values,
                Messages.THREAD_ID + " = ? AND " +
                Messages.UNREAD + " <> 0 AND " +
                Messages.DIRECTION + " = " + Messages.DIRECTION_IN,
                new String[] { String.valueOf(id) });
    }

    public static int getThreadUnreadCount(Context context, long id) {
        int count = 0;
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(
                ContentUris.withAppendedId(Threads.CONTENT_URI, id),
                new String[] { Threads.UNREAD },
                Threads.UNREAD + " > 0",
                null, null);
        if (c.moveToFirst())
            count = c.getInt(0);

        c.close();
        return count;
    }

    private static ContentValues prepareChangeMessageStatus(
            int status, long timestamp, long statusChanged) {
        ContentValues values = new ContentValues();
        values.put(Messages.STATUS, status);
        if (timestamp >= 0)
            values.put(Messages.TIMESTAMP, timestamp);
        if (statusChanged >= 0)
            values.put(Messages.STATUS_CHANGED, statusChanged);
        return values;
    }

    public static int changeMessageStatus(Context context, Uri uri, int status) {
        return changeMessageStatus(context, uri, status, -1, -1);
    }

    public static int changeMessageStatus(Context context, Uri uri, int status, long timestamp, long statusChanged) {
        Log.i(TAG, "changing message status to " + status + " (uri=" + uri + ")");
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);
        return context.getContentResolver().update(uri, values, null, null);
    }

    public static int changeMessageStatus(Context context, long id, int status) {
        return changeMessageStatus(context, id, status, -1, -1);
    }

    public static int changeMessageStatus(Context context, long id, int status, long timestamp, long statusChanged) {
        Log.i(TAG, "changing message status to " + status + " (id=" + id + ")");
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);
        Uri uri = ContentUris.withAppendedId(Messages.CONTENT_URI, id);
        return context.getContentResolver().update(uri, values, null, null);
    }

    public static int changeMessageStatus(Context context, String id, boolean realId, int status) {
        return changeMessageStatus(context, id, realId, status, -1, -1);
    }

    public static int changeMessageStatus(Context context, String id, boolean realId, int status, long timestamp, long statusChanged) {
        Log.i(TAG, "changing message status to " + status + " (id=" + id + ")");
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);

        String field = (realId) ? Messages.REAL_ID : Messages.MESSAGE_ID;
        return context.getContentResolver().update(Messages.CONTENT_URI, values,
                field + " = ?",
                new String[] { id });
    }

    /** Update a message status if old status == whereStatus. */
    public static int changeMessageStatusWhere(Context context,
            boolean notEquals, int whereStatus, String id, boolean realId,
            int status, long timestamp, long statusChanged) {
        Log.i(TAG, "changing message status to " + status + " (id=" + id + ")");
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);

        String field = (realId) ? Messages.REAL_ID : Messages.MESSAGE_ID;
        String op = (notEquals) ? "<>" : "=";
        return context.getContentResolver().update(Messages.CONTENT_URI, values,
                field + " = ? AND " + Messages.STATUS + op + whereStatus,
                new String[] { id });
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS, THREADS);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS + "/#", THREADS_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS + "/*", THREADS_PEER);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES, MESSAGES);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/#", MESSAGES_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/*", MESSAGES_SERVERID);
        sUriMatcher.addURI(AUTHORITY, "conversations/#", CONVERSATIONS_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_FULLTEXT, FULLTEXT_ID);

        messagesProjectionMap = new HashMap<String, String>();
        messagesProjectionMap.put(Messages._ID, Messages._ID);
        messagesProjectionMap.put(Messages.THREAD_ID, Messages.THREAD_ID);
        messagesProjectionMap.put(Messages.MESSAGE_ID, Messages.MESSAGE_ID);
        messagesProjectionMap.put(Messages.REAL_ID, Messages.REAL_ID);
        messagesProjectionMap.put(Messages.PEER, Messages.PEER);
        messagesProjectionMap.put(Messages.MIME, Messages.MIME);
        messagesProjectionMap.put(Messages.CONTENT, Messages.CONTENT);
        messagesProjectionMap.put(Messages.UNREAD, Messages.UNREAD);
        messagesProjectionMap.put(Messages.DIRECTION, Messages.DIRECTION);
        messagesProjectionMap.put(Messages.TIMESTAMP, Messages.TIMESTAMP);
        messagesProjectionMap.put(Messages.STATUS_CHANGED, Messages.STATUS_CHANGED);
        messagesProjectionMap.put(Messages.STATUS, Messages.STATUS);
        messagesProjectionMap.put(Messages.FETCH_URL, Messages.FETCH_URL);
        messagesProjectionMap.put(Messages.FETCHED, Messages.FETCHED);
        messagesProjectionMap.put(Messages.LOCAL_URI, Messages.LOCAL_URI);
        messagesProjectionMap.put(Messages.ENCRYPTED, Messages.ENCRYPTED);
        messagesProjectionMap.put(Messages.ENCRYPT_KEY, Messages.ENCRYPT_KEY);

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
        threadsProjectionMap.put(Threads.STATUS_CHANGED, Threads.STATUS_CHANGED);
        threadsProjectionMap.put(Threads.STATUS, Threads.STATUS);
        threadsProjectionMap.put(Threads.DRAFT, Threads.DRAFT);

        fulltextProjectionMap = new HashMap<String, String>();
        fulltextProjectionMap.put(Messages._ID, Messages._ID);
        fulltextProjectionMap.put(Messages.THREAD_ID, Messages.THREAD_ID);
        fulltextProjectionMap.put(Messages.CONTENT, Messages.CONTENT);
    }
}

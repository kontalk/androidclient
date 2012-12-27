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

package org.kontalk.xmpp.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.message.PlainTextMessage;
import org.kontalk.xmpp.provider.MyMessages.Messages;
import org.kontalk.xmpp.provider.MyMessages.Threads;
import org.kontalk.xmpp.provider.MyMessages.Messages.Fulltext;
import org.kontalk.xmpp.provider.MyMessages.Threads.Conversations;

import android.annotation.TargetApi;
import android.content.ContentProvider;
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
 */
public class MessagesProvider extends ContentProvider {

    private static final String TAG = MessagesProvider.class.getSimpleName();
    public static final String AUTHORITY = "org.kontalk.xmpp.messages";

    private static final int DATABASE_VERSION = 4;
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
    private static final int CONVERSATIONS_ALL_ID = 8;
    private static final int FULLTEXT_ID = 9;

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
            "msg_id TEXT NOT NULL, " +  // UNIQUE
            "real_id TEXT, " +
            "peer TEXT NOT NULL, " +
            "mime TEXT NOT NULL, " +
            "content BLOB," +
            "direction INTEGER NOT NULL, " +
            "unread INTEGER NOT NULL DEFAULT 0, " +
            // this the sent/received timestamp
            "timestamp INTEGER NOT NULL," +
            // this the timestamp of the latest status change
            "status_changed INTEGER," +
            "status INTEGER," +
            "fetch_url TEXT," +
            "local_uri TEXT," +
            "encrypted INTEGER NOT NULL DEFAULT 0, " +
            "encrypt_key TEXT," +
            "preview_path TEXT," +
            // server-received timestamp (or local sending time)
            "server_timestamp TEXT," +
            // message length (original file length for media messages)
            "length INTEGER NOT NULL DEFAULT 0" +
            ")";

        /** This table will contain the latest message from each conversation. */
        private static final String SCHEMA_THREADS =
            "CREATE TABLE " + TABLE_THREADS + " (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "msg_id TEXT NOT NULL, " +  // UNIQUE
            "peer TEXT NOT NULL UNIQUE, " +
            "direction INTEGER NOT NULL, " +
            "count INTEGER NOT NULL DEFAULT 0, " +
            "unread INTEGER NOT NULL DEFAULT 0, " +
            "mime TEXT NOT NULL, " +
            "content TEXT, " +
            // this the sent/received timestamp
            "timestamp INTEGER NOT NULL," +
            // this the timestamp of the latest status change
            "status_changed INTEGER," +
            "status INTEGER," +
            "draft TEXT" +
            ")";

        /** This table will contain every text message to speed-up full text searches. */
        private static final String SCHEMA_FULLTEXT =
            "CREATE VIRTUAL TABLE " + TABLE_FULLTEXT + " USING fts3 (" +
            "thread_id INTEGER NOT NULL, " +
            "content TEXT" +
            ")";

        private static final String SCHEMA_MESSAGES_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS unique_message ON " + TABLE_MESSAGES +
            " (msg_id, direction)";

        private static final String SCHEMA_MESSAGES_TIMESTAMP_IDX =
            "CREATE INDEX IF NOT EXISTS timestamp_message ON " + TABLE_MESSAGES +
            " (timestamp)";

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
            "END";

        /** This trigger will update the threads table counters on UPDATE. */
        private static final String TRIGGER_THREADS_UPDATE_COUNT =
            "CREATE TRIGGER update_thread_on_update AFTER UPDATE ON " + TABLE_MESSAGES +
            " BEGIN " +
            UPDATE_MESSAGES_COUNT_NEW + ";" +
            UPDATE_UNREAD_COUNT_NEW   + ";" +
            UPDATE_STATUS_NEW         + ";" +
            "END";


        /** This trigger will update the threads table counters on DELETE. */
        private static final String TRIGGER_THREADS_DELETE_COUNT =
            "CREATE TRIGGER update_thread_on_delete AFTER DELETE ON " + TABLE_MESSAGES +
            " BEGIN " +
            UPDATE_MESSAGES_COUNT_OLD + ";" +
            UPDATE_UNREAD_COUNT_OLD   + ";" +
            // do not call this here -- UPDATE_STATUS_OLD         + ";" +
            "END";


        private static final String SCHEMA_V1_TO_V2 =
            // add column preview_path to table messages
            "ALTER TABLE " + TABLE_MESSAGES + " ADD COLUMN preview_path TEXT";

        private static final String[] SCHEMA_V2_TO_V3 = {
            // create temporary messages tables without msg_id UNIQUE constraint
            "CREATE TABLE " + TABLE_MESSAGES + "_new (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "thread_id INTEGER NOT NULL, " +
            "msg_id TEXT NOT NULL, " +
            "real_id TEXT, " +
            "peer TEXT NOT NULL, " +
            "mime TEXT NOT NULL, " +
            "content BLOB," +
            "direction INTEGER NOT NULL, " +
            "unread INTEGER NOT NULL DEFAULT 0, " +
            // this the sent/received timestamp
            "timestamp INTEGER NOT NULL," +
            // this the timestamp of the latest status change
            "status_changed INTEGER," +
            "status INTEGER," +
            "fetch_url TEXT," +
            "fetched INTEGER NOT NULL DEFAULT 0," +
            "local_uri TEXT," +
            "encrypted INTEGER NOT NULL DEFAULT 0, " +
            "encrypt_key TEXT," +
            "preview_path TEXT," +
            // server-received timestamp (or local sending time)
            "server_timestamp TEXT," +
            // message length (original file length for media messages)
            "length INTEGER NOT NULL DEFAULT 0" +
            ")",
            // create temporary threads tables without msg_id UNIQUE constraint
            "CREATE TABLE " + TABLE_THREADS + "_new (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "msg_id TEXT NOT NULL, " +
            "peer TEXT NOT NULL UNIQUE, " +
            "direction INTEGER NOT NULL, " +
            "count INTEGER NOT NULL DEFAULT 0, " +
            "unread INTEGER NOT NULL DEFAULT 0, " +
            "mime TEXT NOT NULL, " +
            "content TEXT, " +
            // this the sent/received timestamp
            "timestamp INTEGER NOT NULL," +
            // this the timestamp of the latest status change
            "status_changed INTEGER," +
            "status INTEGER," +
            "draft TEXT" +
            ")",
            // copy contents of messages table
            "INSERT INTO " + TABLE_MESSAGES + "_new SELECT " +
            "_id, thread_id, msg_id, real_id, peer, mime, content, direction, unread, timestamp, status_changed, status, fetch_url, " +
            "fetched, local_uri, encrypted, encrypt_key, preview_path, NULL, 0"
                + " FROM " + TABLE_MESSAGES,
            // copy contents of threads table
            "INSERT INTO " + TABLE_THREADS + "_new SELECT * FROM " + TABLE_THREADS,
            // drop table messages
            "DROP TABLE " + TABLE_MESSAGES,
            // drop table threads
            "DROP TABLE " + TABLE_THREADS,
            // rename messages_new to messages
            "ALTER TABLE " + TABLE_MESSAGES + "_new RENAME TO " + TABLE_MESSAGES,
            // rename threads_new to threads
            "ALTER TABLE " + TABLE_THREADS + "_new RENAME TO " + TABLE_THREADS,
            // unique message index
            SCHEMA_MESSAGES_INDEX,
            // timestamp message index (for sorting)
            SCHEMA_MESSAGES_TIMESTAMP_IDX,
            // triggers
            TRIGGER_THREADS_INSERT_COUNT,
            TRIGGER_THREADS_UPDATE_COUNT,
            TRIGGER_THREADS_DELETE_COUNT
        };

        private static final String[] SCHEMA_V3_TO_V4 = {
            SCHEMA_MESSAGES_TIMESTAMP_IDX
        };

        protected DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_MESSAGES);
            db.execSQL(SCHEMA_THREADS);
            db.execSQL(SCHEMA_FULLTEXT);
            db.execSQL(SCHEMA_MESSAGES_INDEX);
            db.execSQL(SCHEMA_MESSAGES_TIMESTAMP_IDX);
            db.execSQL(TRIGGER_THREADS_INSERT_COUNT);
            db.execSQL(TRIGGER_THREADS_UPDATE_COUNT);
            db.execSQL(TRIGGER_THREADS_DELETE_COUNT);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL(SCHEMA_V1_TO_V2);
            }
            if (oldVersion < 3) {
                for (int i = 0; i < SCHEMA_V2_TO_V3.length; i++)
                    db.execSQL(SCHEMA_V2_TO_V3[i]);
            }
            if (oldVersion < 4) {
                for (int i = 0; i < SCHEMA_V3_TO_V4.length; i++)
                    db.execSQL(SCHEMA_V3_TO_V4[i]);
            }
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
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {
        // only messages table can be inserted
        if (sUriMatcher.match(uri) != MESSAGES) { throw new IllegalArgumentException("Unknown URI " + uri); }
        if (initialValues == null) { throw new IllegalArgumentException("No data"); }

        // if this flag is true, we'll insert the thread only
        String draft = initialValues.getAsString(Threads.DRAFT);

        ContentValues values = new ContentValues(initialValues);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        boolean success = false;
        List<Uri> notifications = new ArrayList<Uri>();

        try {
            beginTransaction(db);

            // create the thread first
            long threadId = updateThreads(db, values, notifications);

            if (draft != null) {
                // notify thread change
                notifications.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
                // notify conversation change
                notifications.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));

                success = setTransactionSuccessful(db);
                return null;
            }

            values.put(Messages.THREAD_ID, threadId);

            // insert the new message now!
            long rowId = db.insertOrThrow(TABLE_MESSAGES, null, values);

            /*
             * this will be useful one day perhaps :)
            long rowId = 0;
            try {
                rowId = db.insert(TABLE_MESSAGES, null, values);
            }
            catch (SQLiteConstraintException e) {
                // unique constraint is on msg_id, direction
                // already stored message, skip it and return uri
                Cursor c = null;
                try {
                    c = db.query(TABLE_MESSAGES, new String[] { Messages._ID },
                        Messages.MESSAGE_ID + " = ? AND " + Messages.DIRECTION + " = ?",
                        new String[] {
                            values.getAsString(Messages.MESSAGE_ID),
                            values.getAsString(Messages.DIRECTION)
                        }, null, null, null, "1");
                    if (c.moveToFirst()) {
                        rowId = c.getLong(0);
                        return ContentUris.withAppendedId(uri, rowId);
                    }
                }
                finally {
                    try {
                        c.close();
                    }
                    catch (Exception eClose) {
                        // ignore exception
                    }
                }

                // message not found (WHAT???)
                throw e;
            }
            */

            if (rowId > 0) {
                // update fulltext table
                Boolean encrypted = values.getAsBoolean(Messages.ENCRYPTED);
                String mime = values.getAsString(Messages.MIME);
                if ((encrypted == null || !encrypted.booleanValue()) && PlainTextMessage.MIME_TYPE.equals(mime)) {
                    byte[] content = values.getAsByteArray(Messages.CONTENT);
                    updateFulltext(db, rowId, threadId, content);
                }

                Uri msgUri = ContentUris.withAppendedId(uri, rowId);
                notifications.add(msgUri);

                // notify thread change
                notifications.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
                // notify conversation change
                notifications.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));

                success = setTransactionSuccessful(db);
                return msgUri;
            }

            throw new SQLException("Failed to insert row into " + uri);
        }
        finally {
            endTransaction(db, success);
            ContentResolver cr = getContext().getContentResolver();
            for (Uri nuri : notifications)
                cr.notifyChange(nuri, null);
        }
    }

    /**
     * Updates the threads table, returning the thread id to associate with the new message.
     * A thread is created for the given message if not found.
     * @param db
     * @param values
     * @return the thread id
     */
    private long updateThreads(SQLiteDatabase db, ContentValues initialValues, List<Uri> notifications) {
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
        values.remove(Messages.LOCAL_URI);
        values.remove(Messages.PREVIEW_PATH);
        values.remove(Messages.ENCRYPTED);
        values.remove(Messages.ENCRYPT_KEY);
        values.remove(Messages.SERVER_TIMESTAMP);
        values.remove(Messages.LENGTH);

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
        }
        else {
            threadId = resThreadId;

            // notify newly created thread by userid
            // this will be used for fixing ticket #18
            notifications.add(Threads.getUri(peer));
        }

        return threadId;
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
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

        List<Uri> notifications = new ArrayList<Uri>();
        boolean success = false;
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        try {
            beginTransaction(db);

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

            // notify change only if rows are actually affected
            if (rows > 0) {
                notifications.add(uri);

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
                            updateThreadInfo(db, threadId, notifications);

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

            success = setTransactionSuccessful(db);
            return rows;
        }
        finally {
            endTransaction(db, success);
            ContentResolver cr = getContext().getContentResolver();
            for (Uri nuri : notifications)
                cr.notifyChange(nuri, null);
        }
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
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
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
                // END :)
            }

            // special case: delete all content
            case CONVERSATIONS_ALL_ID: {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                boolean success = false;
                int num = 0;
                try {
                    beginTransaction(db);
                    // rows count will be conversations
                    num = db.delete(TABLE_THREADS, null, null);
                    db.delete(TABLE_MESSAGES, null, null);
                    // update fulltext
                    db.delete(TABLE_FULLTEXT, null, null);

                    // set transaction successful
                    success = setTransactionSuccessful(db);
                }
                finally {
                    endTransaction(db, success);
                }

                if (num > 0) {
                    ContentResolver cr = getContext().getContentResolver();
                    // notify conversations and threads
                    cr.notifyChange(uri, null);
                    cr.notifyChange(Threads.CONTENT_URI, null);
                }

                return num;
            }

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        int rows = 0;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        boolean success = false;
        List<Uri> notifications = new ArrayList<Uri>();
        try {
            // let's begin this big transaction :S
            beginTransaction(db);

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
                        // FIXME this way we'll only get one threadId...
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
            rows = db.delete(table, where, args);

            // notify change only if rows are actually affected
            if (rows > 0)
                notifications.add(uri);

            if (table.equals(TABLE_MESSAGES)) {
                // check for empty threads
                if (deleteEmptyThreads(db) > 0)
                    notifications.add(Threads.CONTENT_URI);
                // update thread with latest info and status
                if (threadId > 0) {
                    updateThreadInfo(db, threadId, notifications);
                }
                else
                    Log.e(TAG, "unable to update thread metadata (threadId not found)");
                // change notifications get triggered by previous method calls
            }

            success = setTransactionSuccessful(db);
        }
        finally {
            endTransaction(db, success);
            ContentResolver cr = getContext().getContentResolver();
            for (Uri nuri : notifications)
                cr.notifyChange(nuri, null);
        }

        return rows;
    }

    private int deleteConversation(Uri uri) {
        long threadId = ContentUris.parseId(uri);
        if (threadId > 0) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            boolean success = false;
            try {
                int num = 0;

                beginTransaction(db);
                num = db.delete(TABLE_THREADS, Threads._ID + " = " + threadId, null);
                num += db.delete(TABLE_MESSAGES, Messages.THREAD_ID + " = " + threadId, null);
                // update fulltext
                db.delete(TABLE_FULLTEXT, Messages.THREAD_ID + " = " + threadId, null);

                // set transaction successful
                success = setTransactionSuccessful(db);

                return num;
            }
            finally {
                endTransaction(db, success);
            }
        }

        return -1;
    }

    /** Updates metadata of a given thread. */
    private int updateThreadInfo(SQLiteDatabase db, long threadId, List<Uri> notifications) {
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
                    notifications.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
                    notifications.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
                }
            }
            c.close();
        }

        return rc;
    }

    private int deleteEmptyThreads(SQLiteDatabase db) {
        return db.delete(TABLE_THREADS, "\"" + Threads.COUNT + "\"" + " = 0 AND " +
                Threads.DRAFT + " IS NULL", null);
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

    public static boolean deleteDatabase(Context ctx) {
        try {
            ContentResolver c = ctx.getContentResolver();
            c.delete(Conversations.CONTENT_URI, null, null);
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

    public static int changeMessageStatus(Context context, Uri uri, int direction, int status) {
        return changeMessageStatus(context, uri, direction, status, -1, -1);
    }

    public static int changeMessageStatus(Context context, Uri uri, int direction, int status, long timestamp, long statusChanged) {
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);
        return context.getContentResolver().update(uri, values, Messages.DIRECTION + "=" + direction, null);
    }

    public static int changeMessageStatus(Context context, long id, int direction, int status) {
        return changeMessageStatus(context, id, direction, status, -1, -1);
    }

    public static int changeMessageStatus(Context context, long id, int direction, int status, long timestamp, long statusChanged) {
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);
        Uri uri = ContentUris.withAppendedId(Messages.CONTENT_URI, id);
        return context.getContentResolver().update(uri, values, Messages.DIRECTION + "=" + direction, null);
    }

    public static int changeMessageStatus(Context context, String id, int direction, boolean realId, int status) {
        return changeMessageStatus(context, id, direction, realId, status, -1, -1);
    }

    public static int changeMessageStatus(Context context, String id, int direction, boolean realId, int status, long timestamp, long statusChanged) {
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);

        String field = (realId) ? Messages.REAL_ID : Messages.MESSAGE_ID;
        return context.getContentResolver().update(Messages.CONTENT_URI, values,
                field + " = ? AND " +
                Messages.DIRECTION + "=" + direction,
                new String[] { id });
    }

    /** Update a message status if old status == whereStatus. */
    public static int changeMessageStatusWhere(Context context,
            boolean notEquals, int whereStatus, String id, boolean realId,
            int status, long timestamp, long statusChanged) {
        ContentValues values = prepareChangeMessageStatus(status, timestamp, statusChanged);

        String field = (realId) ? Messages.REAL_ID : Messages.MESSAGE_ID;
        String op = (notEquals) ? "<>" : "=";
        return context.getContentResolver().update(Messages.CONTENT_URI, values,
                field + " = ? AND " + Messages.STATUS + op + whereStatus,
                new String[] { id });
    }

    public static long getConversationByMessage(Context context, long msgId) {
        // TODO
        return 0;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS, THREADS);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS + "/#", THREADS_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_THREADS + "/*", THREADS_PEER);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES, MESSAGES);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/#", MESSAGES_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_MESSAGES + "/*", MESSAGES_SERVERID);
        sUriMatcher.addURI(AUTHORITY, "conversations", CONVERSATIONS_ALL_ID);
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
        messagesProjectionMap.put(Messages.LOCAL_URI, Messages.LOCAL_URI);
        messagesProjectionMap.put(Messages.ENCRYPTED, Messages.ENCRYPTED);
        messagesProjectionMap.put(Messages.ENCRYPT_KEY, Messages.ENCRYPT_KEY);
        messagesProjectionMap.put(Messages.PREVIEW_PATH, Messages.PREVIEW_PATH);
        messagesProjectionMap.put(Messages.SERVER_TIMESTAMP, Messages.SERVER_TIMESTAMP);
        messagesProjectionMap.put(Messages.LENGTH, Messages.LENGTH);

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

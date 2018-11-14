/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import io.requery.android.database.sqlite.SQLiteDatabase;
import io.requery.android.database.sqlite.SQLiteOpenHelper;

import org.kontalk.BuildConfig;
import org.kontalk.Log;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.LocationComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Messages.Fulltext;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.util.SystemUtils;


/**
 * The message storage provider.
 * TODO this class needs serious refactoring. There are many tricks and workarounds that should be reworked.
 * @author Daniele Ricci
 */
public class MessagesProvider extends ContentProvider {

    static final String TAG = MessagesProvider.class.getSimpleName();
    public static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".messages";

    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_MESSAGES_GROUPS = "messages_groups";
    private static final String TABLE_FULLTEXT = "fulltext";
    private static final String TABLE_THREADS = "threads";
    private static final String TABLE_GROUPS = "groups";
    private static final String TABLE_GROUP_MEMBERS = "group_members";

    private static final String TABLE_THREADS_GROUPS = TABLE_THREADS +
        " LEFT OUTER JOIN " + TABLE_GROUPS + " ON " +
        TABLE_THREADS + "." + Threads._ID + "=" +
        TABLE_GROUPS + "." + Groups.THREAD_ID;

    private static final int THREADS = 1;
    private static final int THREADS_ID = 2;
    private static final int THREADS_PEER = 3;
    private static final int MESSAGES = 4;
    private static final int MESSAGES_ID = 5;
    private static final int MESSAGES_SERVERID = 6;
    private static final int CONVERSATIONS_ID = 7;
    private static final int CONVERSATIONS_ALL_ID = 8;
    private static final int GROUPS = 9;
    private static final int GROUPS_ID = 10;
    private static final int GROUPS_MEMBERS = 11;
    private static final int GROUPS_MEMBERS_ID = 12;
    private static final int FULLTEXT_ID = 13;
    private static final int REQUESTS = 14;
    private static final int IMPORT_LOCK = 15;
    private static final int IMPORT_UNLOCK = 16;
    private static final int RELOAD = 17;

    private DatabaseHelper dbHelper;
    private static final UriMatcher sUriMatcher;
    private static HashMap<String, String> messagesProjectionMap;
    private static HashMap<String, String> threadsProjectionMap;
    private static HashMap<String, String> fulltextProjectionMap;
    private static HashMap<String, String> groupsMembersProjectionMap;
    private static HashMap<String, String> groupsProjectionMap;

    @VisibleForTesting
    static class DatabaseHelper extends SQLiteOpenHelper {
        @VisibleForTesting
        static final int DATABASE_VERSION = 19;
        @VisibleForTesting
        static final String DATABASE_NAME = "messages.db";

        // TODO UNIQUE index on msg_id + direction
        private static final String _SCHEMA_MESSAGES = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "thread_id INTEGER NOT NULL, " +
            "msg_id TEXT NOT NULL, " +  // UNIQUE
            "peer TEXT NOT NULL, " +
            "direction INTEGER NOT NULL, " +
            "unread INTEGER NOT NULL DEFAULT 0, " +
            "new INTEGER NOT NULL DEFAULT 0, " +
            // this the sent/received timestamp
            // this will not change after insert EVER
            "timestamp INTEGER NOT NULL," +
            // this the timestamp of the latest status change
            // updated when status field is modified
            "status_changed INTEGER," +
            "status INTEGER," +

            // for text body or encrypted stanza
            "body_mime TEXT," + // if null couldn't be determined yet (e.g. encrypted message)
            "body_content BLOB," + // message body or encrypted e2e content (if mime is null)
            "body_length INTEGER NOT NULL DEFAULT 0," +

            // for a single attachment
            "att_mime TEXT, " +
            "att_preview_path TEXT," +
            "att_fetch_url TEXT," +
            "att_local_uri TEXT," +
            "att_length INTEGER NOT NULL DEFAULT 0," +
            "att_compress INTEGER NOT NULL DEFAULT 0," +
            "att_encrypted INTEGER NOT NULL DEFAULT 0," +
            "att_security_flags INTEGER NOT NULL DEFAULT 0," +

            // location data
            "geo_lat NUMBER," +
            "geo_lon NUMBER," +
            "geo_text TEXT," +
            "geo_street TEXT," +

            // whole content encrypted
            "encrypted INTEGER NOT NULL DEFAULT 0, " +
            // security flags
            "security_flags INTEGER NOT NULL DEFAULT 0," +
            // timestamp declared by server for incoming messages
            // timestamp of message accepted by server for outgoing messages
            "server_timestamp INTEGER," +

            // reference to message id we are replying to
            "in_reply_to INTEGER" +
            ")";

        /** This table will contain all the messages .*/
        private static final String SCHEMA_MESSAGES =
            "CREATE TABLE " + TABLE_MESSAGES + " " + _SCHEMA_MESSAGES;

        private static final String _SCHEMA_THREADS = "(" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "msg_id TEXT NOT NULL, " +  // UNIQUE
            "peer TEXT NOT NULL UNIQUE, " +
            "direction INTEGER NOT NULL, " +
            "count INTEGER NOT NULL DEFAULT 0, " +
            "unread INTEGER NOT NULL DEFAULT 0, " +
            "new INTEGER NOT NULL DEFAULT 0, " +
            "mime TEXT, " +
            "content TEXT, " +
            // this the sent/received timestamp
            "timestamp INTEGER NOT NULL," +
            // this the timestamp of the latest status change
            "status_changed INTEGER," +
            "status INTEGER," +
            "encrypted INTEGER NOT NULL DEFAULT 0, " +
            "draft TEXT," +
            "request_status INTEGER NOT NULL DEFAULT 0," +
            "sticky INTEGER NOT NULL DEFAULT 0," +
            "encryption INTEGER NOT NULL DEFAULT 1," +
            "archived INTEGER NOT NULL DEFAULT 0" +
            ")";

        /** This table will contain the latest message from each conversation. */
        private static final String SCHEMA_THREADS =
            "CREATE TABLE " + TABLE_THREADS + " " + _SCHEMA_THREADS;

        private static final String _SCHEMA_GROUPS = "(" +
            "group_jid TEXT NOT NULL PRIMARY KEY, " +
            "thread_id INTEGER NOT NULL," +
            "group_type TEXT NOT NULL," +
            "subject TEXT," +
            "membership INTEGER NOT NULL DEFAULT 1" +
            ")";

        /** This table will contain the groups definitions.*/
        private static final String SCHEMA_GROUPS =
            "CREATE TABLE " + TABLE_GROUPS + " " + _SCHEMA_GROUPS;

        private static final String _SCHEMA_GROUP_MEMBERS = "(" +
            "group_jid TEXT NOT NULL, " +
            "group_peer TEXT NOT NULL, " +
            "pending INTEGER NOT NULL DEFAULT 0," +
            "PRIMARY KEY (group_jid, group_peer)" +
            ")";

        /** This table will contain the groups participants .*/
        private static final String SCHEMA_GROUPS_MEMBERS =
            "CREATE TABLE " + TABLE_GROUP_MEMBERS + " " + _SCHEMA_GROUP_MEMBERS;

        /** A view to link messages and groups. */
        private static final String SCHEMA_MESSAGES_GROUPS =
            "CREATE VIEW " + TABLE_MESSAGES_GROUPS + " AS " +
            "SELECT " + TABLE_MESSAGES + ".*," +
                TABLE_GROUPS + "." + Groups.GROUP_JID + "," +
                TABLE_GROUPS + "." + Groups.SUBJECT + "," +
                TABLE_GROUPS + "." + Groups.GROUP_TYPE + "," +
                TABLE_GROUPS + "." + Groups.MEMBERSHIP +
            " FROM " + TABLE_MESSAGES + " LEFT JOIN " + TABLE_THREADS +
            " ON " + TABLE_MESSAGES + "." + Messages.THREAD_ID + "=" + TABLE_THREADS + "." + Threads._ID +
            " LEFT OUTER JOIN " + TABLE_GROUPS + " ON " +
            TABLE_THREADS + "." + Threads._ID + "=" +
            TABLE_GROUPS + "." + Groups.THREAD_ID;

        /** This table will contain every text message to speed-up full text searches. */
        private static final String SCHEMA_FULLTEXT =
            "CREATE VIRTUAL TABLE " + TABLE_FULLTEXT + " USING fts3 (" +
            "msg_id INTEGER PRIMARY KEY," +
            "thread_id INTEGER NOT NULL, " +
            "timestamp INTEGER NOT NULL," +
            "content TEXT" +
            ")";

        private static final String SCHEMA_MESSAGES_INDEX =
            "CREATE UNIQUE INDEX IF NOT EXISTS unique_message ON " + TABLE_MESSAGES +
            " (msg_id, direction)";

        private static final String SCHEMA_MESSAGES_TIMESTAMP_IDX =
            "CREATE INDEX IF NOT EXISTS timestamp_message ON " + TABLE_MESSAGES +
            " (timestamp)";

        private static final String SCHEMA_MESSAGES_THREAD_ID_IDX =
            "CREATE INDEX IF NOT EXISTS idx_messages_thread_id ON " + TABLE_MESSAGES +
            "(" + Messages.THREAD_ID + ")";

        /** Updates the thread messages count. */
        private static final String UPDATE_MESSAGES_COUNT_NEW =
            "UPDATE " + TABLE_THREADS + " SET count = (" +
            "SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = new.thread_id" +
            ") WHERE _id = new.thread_id";
        private static final String UPDATE_MESSAGES_COUNT_OLD =
            "UPDATE " + TABLE_THREADS + " SET count = (" +
            "SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = old.thread_id" +
            ") WHERE _id = old.thread_id";

        /** Updates the thread unread/new count. */
        private static final String UPDATE_UNREAD_COUNT_NEW =
            "UPDATE " + TABLE_THREADS + " SET " +
                "unread = (SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = new.thread_id AND unread <> 0), " +
                "\"new\" = (SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = new.thread_id AND \"new\" <> 0) " +
                "WHERE _id = new.thread_id";
        private static final String UPDATE_UNREAD_COUNT_OLD =
            "UPDATE " + TABLE_THREADS + " SET " +
                "unread = (SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = old.thread_id AND unread <> 0), " +
                "\"new\" = (SELECT COUNT(_id) FROM " + TABLE_MESSAGES + " WHERE thread_id = old.thread_id AND \"new\" <> 0) " +
                "WHERE _id = old.thread_id";

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
            "CREATE TRIGGER update_thread_on_update AFTER UPDATE OF " +
                Messages.STATUS + " ON " + TABLE_MESSAGES +
            " BEGIN " +
            UPDATE_STATUS_NEW         + ";" +
            "END";

        /** Delete group members linked to thread. */
        private static final String DELETE_GROUP_MEMBERS = "DELETE FROM " + TABLE_GROUP_MEMBERS + " WHERE " +
            Groups.GROUP_JID + "=old." + Groups.GROUP_JID;

        /** This trigger will update the threads table counters on DELETE. */
        private static final String TRIGGER_THREADS_DELETE_COUNT =
            "CREATE TRIGGER update_thread_on_delete AFTER DELETE ON " + TABLE_MESSAGES +
            " BEGIN " +
            UPDATE_MESSAGES_COUNT_OLD + ";" +
            UPDATE_UNREAD_COUNT_OLD   + ";" +
            // do not call this here -- UPDATE_STATUS_OLD         + ";" +
            "END";

        /** This trigger will delete group members when a group is deleted. */
        private static final String TRIGGER_GROUPS_DELETE_MEMBERS =
            "CREATE TRIGGER delete_groups_on_delete AFTER DELETE ON " + TABLE_GROUPS +
            " BEGIN " +
            DELETE_GROUP_MEMBERS      + ";" +
            "END";

        // -- schema upgrades --
        // the number in the constant name is the version we are upgrading *from*

        private static final String[] SCHEMA_UPGRADE_V8 = {
            "CREATE TABLE groups (" +
                "group_jid TEXT NOT NULL PRIMARY KEY, " +
                "thread_id INTEGER NOT NULL," +
                "group_type TEXT NOT NULL," +
                "subject TEXT" +
            ")",
            "CREATE TABLE group_members (" +
                "group_jid TEXT NOT NULL, " +
                "group_peer TEXT NOT NULL, " +
                "pending INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY (group_jid, group_peer)" +
            ")",
            "CREATE VIEW messages_groups AS " +
            "SELECT messages.*," +
            "groups.group_jid," +
            "groups.subject," +
            "groups.group_type" +
            " FROM messages LEFT JOIN threads" +
            " ON messages.thread_id=threads._id" +
            " LEFT OUTER JOIN groups ON " +
            "threads._id=groups.thread_id",
            "CREATE TRIGGER delete_groups_on_delete AFTER DELETE ON groups" +
            " BEGIN " +
            "DELETE FROM group_members WHERE group_jid=old.group_jid;" +
            "END",
        };

        private static final String[] SCHEMA_UPGRADE_V9 = {
            "ALTER TABLE groups ADD COLUMN membership INTEGER NOT NULL DEFAULT 1",
            "DROP VIEW messages_groups",
            "CREATE VIEW messages_groups AS " +
            "SELECT messages.*," +
            "groups.group_jid," +
            "groups.subject," +
            "groups.group_type," +
            "groups.membership" +
            " FROM messages LEFT JOIN threads" +
            " ON messages.thread_id=threads._id" +
            " LEFT OUTER JOIN groups ON " +
            "threads._id=groups.thread_id",
        };

        private static final String[] SCHEMA_UPGRADE_V10 = {
            "ALTER TABLE threads ADD COLUMN sticky INTEGER NOT NULL DEFAULT 0",
        };

        private static final String SCHEMA_UPGRADE_V11 =
            "ALTER TABLE threads ADD COLUMN encryption INTEGER NOT NULL DEFAULT 1";

        private static final String[] SCHEMA_UPGRADE_V12 = {
            "ALTER TABLE messages ADD COLUMN geo_lat NUMBER",
            "ALTER TABLE messages ADD COLUMN geo_lon NUMBER",
            "ALTER TABLE messages ADD COLUMN geo_text TEXT",
            "ALTER TABLE messages ADD COLUMN geo_street TEXT",
        };

        private static final String[] SCHEMA_UPGRADE_V13 = {
            "DELETE FROM messages WHERE thread_id < 0",
        };

        private static final String[] SCHEMA_UPGRADE_V14 = {
            "CREATE INDEX idx_messages_thread_id ON messages(thread_id)",
        };

        private static final String[] SCHEMA_UPGRADE_V15 = {
            "ALTER TABLE messages ADD COLUMN in_reply_to INTEGER",
        };

        private static final String[] SCHEMA_UPGRADE_V16 = {
            "DROP TRIGGER IF EXISTS update_thread_on_insert",
            "CREATE TRIGGER update_thread_on_insert AFTER INSERT ON messages" +
                " BEGIN " +
                "UPDATE threads SET count = (" +
                    "SELECT COUNT(_id) FROM messages WHERE thread_id = new.thread_id" +
                    ") WHERE _id = new.thread_id;" +
                "UPDATE threads SET " +
                    "unread = (SELECT COUNT(_id) FROM messages WHERE thread_id = new.thread_id AND unread <> 0), " +
                    "\"new\" = (SELECT COUNT(_id) FROM messages WHERE thread_id = new.thread_id AND \"new\" <> 0) " +
                    "WHERE _id = new.thread_id;" +
                "UPDATE threads SET status = (" +
                    "SELECT status FROM messages WHERE thread_id = new.thread_id ORDER BY timestamp DESC LIMIT 1)" +
                    " WHERE _id = new.thread_id;" +
                "END",
            "DROP TRIGGER IF EXISTS update_thread_on_update",
            "CREATE TRIGGER update_thread_on_update AFTER UPDATE OF " +
                "status ON messages" +
                " BEGIN " +
                "UPDATE threads SET status = (" +
                    "SELECT status FROM messages WHERE thread_id = new.thread_id ORDER BY timestamp DESC LIMIT 1)" +
                    " WHERE _id = new.thread_id;" +
                "END",
            "DROP TRIGGER IF EXISTS update_thread_on_delete",
            "CREATE TRIGGER update_thread_on_delete AFTER DELETE ON messages" +
                " BEGIN " +
                "UPDATE threads SET count = (" +
                    "SELECT COUNT(_id) FROM messages WHERE thread_id = old.thread_id" +
                    ") WHERE _id = old.thread_id;" +
                "UPDATE threads SET " +
                    "unread = (SELECT COUNT(_id) FROM messages WHERE thread_id = old.thread_id AND unread <> 0), " +
                    "\"new\" = (SELECT COUNT(_id) FROM messages WHERE thread_id = old.thread_id AND \"new\" <> 0) " +
                    "WHERE _id = old.thread_id;" +
                "END",
        };

        private static final String[] SCHEMA_UPGRADE_V17 = {
            "ALTER TABLE threads ADD COLUMN archived INTEGER NOT NULL DEFAULT 0",
        };

        private static final String[] SCHEMA_UPGRADE_V18 = {
            "CREATE VIRTUAL TABLE fulltext_timestamp USING fts3 (" +
                "msg_id INTEGER PRIMARY KEY," +
                "thread_id INTEGER NOT NULL," +
                "timestamp INTEGER NOT NULL," +
                "content TEXT)",
            "INSERT INTO fulltext_timestamp" +
                " SELECT _id, thread_id, timestamp, CAST(body_content AS TEXT)" +
                "  FROM messages" +
                " WHERE body_mime = 'text/plain' AND" +
                " encrypted = 0",
            "DROP TABLE fulltext",
            "ALTER TABLE fulltext_timestamp RENAME TO fulltext",
        };

        /** If true, fail all operations. */
        private boolean mLocked;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_MESSAGES);
            db.execSQL(SCHEMA_THREADS);
            db.execSQL(SCHEMA_GROUPS);
            db.execSQL(SCHEMA_GROUPS_MEMBERS);
            db.execSQL(SCHEMA_MESSAGES_GROUPS);
            db.execSQL(SCHEMA_FULLTEXT);
            db.execSQL(SCHEMA_MESSAGES_INDEX);
            db.execSQL(SCHEMA_MESSAGES_TIMESTAMP_IDX);
            db.execSQL(SCHEMA_MESSAGES_THREAD_ID_IDX);
            db.execSQL(TRIGGER_THREADS_INSERT_COUNT);
            db.execSQL(TRIGGER_THREADS_UPDATE_COUNT);
            db.execSQL(TRIGGER_THREADS_DELETE_COUNT);
            db.execSQL(TRIGGER_GROUPS_DELETE_MEMBERS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 8) {
                // unsupported version
                throw new SQLException("database can only be upgraded from versions greater than 7");
            }

            switch (oldVersion) {
                case 8:
                    for (String sql : SCHEMA_UPGRADE_V8) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 9:
                    for (String sql : SCHEMA_UPGRADE_V9) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 10:
                    for (String sql : SCHEMA_UPGRADE_V10) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 11:
                    db.execSQL(SCHEMA_UPGRADE_V11);
                    // fall through
                case 12:
                    for (String sql : SCHEMA_UPGRADE_V12) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 13:
                    for (String sql : SCHEMA_UPGRADE_V13) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 14:
                    for (String sql : SCHEMA_UPGRADE_V14) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 15:
                    for (String sql : SCHEMA_UPGRADE_V15) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 16:
                    for (String sql : SCHEMA_UPGRADE_V16) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 17:
                    for (String sql : SCHEMA_UPGRADE_V17) {
                        db.execSQL(sql);
                    }
                    // fall through
                case 18:
                    for (String sql : SCHEMA_UPGRADE_V18) {
                        db.execSQL(sql);
                    }
                    // fall through
            }
        }

        public void lock() {
            mLocked = true;
        }

        public void unlock() {
            mLocked = false;
        }

        @Override
        public SQLiteDatabase getReadableDatabase() {
            if (mLocked)
                throw new SQLiteDatabaseLockedException("locked by user");
            return super.getReadableDatabase();
        }

        @Override
        public SQLiteDatabase getWritableDatabase() {
            if (mLocked)
                throw new SQLiteDatabaseLockedException("locked by user");
            return super.getWritableDatabase();
        }
    }

    @Override
    public boolean onCreate() {
        dbHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public void shutdown() {
        dbHelper.close();
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLitePagedQueryBuilder qb = new SQLitePagedQueryBuilder();

        switch (sUriMatcher.match(uri)) {
            case MESSAGES:
                qb.setTables(TABLE_MESSAGES_GROUPS);
                qb.setProjectionMap(messagesProjectionMap);
                break;

            case MESSAGES_ID:
                qb.setTables(TABLE_MESSAGES_GROUPS);
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(Messages._ID + "=" + uri.getPathSegments().get(1));
                break;

            case MESSAGES_SERVERID:
                qb.setTables(TABLE_MESSAGES_GROUPS);
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(Messages.MESSAGE_ID + "=" + DatabaseUtils.sqlEscapeString(uri.getPathSegments().get(1)));
                break;

            case THREADS:
                qb.setTables(TABLE_THREADS_GROUPS);
                qb.setProjectionMap(threadsProjectionMap);
                break;

            case THREADS_ID:
                qb.setTables(TABLE_THREADS_GROUPS);
                qb.setProjectionMap(threadsProjectionMap);
                qb.appendWhere(Threads._ID + "=" + uri.getPathSegments().get(1));
                break;

            case THREADS_PEER:
                qb.setTables(TABLE_THREADS_GROUPS);
                qb.setProjectionMap(threadsProjectionMap);
                qb.appendWhere(Threads.PEER + "=" + DatabaseUtils.sqlEscapeString(uri.getPathSegments().get(1)) + " COLLATE NOCASE");
                break;

            case CONVERSATIONS_ID:
                // page row count
                int count = 0;
                // last ID (scrolling cursor)
                int lastId = 0;

                try {
                    lastId = Integer.parseInt(uri.getQueryParameter("last"));
                }
                catch (Exception ignored) {
                }
                try {
                    count = Integer.parseInt(uri.getQueryParameter("count"));
                }
                catch (Exception ignored) {
                }

                // setup page if requested
                if (count > 0) {
                    qb.setPage(count, Messages._ID, lastId);
                }

                qb.setTables(TABLE_MESSAGES_GROUPS);
                qb.setProjectionMap(messagesProjectionMap);
                qb.appendWhere(Messages.THREAD_ID + "=" + uri.getPathSegments().get(1));
                break;

            case FULLTEXT_ID:
                qb.setTables(TABLE_FULLTEXT);
                qb.setProjectionMap(fulltextProjectionMap);
                qb.appendWhere(Fulltext.CONTENT + " MATCH ?");
                selectionArgs = new String[] { uri.getQueryParameter("pattern") };
                break;

            case GROUPS_ID:
                qb.setTables(TABLE_GROUPS);
                qb.setProjectionMap(groupsProjectionMap
                );
                qb.appendWhere(Groups.GROUP_JID + "=? COLLATE NOCASE");
                if (selectionArgs != null) {
                    // conditions appended here will get added before the caller-supplied selection
                    selectionArgs = SystemUtils.concatenate(new String[] { uri.getLastPathSegment() },
                        selectionArgs);
                }
                else {
                    selectionArgs = new String[] { uri.getLastPathSegment() };
                }
                break;

            case GROUPS_MEMBERS:
                qb.setTables(TABLE_GROUP_MEMBERS);
                qb.setProjectionMap(groupsMembersProjectionMap);
                qb.appendWhere(Groups.GROUP_JID + "=? COLLATE NOCASE");
                if (selectionArgs != null) {
                    // conditions appended here will get added before the caller-supplied selection
                    selectionArgs = SystemUtils.concatenate(new String[] { uri.getPathSegments().get(1) },
                        selectionArgs);
                }
                else {
                    selectionArgs = new String[] { uri.getPathSegments().get(1) };
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        String limit = uri.getQueryParameter("limit");

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c;
        if (projection != null && projection.length == 1 && projection[0].equals(BaseColumns._COUNT)) {
            c = db.query(qb.getTables(), new String[] { "COUNT(*) AS " + BaseColumns._COUNT },
                selection, selectionArgs, null, null, sortOrder, limit);
        }
        else {
            c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder, limit);
        }

        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        if (initialValues == null)
            throw new IllegalArgumentException("No data");

        // only messages and requests virtual table can be inserted
        int match = sUriMatcher.match(uri);
        if (match != MESSAGES && match != REQUESTS && match != GROUPS && match != GROUPS_MEMBERS)
            throw new IllegalArgumentException("Unknown URI " + uri);

        // if this column is present, we'll insert the thread only
        String draft = initialValues.getAsString(Threads.DRAFT);

        ContentValues values = new ContentValues(initialValues);

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        List<Uri> notifications = new ArrayList<>();

        db.beginTransactionNonExclusive();

        try {
            switch (match) {
                case GROUPS:
                    // configure thread as group
                    insertGroup(db, values, notifications);
                    db.setTransactionSuccessful();
                    // no uri needed
                    return null;
                case GROUPS_MEMBERS:
                    // insert members into group
                    String groupJid = uri.getPathSegments().get(1);
                    insertGroupMembers(db, groupJid, values);
                    db.setTransactionSuccessful();
                    // no uri needed
                    return null;
            }

            // we need to know if there previously was a pending request
            // so we can decide if we have to fire a notification or not
            boolean requestExists = false;
            if (match == REQUESTS) {
                requestExists = isRequestPending(db, initialValues.getAsString(Threads.PEER));
            }

            // create the thread first
            long threadId = updateThreads(db, values, notifications, match == REQUESTS);
            values.put(Messages.THREAD_ID, threadId);

            if (threadId != Messages.NO_THREAD && (draft != null || match == REQUESTS)) {
                // notify thread change
                notifications.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
                // notify conversation change
                notifications.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));

                db.setTransactionSuccessful();

                // draft or request - return conversation
                return (draft != null || !requestExists) ?
                    ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId) : null;
            }

            // remove reserved columns
            values.remove(Groups.GROUP_JID);
            values.remove(Groups.SUBJECT);
            values.remove(Groups.GROUP_TYPE);
            values.remove(Threads.ENCRYPTION);

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
                if (threadId != Messages.NO_THREAD) {
                    // update fulltext table
                    byte[] content = values.getAsByteArray(Messages.BODY_CONTENT);
                    String mime = values.getAsString(Messages.BODY_MIME);
                    Boolean encrypted = values.getAsBoolean(Messages.ENCRYPTED);
                    if (content != null && content.length > 0 && TextComponent.MIME_TYPE.equals(mime) &&
                            (encrypted == null || !encrypted)) {
                        updateFulltext(db, rowId, threadId, content);
                    }
                }

                Uri msgUri = ContentUris.withAppendedId(uri, rowId);
                notifications.add(msgUri);

                if (threadId != Messages.NO_THREAD) {
                    // notify thread change
                    notifications.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
                    // notify conversation change
                    notifications.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
                }

                db.setTransactionSuccessful();
                return msgUri;
            }

            throw new SQLException("Failed to insert row into " + uri);
        }
        finally {
            db.endTransaction();
            ContentResolver cr = getContext().getContentResolver();
            for (Uri nuri : notifications)
                cr.notifyChange(nuri, null);
        }
    }

    private void insertGroup(SQLiteDatabase db, ContentValues values, List<Uri> notifications) {
        if (notifications != null) {
            long threadId = values.getAsLong(Groups.THREAD_ID);
            // notify thread change
            notifications.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
            // notify conversation change
            notifications.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
        }

        db.insertOrThrow(TABLE_GROUPS, null, values);
    }

    private void insertGroupMembers(SQLiteDatabase db, String groupJid, ContentValues values) {
        // TODO shouldn't we notify someone?

        try {
            values.put(Groups.GROUP_JID, groupJid);
            db.insertOrThrow(TABLE_GROUP_MEMBERS, null, values);
        }
        catch (SQLiteConstraintException e) {
            // just ignore dups - it doesn't really matter
        }
    }

    private boolean isRequestPending(SQLiteDatabase db, String peer) {
        Cursor c = null;
        try {
            c = db.query(TABLE_THREADS, new String[] { Threads.REQUEST_STATUS },
                Threads.PEER + "=? COLLATE NOCASE", new String[] { peer }, null, null, null);
            return c.moveToFirst() && c.getInt(0) == Threads.REQUEST_WAITING;
        }
        catch (Exception e) {
            // ignored
        }
        finally {
            if (c != null)
                c.close();
        }
        return false;
    }

    /** Used to determine content and mime type for a thread. */
    private void setThreadContent(byte[] bodyContent, String bodyMime, String attachmentMime, String peer, ContentValues values) {
        String mime;
        String content;

        // use the binary content converted to string
        if (bodyContent == null) {
            // try the attachment mime
            mime = attachmentMime;
            // no content
            content = null;
        }
        else {
            // use body data if there is indeed a mime
            if (bodyMime != null) {
                mime = bodyMime;
                // do not include content for location messages
                content = !LocationComponent.supportsMimeType(mime) ?
                        new String(bodyContent) : null;
            }
            // no mime and no data, nothing to do
            else {
                mime = null;
                content = null;
            }
        }

        if (peer != null) {
            String newContent = peer + ";";
            if (content != null)
                newContent += content;
            content = newContent;
        }

        values.put(Threads.CONTENT, content);
        values.put(Threads.MIME, mime);
    }

    /**
     * Updates the threads table, returning the thread id to associate with the new message.
     * A thread is created for the given message if not found.
     * @return the thread id
     */
    private long updateThreads(SQLiteDatabase db, ContentValues initialValues, List<Uri> notifications, boolean requestOnly) {
        long threadId = -1;
        if (initialValues.containsKey(Messages.THREAD_ID)) {
            threadId = initialValues.getAsLong(Messages.THREAD_ID);
            if (threadId == Messages.NO_THREAD)
                return Messages.NO_THREAD;
        }

        ContentValues values = new ContentValues();
        // group JID will be the thread peer in this case
        String peer;
        String groupJid = initialValues.getAsString(Groups.GROUP_JID);
        if (groupJid != null)
            peer = groupJid;
        else
            peer = initialValues.getAsString(Threads.PEER);

        values.put(Threads.PEER, peer);
        values.put(Threads.TIMESTAMP, initialValues.getAsLong(Messages.TIMESTAMP));
        if (initialValues.containsKey(Threads.ENCRYPTION))
            values.put(Threads.ENCRYPTION, initialValues.getAsBoolean(Threads.ENCRYPTION));

        if (requestOnly) {

            values.put(Threads.MESSAGE_ID, "");
            values.put(Threads.DIRECTION, Messages.DIRECTION_IN);
            values.put(Threads.ENCRYPTED, false);
            values.put(Threads.REQUEST_STATUS, Threads.REQUEST_WAITING);

        }

        else {

            values.put(Threads.MESSAGE_ID, initialValues.getAsString(Messages.MESSAGE_ID));
            values.put(Threads.DIRECTION, initialValues.getAsInteger(Messages.DIRECTION));
            values.put(Threads.ENCRYPTED, initialValues.getAsBoolean(Messages.ENCRYPTED));

            if (initialValues.containsKey(Messages.STATUS))
                values.put(Threads.STATUS, initialValues.getAsInteger(Messages.STATUS));
            if (initialValues.containsKey(Messages.STATUS_CHANGED))
                values.put(Threads.STATUS_CHANGED, initialValues.getAsInteger(Messages.STATUS_CHANGED));
            // this column is an exception
            if (initialValues.containsKey(Threads.DRAFT)) {
                String draft = initialValues.getAsString(Threads.DRAFT);
                if (draft != null && draft.length() == 0)
                    values.putNull(Threads.DRAFT);
                else
                    values.put(Threads.DRAFT, draft);
            }

            // unread column will be calculated by the trigger

            // thread content has a special behaviour
            int direction = initialValues.getAsInteger(Messages.DIRECTION);
            setThreadContent(
                initialValues.getAsByteArray(Messages.BODY_CONTENT),
                initialValues.getAsString(Messages.BODY_MIME),
                initialValues.getAsString(Messages.ATTACHMENT_MIME),
                direction == Messages.DIRECTION_IN && groupJid != null ?
                    initialValues.getAsString(Threads.PEER) : null,
                values);
        }

        // will reset archived status since we are called for inserting a message
        values.put(Threads.ARCHIVED, false);

        // insert new thread
        try {
            threadId = db.insertOrThrow(TABLE_THREADS, null, values);

            // insert group info if needed
            if (groupJid != null) {
                ContentValues groupValues = new ContentValues();
                groupValues.put(Groups.GROUP_JID, groupJid);
                groupValues.put(Groups.THREAD_ID, threadId);
                groupValues.put(Groups.SUBJECT, initialValues.getAsString(Groups.SUBJECT));
                groupValues.put(Groups.GROUP_TYPE, initialValues.getAsString(Groups.GROUP_TYPE));
                insertGroup(db, groupValues, null);
            }

            // notify newly created thread by userid
            // this will be used for fixing ticket #18
            notifications.add(Threads.getUri(peer));
        }
        catch (SQLException e) {
            //Log.w(TAG, "error updating thread: " + e.getClass(), e);
            // clear draft if outgoing message
            Integer direction = values.getAsInteger(Threads.DIRECTION);
            if (direction != null && direction == Messages.DIRECTION_OUT)
                values.putNull(Threads.DRAFT);
            // remove other stuff coming from subscription request entry
            if (requestOnly) {
                values.remove(Threads.MESSAGE_ID);
                values.remove(Threads.ENCRYPTED);
                values.remove(Threads.DIRECTION);
            }

            values.remove(Threads.PEER);
            db.update(TABLE_THREADS, values, "peer = ? COLLATE NOCASE", new String[] { peer });
            // the client did not pass the thread id, query for it manually
            if (threadId < 0) {
                Cursor c = db.query(TABLE_THREADS, new String[] { Threads._ID }, "peer = ? COLLATE NOCASE", new String[] { peer }, null, null, null);
                if (c.moveToFirst())
                    threadId = c.getLong(0);
                c.close();
            }
        }

        return threadId;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        String table;
        String where;
        String[] args;
        boolean requestOnly = false;

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
                // WARNING selectionArgs is not supported yet
                if (selection != null)
                    where += " AND (" + selection + ")";
                args = new String[] { String.valueOf(_id) };
                break;
            }

            case MESSAGES_SERVERID: {
                String messageId = uri.getPathSegments().get(1);
                table = TABLE_MESSAGES;
                where = Messages.MESSAGE_ID + " = ?";
                // WARNING selectionArgs is not supported yet
                if (selection != null)
                    where += " AND (" + selection + ")";
                args = new String[] { String.valueOf(messageId) };
                break;
            }

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

            case REQUESTS: {
                table = TABLE_THREADS;
                where = selection;
                args = selectionArgs;
                requestOnly = true;
                break;
            }

            case GROUPS: {
                table = TABLE_GROUPS;
                where = selection;
                args = selectionArgs;
                break;
            }

            case GROUPS_ID: {
                table = TABLE_GROUPS;
                String groupId = uri.getLastPathSegment();
                where = Groups.GROUP_JID + " = ? COLLATE NOCASE";
                args = new String[] { groupId };
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;
            }

            case GROUPS_MEMBERS: {
                String groupId = uri.getPathSegments().get(1);
                table = TABLE_GROUP_MEMBERS;
                where = Groups.GROUP_JID + " = ? COLLATE NOCASE";
                args = new String[] { groupId };
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;
            }

            case GROUPS_MEMBERS_ID: {
                table = TABLE_GROUP_MEMBERS;
                where = Groups.GROUP_JID + " = ? COLLATE NOCASE AND " + Groups.PEER + " = ? COLLATE NOCASE";
                args = new String[] { uri.getPathSegments().get(1), uri.getLastPathSegment() };
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;
            }

            case IMPORT_LOCK: {
                dbHelper.lock();
                return 0;
            }

            case IMPORT_UNLOCK: {
                dbHelper.unlock();
                return 0;
            }

            case RELOAD: {
                dbHelper.close();
                try {
                    onCreate();
                    dbHelper.getReadableDatabase();
                }
                catch (Exception e) {
                    // restart from scratch
                    getContext().deleteDatabase(DatabaseHelper.DATABASE_NAME);
                    onCreate();
                    throw new SQLiteException(e.toString());
                }
                return 0;
            }

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        List<Uri> notifications = null;
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        db.beginTransactionNonExclusive();

        try {
            // handle clear pending flags
            String pendingFlags = uri.getQueryParameter(Messages.CLEAR_PENDING);
            if (pendingFlags != null) {
                updatePendingFlags(db, Integer.parseInt(pendingFlags), where, args);
                db.setTransactionSuccessful();
                return 0;
            }

            // retrieve old data for notifying.
            // This was done because of the update call could make the old where
            // condition not working any more.
            boolean skipUpdate = false;
            String[] msgIdList = null;
            if (table.equals(TABLE_MESSAGES)) {
                // preserve a list of the matching messages for notification and
                // fulltext update later
                Cursor old = db.query(TABLE_MESSAGES, new String[] { Messages._ID },
                        where, args, null, null, null);
                int msgCount = old.getCount();
                if (msgCount > 0) {
                    msgIdList = new String[msgCount];
                    int i = 0;
                    while (old.moveToNext()) {
                        msgIdList[i] = old.getString(0);
                        i++;
                    }
                }
                else {
                    // will skip update if now message was found (what's the point?)
                    skipUpdate = true;
                }

                old.close();
            }

            int rows = 0;
            if (!skipUpdate)
                rows = db.update(table, values, where, args);

            // notify change only if rows are actually affected
            if (rows > 0) {
                if (requestOnly)
                    uri = Threads.CONTENT_URI;

                notifications = new ArrayList<>();
                notifications.add(uri);

                if (table.equals(TABLE_MESSAGES)) {
                    // update fulltext only if content actually changed
                    boolean doUpdateFulltext;
                    String[] projection;

                    byte[] oldContent = values.getAsByteArray(Messages.BODY_CONTENT);
                    if (oldContent != null) {
                        doUpdateFulltext = true;
                        projection = new String[] { Messages.THREAD_ID, Messages._ID,
                                Messages.DIRECTION, Messages.ENCRYPTED,
                                Messages.BODY_CONTENT };
                    }
                    else {
                        doUpdateFulltext = false;
                        projection = new String[] { Messages.THREAD_ID };
                    }

                    // build new IN where condition
                    if (msgIdList != null) {    // a non-null array means at least 1 element
                        StringBuilder whereBuilder = new StringBuilder(Messages._ID + " IN (?");
                        for (int i = 1; i < msgIdList.length; i++)
                            whereBuilder.append(",?");
                        whereBuilder.append(")");

                        Cursor c = db.query(TABLE_MESSAGES, projection,
                                whereBuilder.toString(), msgIdList, null, null, Messages.THREAD_ID);

                        long oldThreadId = 0;
                        while (c.moveToNext()) {
                            long threadId = c.getLong(0);
                            if (oldThreadId != threadId) {
                                updateThreadInfo(db, threadId, notifications);
                                oldThreadId = threadId;
                            }

                            // update fulltext if necessary
                            if (doUpdateFulltext) {
                                int direction = c.getInt(2);
                                int encrypted = c.getInt(3);
                                if (direction != Messages.DIRECTION_IN || encrypted == 0)
                                    updateFulltext(db, c.getLong(1), threadId, c.getBlob(4));
                            }
                        }

                        c.close();
                    }
                }

                // delete thread if no messages are found
                else if (requestOnly) {

                    Cursor th = db.query(TABLE_THREADS, new String[] { Threads.COUNT },
                            where, args, null, null, null);

                    if (th.moveToFirst() && th.getInt(0) == 0)
                        db.delete(TABLE_THREADS, where, args);

                    th.close();

                }
            }

            db.setTransactionSuccessful();
            return rows;
        }
        finally {
            db.endTransaction();
            if (notifications != null) {
                ContentResolver cr = getContext().getContentResolver();
                for (Uri nuri : notifications)
                    cr.notifyChange(nuri, null);
            }
        }
    }

    /** Updates group status pending flags. */
    private void updatePendingFlags(SQLiteDatabase db, int flags, String where, String[] args) {
        db.execSQL("UPDATE " + TABLE_GROUP_MEMBERS + " SET pending = pending & ~("+flags+") WHERE " + where, args);
    }

    private void updateFulltext(SQLiteDatabase db, long id, long threadId, byte[] content) {
        // use the binary content converted to string
        String text = new String(content);

        ContentValues fulltext = new ContentValues();
        fulltext.put(Fulltext._ID, id);
        fulltext.put(Fulltext.THREAD_ID, threadId);
        fulltext.put(Fulltext.CONTENT, text);
        db.replace(TABLE_FULLTEXT, null, fulltext);
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
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
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;

            case MESSAGES_SERVERID:
                table = TABLE_MESSAGES;
                String sid = uri.getPathSegments().get(1);
                where = "msg_id = ?";
                args = new String[] { String.valueOf(sid) };
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
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
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;

            case THREADS_PEER:
                table = TABLE_THREADS;
                where = "peer = ? COLLATE NOCASE";
                args = new String[] { uri.getLastPathSegment() };
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;

            case GROUPS_ID:
                table = TABLE_GROUPS;
                where = Groups.GROUP_JID + "=? COLLATE NOCASE";
                args = new String[] { uri.getLastPathSegment() };
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;

            case GROUPS_MEMBERS_ID:
                table = TABLE_GROUP_MEMBERS;
                where = Groups.GROUP_JID + " = ? COLLATE NOCASE AND " + Groups.PEER + " = ? COLLATE NOCASE";
                args = new String[] { uri.getPathSegments().get(1), uri.getLastPathSegment() };
                if (selection != null) {
                    where += " AND (" + selection + ")";
                    if (selectionArgs != null)
                        args = SystemUtils.concatenate(args, selectionArgs);
                }
                break;

            // special case: conversations
            case CONVERSATIONS_ID: {
                boolean keepGroup = Boolean.parseBoolean(uri.getQueryParameter(Messages.KEEP_GROUP));
                int rows = deleteConversation(uri, keepGroup);
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
                boolean keepGroup = Boolean.parseBoolean(uri.getQueryParameter(Messages.KEEP_GROUP));

                int num = 0;
                db.beginTransactionNonExclusive();
                try {
                    // rows count will be conversations
                    num = db.delete(TABLE_THREADS, null, null);
                    db.delete(TABLE_MESSAGES, null, null);
                    // update fulltext
                    db.delete(TABLE_FULLTEXT, null, null);
                    if (!keepGroup) {
                        // delete groups (members will cascade)
                        db.delete(TABLE_GROUPS, null, null);
                    }

                    // set transaction successful
                    db.setTransactionSuccessful();
                }
                finally {
                    db.endTransaction();
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
        List<Uri> notifications = new ArrayList<>();

        // let's begin this big transaction :S
        db.beginTransactionNonExclusive();

        try {

            long threadId = -1;
            if (table.equals(TABLE_MESSAGES)) {
                // retrieve the thread id for later use by updateThreadInfo(), and
                // also update fulltext table
                Cursor c = db.query(TABLE_MESSAGES, new String[] {
                        Messages.THREAD_ID,
                        Messages._ID,
                        Messages.DIRECTION,
                        Messages.ENCRYPTED
                    },
                    where, args, null, null, null);
                if (c != null) {
                    while (c.moveToNext()) {
                        // FIXME this way we'll only get one threadId...
                        threadId = c.getLong(0);

                        // update fulltext
                        int direction = c.getInt(2);
                        int encrypted = c.getInt(3);
                        if (direction != Messages.DIRECTION_IN || encrypted == 0)
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
                updateThreadAfterDelete(db, threadId, notifications);
            }

            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
            ContentResolver cr = getContext().getContentResolver();
            for (Uri nuri : notifications)
                cr.notifyChange(nuri, null);
        }

        return rows;
    }

    private void updateThreadAfterDelete(SQLiteDatabase db, long threadId, @Nullable List<Uri> notifications) {
        // check for empty threads
        if (deleteEmptyThreads(db) > 0 && notifications != null)
            notifications.add(Threads.CONTENT_URI);
        // update thread with latest info and status
        if (threadId > 0) {
            updateThreadInfo(db, threadId, notifications);
        }
        else
            Log.e(TAG, "unable to update thread metadata (threadId not found)");
        // change notifications get triggered by previous method calls
    }

    private int deleteConversation(Uri uri, boolean keepGroup) {
        long threadId = ContentUris.parseId(uri);
        if (threadId > 0) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            db.beginTransactionNonExclusive();

            try {
                int num = 0;

                if (!keepGroup)
                    num = db.delete(TABLE_THREADS, Threads._ID + " = " + threadId, null);

                // exclude group commands from delete if we are keeping the group
                String where = Messages.THREAD_ID + " = " + threadId;
                String[] args = null;
                if (keepGroup) {
                    where += " AND (" + Messages.BODY_MIME + " <> ? OR " + Messages.BODY_MIME + " IS NULL)";
                    args = new String[] { GroupCommandComponent.MIME_TYPE };
                }
                num += db.delete(TABLE_MESSAGES, where, args);

                if (!keepGroup)
                    // delete group if requested
                    num += db.delete(TABLE_GROUPS, Groups.THREAD_ID + " = " + threadId, null);

                // update fulltext
                db.delete(TABLE_FULLTEXT, Messages.THREAD_ID + " = " + threadId, null);

                // update thread information
                updateThreadAfterDelete(db, threadId, null);

                // set transaction successful
                db.setTransactionSuccessful();

                return num;
            }
            finally {
                db.endTransaction();
            }
        }

        return -1;
    }

    /** Updates metadata of a given thread. */
    private void updateThreadInfo(SQLiteDatabase db, long threadId, @Nullable List<Uri> notifications) {
        Cursor c = db.query(TABLE_MESSAGES_GROUPS, new String[] {
                Messages.MESSAGE_ID,
                Messages.DIRECTION,
                Messages.STATUS,
                Messages.BODY_CONTENT,
                Messages.BODY_MIME,
                Messages.ATTACHMENT_MIME,
                Messages.TIMESTAMP,
                Messages.SERVER_TIMESTAMP,
                Messages.PEER,
                Groups.GROUP_JID,
            }, Messages.THREAD_ID + " = ?", new String[] { String.valueOf(threadId) },
            null, null, Messages.INVERTED_SORT_ORDER, "1");

        if (c != null) {
            ContentValues v = new ContentValues();
            if (c.moveToFirst()) {
                int direction = c.getInt(1);
                v.put(Threads.MESSAGE_ID, c.getString(0));
                v.put(Threads.DIRECTION, direction);
                v.put(Threads.STATUS, c.getInt(2));

                String groupJid = c.getString(9);
                String peer = (groupJid != null && direction == Messages.DIRECTION_IN) ? c.getString(8) : null;
                setThreadContent(c.getBlob(3), c.getString(4), c.getString(5), peer, v);

                // use server timestamp if present
                long ts = c.getLong(7);
                v.put(Threads.TIMESTAMP, ts > 0 ? ts : c.getLong(6));
            }
            else {
                // empty thread data
                v.put(Threads.MESSAGE_ID, "draft" + (new Random().nextInt()));
                v.put(Threads.DIRECTION, Messages.DIRECTION_OUT);
                v.put(Threads.TIMESTAMP, System.currentTimeMillis());
                v.putNull(Threads.STATUS);
                setThreadContent(new byte[0], TextComponent.MIME_TYPE, null, null, v);
            }
            c.close();

            // extract counters now
            c = db.rawQuery("SELECT SUM(unread), SUM(\"new\") FROM " + TABLE_MESSAGES + " WHERE " +
                    Messages.THREAD_ID + " = ?",
                new String[] { String.valueOf(threadId) });
            if (c != null) {
                if (c.moveToFirst()) {
                    v.put(Threads.UNREAD, c.getLong(0));
                    v.put(Threads.NEW, c.getLong(1));
                }
                c.close();
            }

            db.update(TABLE_THREADS, v, Threads._ID + "=" + threadId, null);
            if (notifications != null) {
                notifications.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
                notifications.add(ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId));
            }
        }
    }

    private int deleteEmptyThreads(SQLiteDatabase db) {
        return db.delete(TABLE_THREADS, "\"" + Threads.COUNT + "\"" + " = 0 AND " +
                Threads.DRAFT + " IS NULL AND " +
                "NOT EXISTS (SELECT 1 FROM " + TABLE_GROUPS +
                    " WHERE "+TABLE_THREADS+"."+Threads._ID+"="+Groups.THREAD_ID+")", null);
    }

    @Override
    public String getType(@NonNull Uri uri) {
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

    public static File getDatabaseUri(Context context) {
        return context.getDatabasePath(DatabaseHelper.DATABASE_NAME);
    }

    public static void reload(Context context) {
        context.getContentResolver().update(Uri
            .parse("content://" + MessagesProvider.AUTHORITY + "/" + Messages.RELOAD),
            null, null, null);
    }

    public static void lockForImport(Context context) {
        context.getContentResolver().update(Uri
                .parse("content://" + MessagesProvider.AUTHORITY + "/" + Messages.IMPORT_LOCK),
            null, null, null);
    }

    public static void unlockForImport(Context context) {
        context.getContentResolver().update(Uri
                .parse("content://" + MessagesProvider.AUTHORITY + "/" + Messages.IMPORT_UNLOCK),
            null, null, null);
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
        sUriMatcher.addURI(AUTHORITY, TABLE_GROUPS, GROUPS);
        sUriMatcher.addURI(AUTHORITY, TABLE_GROUPS + "/*", GROUPS_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_GROUPS + "/*/members", GROUPS_MEMBERS);
        sUriMatcher.addURI(AUTHORITY, TABLE_GROUPS + "/*/members/*", GROUPS_MEMBERS_ID);
        sUriMatcher.addURI(AUTHORITY, TABLE_FULLTEXT, FULLTEXT_ID);
        sUriMatcher.addURI(AUTHORITY, "requests", REQUESTS);
        sUriMatcher.addURI(AUTHORITY, Messages.IMPORT_LOCK, IMPORT_LOCK);
        sUriMatcher.addURI(AUTHORITY, Messages.IMPORT_UNLOCK, IMPORT_UNLOCK);
        sUriMatcher.addURI(AUTHORITY, Messages.RELOAD, RELOAD);

        messagesProjectionMap = new HashMap<>();
        messagesProjectionMap.put(Messages._ID, Messages._ID);
        messagesProjectionMap.put(Messages.THREAD_ID, Messages.THREAD_ID);
        messagesProjectionMap.put(Messages.MESSAGE_ID, Messages.MESSAGE_ID);
        messagesProjectionMap.put(Messages.PEER, Messages.PEER);

        messagesProjectionMap.put(Messages.BODY_MIME, Messages.BODY_MIME);
        messagesProjectionMap.put(Messages.BODY_CONTENT, Messages.BODY_CONTENT);
        messagesProjectionMap.put(Messages.BODY_LENGTH, Messages.BODY_LENGTH);

        messagesProjectionMap.put(Messages.ATTACHMENT_MIME, Messages.ATTACHMENT_MIME);
        messagesProjectionMap.put(Messages.ATTACHMENT_PREVIEW_PATH, Messages.ATTACHMENT_PREVIEW_PATH);
        messagesProjectionMap.put(Messages.ATTACHMENT_FETCH_URL, Messages.ATTACHMENT_FETCH_URL);
        messagesProjectionMap.put(Messages.ATTACHMENT_LOCAL_URI, Messages.ATTACHMENT_LOCAL_URI);
        messagesProjectionMap.put(Messages.ATTACHMENT_LENGTH, Messages.ATTACHMENT_LENGTH);
        messagesProjectionMap.put(Messages.ATTACHMENT_COMPRESS, Messages.ATTACHMENT_COMPRESS);
        messagesProjectionMap.put(Messages.ATTACHMENT_ENCRYPTED, Messages.ATTACHMENT_ENCRYPTED);
        messagesProjectionMap.put(Messages.ATTACHMENT_SECURITY_FLAGS, Messages.ATTACHMENT_SECURITY_FLAGS);

        messagesProjectionMap.put(Messages.GEO_LATITUDE, Messages.GEO_LATITUDE);
        messagesProjectionMap.put(Messages.GEO_LONGITUDE, Messages.GEO_LONGITUDE);
        messagesProjectionMap.put(Messages.GEO_TEXT, Messages.GEO_TEXT);
        messagesProjectionMap.put(Messages.GEO_STREET, Messages.GEO_STREET);

        messagesProjectionMap.put(Messages.UNREAD, Messages.UNREAD);
        messagesProjectionMap.put(Messages.NEW, Messages.NEW);
        messagesProjectionMap.put(Messages.DIRECTION, Messages.DIRECTION);
        messagesProjectionMap.put(Messages.TIMESTAMP, Messages.TIMESTAMP);
        messagesProjectionMap.put(Messages.STATUS_CHANGED, Messages.STATUS_CHANGED);
        messagesProjectionMap.put(Messages.STATUS, Messages.STATUS);
        messagesProjectionMap.put(Messages.ENCRYPTED, Messages.ENCRYPTED);
        messagesProjectionMap.put(Messages.SECURITY_FLAGS, Messages.SECURITY_FLAGS);
        messagesProjectionMap.put(Messages.SERVER_TIMESTAMP, Messages.SERVER_TIMESTAMP);
        messagesProjectionMap.put(Messages.IN_REPLY_TO, Messages.IN_REPLY_TO);
        messagesProjectionMap.put(Groups.GROUP_JID, Groups.GROUP_JID);
        messagesProjectionMap.put(Groups.SUBJECT, Groups.SUBJECT);
        messagesProjectionMap.put(Groups.GROUP_TYPE, Groups.GROUP_TYPE);
        messagesProjectionMap.put(Groups.MEMBERSHIP, Groups.MEMBERSHIP);

        threadsProjectionMap = new HashMap<>();
        threadsProjectionMap.put(Threads._ID, Threads._ID);
        threadsProjectionMap.put(Threads.MESSAGE_ID, Threads.MESSAGE_ID);
        threadsProjectionMap.put(Threads.PEER, Threads.PEER);
        threadsProjectionMap.put(Threads.DIRECTION, Threads.DIRECTION);
        threadsProjectionMap.put(Threads.COUNT, Threads.COUNT);
        threadsProjectionMap.put(Threads.UNREAD, Threads.UNREAD);
        threadsProjectionMap.put(Threads.NEW, Threads.NEW);
        threadsProjectionMap.put(Threads.MIME, Threads.MIME);
        threadsProjectionMap.put(Threads.CONTENT, Threads.CONTENT);
        threadsProjectionMap.put(Threads.TIMESTAMP, Threads.TIMESTAMP);
        threadsProjectionMap.put(Threads.STATUS_CHANGED, Threads.STATUS_CHANGED);
        threadsProjectionMap.put(Threads.STATUS, Threads.STATUS);
        threadsProjectionMap.put(Threads.ENCRYPTED, Threads.ENCRYPTED);
        threadsProjectionMap.put(Threads.DRAFT, Threads.DRAFT);
        threadsProjectionMap.put(Threads.REQUEST_STATUS, Threads.REQUEST_STATUS);
        threadsProjectionMap.put(Threads.STICKY, Threads.STICKY);
        threadsProjectionMap.put(Threads.ENCRYPTION, Threads.ENCRYPTION);
        threadsProjectionMap.put(Threads.ARCHIVED, Threads.ARCHIVED);
        threadsProjectionMap.put(Groups.GROUP_JID, Groups.GROUP_JID);
        threadsProjectionMap.put(Groups.SUBJECT, Groups.SUBJECT);
        threadsProjectionMap.put(Groups.GROUP_TYPE, Groups.GROUP_TYPE);
        threadsProjectionMap.put(Groups.MEMBERSHIP, Groups.MEMBERSHIP);

        fulltextProjectionMap = new HashMap<>();
        fulltextProjectionMap.put(Fulltext._ID, Fulltext._ID);
        fulltextProjectionMap.put(Fulltext.THREAD_ID, Fulltext.THREAD_ID);
        fulltextProjectionMap.put(Fulltext.TIMESTAMP, Fulltext.TIMESTAMP);
        fulltextProjectionMap.put(Fulltext.CONTENT, Fulltext.CONTENT);

        groupsProjectionMap = new HashMap<>();
        groupsProjectionMap.put(Groups.GROUP_JID, Groups.GROUP_JID);
        groupsProjectionMap.put(Groups.THREAD_ID, Groups.THREAD_ID);
        groupsProjectionMap.put(Groups.GROUP_TYPE, Groups.GROUP_TYPE);
        groupsProjectionMap.put(Groups.SUBJECT, Groups.SUBJECT);
        groupsProjectionMap.put(Groups.MEMBERSHIP, Groups.MEMBERSHIP);

        groupsMembersProjectionMap = new HashMap<>();
        groupsMembersProjectionMap.put(Groups.GROUP_JID, Groups.GROUP_JID);
        groupsMembersProjectionMap.put(Groups.PEER, Groups.PEER);
        groupsMembersProjectionMap.put(Groups.PENDING, Groups.PENDING);
    }
}

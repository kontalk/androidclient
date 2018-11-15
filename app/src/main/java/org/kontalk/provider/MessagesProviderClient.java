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
import java.util.Random;

import org.jxmpp.jid.impl.JidCreate;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteConstraintException;
import android.net.Uri;

import org.kontalk.Log;
import org.kontalk.crypto.Coder;
import org.kontalk.data.GroupInfo;
import org.kontalk.message.LocationComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.group.KontalkGroupController;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;


/**
 * Utility class for interacting with the {@link MessagesProvider}.
 * @author Daniele Ricci
 */
public class MessagesProviderClient {

    private static final String[] LATEST_THREADS_PROJ = {
        Threads._ID,
        Threads.PEER,
    };

    public static final int LATEST_THREADS_COLUMN_ID = 0;
    public static final int LATEST_THREADS_COLUMN_PEER = 1;

    private MessagesProviderClient() {
    }

    /** Checks if the message lives. */
    public static boolean exists(Context context, long msgId) {
        boolean b = false;
        Cursor c = context.getContentResolver().
            query(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                null, null, null, null);
        if (c.moveToFirst())
            b = true;
        c.close();
        return b;
    }

    public static Cursor getLatestThreads(Context context, boolean includeGroups, int limit) {
        return context.getContentResolver().query(Threads.CONTENT_URI.buildUpon()
                .appendQueryParameter("limit", String.valueOf(limit)).build(),
            LATEST_THREADS_PROJ, includeGroups ? null : Groups.GROUP_JID + " IS NULL", null,
            Threads.DEFAULT_SORT_ORDER);
    }

    /** Inserts a new outgoing text message. */
    public static Uri newOutgoingMessage(Context context, String msgId, String userId,
            String text, boolean encrypted, long inReplyTo) {

        byte[] bytes = text.getBytes();
        ContentValues values = new ContentValues(11);
        // must supply a message ID...
        values.put(Messages.MESSAGE_ID, msgId);
        values.put(Messages.PEER, userId);
        values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
        values.put(Messages.BODY_CONTENT, bytes);
        values.put(Messages.BODY_LENGTH, bytes.length);
        values.put(Messages.UNREAD, false);
        values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(Messages.ENCRYPTED, false);
        values.put(Threads.ENCRYPTION, encrypted);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        if (inReplyTo > 0)
            values.put(Messages.IN_REPLY_TO, inReplyTo);
        return context.getContentResolver().insert(
            Messages.CONTENT_URI, values);
    }

    /** Inserts a new outgoing binary message. */
    public static Uri newOutgoingMessage(Context context, String msgId, String userId,
            String mime, Uri uri, long length, int compress, File previewFile, boolean encrypted) {
        ContentValues values = new ContentValues(13);
        values.put(Messages.MESSAGE_ID, msgId);
        values.put(Messages.PEER, userId);

        /* TODO one day we'll ask for a text to send with the image
        values.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
        values.put(Messages.BODY_CONTENT, content.getBytes());
        values.put(Messages.BODY_LENGTH, content.length());
         */

        values.put(Messages.UNREAD, false);
        // of course outgoing messages are not encrypted in database
        values.put(Messages.ENCRYPTED, false);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(Messages.STATUS, Messages.STATUS_QUEUED);

        if (previewFile != null)
            values.put(Messages.ATTACHMENT_PREVIEW_PATH, previewFile.getAbsolutePath());

        values.put(Messages.ATTACHMENT_MIME, mime);
        values.put(Messages.ATTACHMENT_LOCAL_URI, uri.toString());
        values.put(Messages.ATTACHMENT_LENGTH, length);
        values.put(Messages.ATTACHMENT_COMPRESS, compress);

        return context.getContentResolver().insert(Messages.CONTENT_URI, values);
    }

    /** Inserts a new outgoing location message. */
    public static Uri newOutgoingMessage(Context context, String msgId, String userId,
                                         String text, double lat, double lon, String geoText, String geoStreet, boolean encrypted) {

        byte[] bytes = text.getBytes();
        ContentValues values = new ContentValues(11);
        // must supply a message ID...
        values.put(Messages.MESSAGE_ID, msgId);
        values.put(Messages.PEER, userId);
        values.put(Messages.BODY_MIME, LocationComponent.MIME_TYPE);
        values.put(Messages.BODY_CONTENT, bytes);
        values.put(Messages.BODY_LENGTH, bytes.length);
        values.put(Messages.UNREAD, false);
        values.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        values.put(Messages.GEO_LATITUDE, lat);
        values.put(Messages.GEO_LONGITUDE, lon);
        if (geoText != null)
            values.put(Messages.GEO_TEXT, geoText);
        if (geoStreet != null)
            values.put(Messages.GEO_STREET, geoStreet);
        // of course outgoing messages are not encrypted in database
        values.put(Messages.ENCRYPTED, false);
        values.put(Threads.ENCRYPTION, encrypted);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().insert(
                Messages.CONTENT_URI, values);
    }

    public static Uri newIncomingMessage(Context context, ContentValues values) {
        try {
            return context.getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
        } catch (SQLiteConstraintException econstr) {
            // duplicated message, skip it
            return null;
        }
    }

    public static Uri newChatRequest(Context context, String jid) {
        ContentValues values = new ContentValues(2);
        values.put(Threads.PEER, jid);
        values.put(Threads.TIMESTAMP, System.currentTimeMillis());
        return context.getContentResolver()
            .insert(Threads.Requests.CONTENT_URI, values);
    }

    /** Returns the thread associated with the given message. */
    public static long getThreadByMessage(Context context, Uri message) {
        Cursor c = context.getContentResolver().query(message,
            new String[] { Messages.THREAD_ID }, null, null,
            null);
        try {
            if (c.moveToFirst())
                return c.getLong(0);

            return Messages.NO_THREAD;
        }
        finally {
            c.close();
        }
    }

    /** Returns the thread associated with the given peer. */
    public static long findThread(Context context, String peer) {
        long threadId = 0;
        Cursor cp = context.getContentResolver().query(MyMessages.Messages.CONTENT_URI,
            new String[] { MyMessages.Messages.THREAD_ID }, MyMessages.Messages.PEER
                + " = ? COLLATE NOCASE", new String[] { peer }, null);
        if (cp != null) {
            if (cp.moveToFirst())
                threadId = cp.getLong(0);
            cp.close();
        }
        return threadId;
    }

    public static int updateDraft(Context context, long threadId, String draft) {
        ContentValues values = new ContentValues(1);
        if (draft != null && draft.length() > 0)
            values.put(Threads.DRAFT, draft);
        else
            values.putNull(Threads.DRAFT);
        return context.getContentResolver().update(
            ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
            values, null, null);
    }

    public static boolean deleteDatabase(Context ctx) {
        try {
            ContentResolver c = ctx.getContentResolver();
            c.delete(Threads.Conversations.CONTENT_URI, null, null);
            return true;
        }
        catch (Exception e) {
            Log.e(MessagesProvider.TAG, "error during database delete!", e);
            return false;
        }
    }

    /**
     * Marks all messages of the given thread as read.
     * @param context used to request a {@link ContentResolver}
     * @param id the thread id
     * @return the number of rows affected in the messages table
     */
    public static int markThreadAsRead(Context context, long id) {
        ContentResolver c = context.getContentResolver();
        ContentValues values = new ContentValues(2);
        values.put(Messages.UNREAD, Boolean.FALSE);
        values.put(Messages.NEW, Boolean.FALSE);
        return c.update(Messages.CONTENT_URI, values,
                Messages.THREAD_ID + " = ? AND " +
                Messages.UNREAD + " <> 0 AND " +
                Messages.DIRECTION + " = " + Messages.DIRECTION_IN,
                new String[] { String.valueOf(id) });
    }

    /**
     * Marks all messages of the given thread as read.
     * @param context used to request a {@link ContentResolver}
     * @param peer the thread peer
     * @return the number of rows affected in the messages table
     */
    public static int markThreadAsRead(Context context, String peer) {
        ContentResolver c = context.getContentResolver();
        ContentValues values = new ContentValues(2);
        values.put(Messages.UNREAD, Boolean.FALSE);
        values.put(Messages.NEW, Boolean.FALSE);
        return c.update(Messages.CONTENT_URI, values,
            Messages.PEER + " = ? COLLATE NOCASE AND " +
                Messages.UNREAD + " <> 0 AND " +
                Messages.DIRECTION + " = " + Messages.DIRECTION_IN,
            new String[] { peer });
    }

    /**
     * Marks all messages as read.
     * @param context used to request a {@link ContentResolver}
     * @return the number of rows affected in the messages table
     */
    public static int markAllThreadsAsRead(Context context) {
        ContentResolver c = context.getContentResolver();
        ContentValues values = new ContentValues(1);
        values.put(Messages.NEW, Boolean.FALSE);
        values.put(Messages.UNREAD, Boolean.FALSE);
        return c.update(Messages.CONTENT_URI, values,
            Messages.UNREAD + " <> 0 AND " +
                Messages.DIRECTION + " = " + Messages.DIRECTION_IN,
            null);
    }

    public static int markThreadAsOld(Context context, long id) {
        ContentResolver c = context.getContentResolver();
        ContentValues values = new ContentValues(1);
        values.put(Messages.NEW, Boolean.FALSE);
        return c.update(Messages.CONTENT_URI, values,
            Messages.THREAD_ID + " = ? AND " +
            Messages.NEW + " <> 0 AND " +
            Messages.DIRECTION + " = " + Messages.DIRECTION_IN,
            new String[] { String.valueOf(id) });
    }

    /**
     * Marks all messages as old.
     * @param context used to request a {@link ContentResolver}
     * @return the number of rows affected in the messages table
     */
    public static int markAllThreadsAsOld(Context context) {
        ContentResolver c = context.getContentResolver();
        ContentValues values = new ContentValues(1);
        values.put(Messages.NEW, Boolean.FALSE);
        return c.update(Messages.CONTENT_URI, values,
                Messages.NEW + " <> 0 AND " +
                Messages.DIRECTION + " = " + Messages.DIRECTION_IN,
                null);
    }

    public static final class MessageUpdater {
        private Uri mUri;
        private Context mContext;
        private ContentValues mValues;
        private String mWhere;
        private int mSound;
        private String mCheckPaused;

        public static MessageUpdater forMessage(Context context, long id) {
            return new MessageUpdater(context, Messages.getUri(id));
        }

        public static MessageUpdater forMessage(Context context, String id, boolean incoming) {
            MessageUpdater u = new MessageUpdater(context, Messages.getUri(id));
            if (incoming)
                u.incomingOnly();
            else
                u.outgoingOnly();
            return u;
        }

        private MessageUpdater(Context context, Uri uri) {
            mUri = uri;
            mContext = context;
            mValues = new ContentValues();
        }

        public MessageUpdater setStatus(int status) {
            mValues.put(Messages.STATUS, status);
            return this;
        }

        public MessageUpdater setStatus(int status, long timestamp) {
            setStatus(status);
            mValues.put(Messages.STATUS_CHANGED, timestamp);
            return this;
        }

        public MessageUpdater setServerTimestamp(long timestamp) {
            mValues.put(Messages.SERVER_TIMESTAMP, timestamp);
            return this;
        }

        public MessageUpdater appendWhere(String where) {
            mWhere = DatabaseUtils.concatenateWhere(mWhere, where);
            return this;
        }

        public MessageUpdater outgoingOnly() {
            mWhere = Messages.DIRECTION + "=" + Messages.DIRECTION_OUT;
            return this;
        }

        public MessageUpdater incomingOnly() {
            mWhere = Messages.DIRECTION + "=" + Messages.DIRECTION_IN;
            return this;
        }

        /**
         * We will notify of outgoing message handover to the server.
         * @param jid we will check if notification are paused for this user
         */
        public MessageUpdater notifyOutgoing(String jid) {
            mSound = MediaStorage.OUTGOING_MESSAGE_SOUND;
            mCheckPaused = jid;
            return this;
        }

        public void commit() {
            if (mSound > 0 && Preferences.getOutgoingSoundEnabled(mContext) &&
                    MessagingNotification.isPaused(mCheckPaused))
                MediaStorage.playNotificationSound(mContext, mSound);
            mContext.getContentResolver().update(mUri, mValues, mWhere, null);
        }

        public void clear() {
            mValues.clear();
        }
    }

    /**
     * Fills a media message with preview file and local uri, for use e.g.
     * after compressing. Also updates the message status to SENDING.
     */
    public static void updateMedia(Context context, long id, String previewFile, Uri localUri, long length) {
        ContentValues values = new ContentValues(3);
        values.put(Messages.ATTACHMENT_PREVIEW_PATH, previewFile);
        values.put(Messages.ATTACHMENT_LOCAL_URI, localUri.toString());
        values.put(Messages.ATTACHMENT_LENGTH, length);
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        context.getContentResolver().update(ContentUris
            .withAppendedId(Messages.CONTENT_URI, id), values, null, null);
    }

    /** Set the fetch URL of a media message, marking it as uploaded. */
    public static void uploaded(Context context, long msgId, String fetchUrl) {
        ContentValues values = new ContentValues(1);
        values.put(Messages.ATTACHMENT_FETCH_URL, fetchUrl);
        context.getContentResolver().update(Messages.CONTENT_URI, values,
            Messages._ID + " = " + msgId, null);
    }

    /** Set the local Uri of a media message, marking it as downloaded. */
    public static void downloaded(Context context, long msgId, Uri localUri) {
        ContentValues values = new ContentValues(1);
        values.put(Messages.ATTACHMENT_LOCAL_URI, localUri.toString());
        context.getContentResolver().update(ContentUris
            .withAppendedId(Messages.CONTENT_URI, msgId), values, null, null);
    }

    public static void deleteMessage(Context context, long id) {
        context.getContentResolver().delete(ContentUris
            .withAppendedId(Messages.CONTENT_URI, id), null, null);
    }

    public static boolean deleteThread(Context context, long id, boolean keepGroup) {
        ContentResolver c = context.getContentResolver();
        return (c.delete(ContentUris.withAppendedId(Threads.Conversations.CONTENT_URI, id)
            .buildUpon().appendQueryParameter(Messages.KEEP_GROUP, String.valueOf(keepGroup))
            .build(), null, null) > 0);
    }

    public static void setThreadSticky(Context context, long id, boolean sticky) {
        ContentValues values = new ContentValues(1);
        values.put(Threads.STICKY, sticky);
        context.getContentResolver().update(
            ContentUris.withAppendedId(Threads.CONTENT_URI, id),
            values, null, null);
    }

    /** Marks the given message as SENDING, regardless of its current status. */
    public static int retryMessage(Context context, Uri uri, boolean encrypted) {
        ContentValues values = new ContentValues(2);
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().update(uri, values, null, null);
    }

    /** Marks all pending messages to the given recipient as SENDING. */
    public static int retryMessagesTo(Context context, String to) {
        Cursor c = context.getContentResolver().query(Messages.CONTENT_URI,
                new String[] { Messages._ID },
                Messages.PEER + "=? COLLATE NOCASE AND " + Messages.STATUS + "=" + Messages.STATUS_PENDING,
                new String[] { to },
                Messages._ID);

        while (c.moveToNext()) {
            long msgID = c.getLong(0);
            Uri msgURI = ContentUris.withAppendedId(Messages.CONTENT_URI, msgID);
            long threadID = getThreadByMessage(context, msgURI);
            if (threadID == Messages.NO_THREAD)
                continue;
            Uri threadURI = ContentUris.withAppendedId(Threads.CONTENT_URI, threadID);
            Cursor cThread = context.getContentResolver().query(threadURI,
                    new String[] { Threads.ENCRYPTION }, null, null,
                    null);
            if (cThread.moveToFirst()) {
                boolean encrypted = MessageUtils.sendEncrypted(context, cThread.getInt(0) != 0);
                retryMessage(context, msgURI, encrypted);
            }
            cThread.close();
        }

        int count = c.getCount();
        c.close();
        return count;
    }

    /** Marks all pending messages as SENDING. */
    public static int retryAllMessages(Context context) {
        boolean encrypted = Preferences.getEncryptionEnabled(context);
        ContentValues values = new ContentValues(2);
        values.put(Messages.STATUS, Messages.STATUS_SENDING);
        values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().update(Messages.CONTENT_URI, values,
            Messages.STATUS + "=" + Messages.STATUS_PENDING,
            null);
    }

    /** Inserts an empty thread (that is, with no messages). */
    public static long insertEmptyThread(Context context, String peer, String draft) {
        ContentValues msgValues = new ContentValues(9);
        // must supply a message ID...
        msgValues.put(Messages.MESSAGE_ID, "draft" + (new Random().nextInt()));
        // use group id as the peer
        msgValues.put(Messages.PEER, peer);
        msgValues.put(Messages.BODY_CONTENT, new byte[0]);
        msgValues.put(Messages.BODY_LENGTH, 0);
        msgValues.put(Messages.BODY_MIME, TextComponent.MIME_TYPE);
        msgValues.put(Messages.DIRECTION, Messages.DIRECTION_OUT);
        msgValues.put(Messages.TIMESTAMP, System.currentTimeMillis());
        msgValues.put(Messages.ENCRYPTED, false);
        if (draft != null)
            msgValues.put(Threads.DRAFT, draft);
        Uri newThread = context.getContentResolver().insert(Messages.CONTENT_URI, msgValues);
        return newThread != null ? ContentUris.parseId(newThread) : Messages.NO_THREAD;
    }

    public static long createGroupThread(Context context, String groupJid, String subject, String[] members, String draft) {
        // insert group
        ContentValues values = new ContentValues();
        values.put(Groups.GROUP_JID, groupJid);

        // create new conversation
        long threadId = insertEmptyThread(context, groupJid, draft);

        values.put(Groups.THREAD_ID, threadId);
        values.put(Groups.SUBJECT, subject);
        values.put(Groups.GROUP_TYPE, KontalkGroupController.GROUP_TYPE);
        context.getContentResolver().insert(Groups.CONTENT_URI, values);

        // remove values not for members table
        values.remove(Groups.GROUP_JID);
        values.remove(Groups.THREAD_ID);
        values.remove(Groups.SUBJECT);
        values.remove(Groups.GROUP_TYPE);

        // insert group members
        for (String member : members) {
            // FIXME turn this into batch operations
            values.put(Groups.PEER, member);
            context.getContentResolver()
                .insert(Groups.getMembersUri(groupJid), values);
        }

        return threadId;
    }

    public static void addGroupMembers(Context context, String groupJid, String[] members, boolean pending) {
        ContentValues values = new ContentValues();
        values.put(Groups.GROUP_JID, groupJid);
        for (String member : members) {
            // FIXME turn this into batch operations
            values.put(Groups.PEER, member);
            values.put(Groups.PENDING, pending ? Groups.MEMBER_PENDING_ADDED : 0);
            context.getContentResolver()
                .insert(Groups.getMembersUri(groupJid), values);
        }
    }

    public static void removeGroupMembers(Context context, String groupJid, String[] members, boolean pending) {
        if (pending) {
            ContentValues values = new ContentValues(1);
            values.put(Groups.PENDING, Groups.MEMBER_PENDING_REMOVED);
            for (String member : members) {
                // FIXME turn this into batch operations
                context.getContentResolver()
                    .update(Groups.getMembersUri(groupJid).buildUpon()
                        .appendPath(member).build(), values, null, null);
            }
        }
        else {
            for (String member : members) {
                // just beat it!
                context.getContentResolver()
                    .delete(Groups.getMembersUri(groupJid).buildUpon()
                        .appendPath(member).build(), null, null);
            }
        }
    }

    public static int setGroupSubject(Context context, String groupJid, String subject) {
        ContentValues values = new ContentValues();
        if (subject != null)
            values.put(Groups.SUBJECT, subject);
        else
            values.putNull(Groups.SUBJECT);

        return context.getContentResolver().update(Groups.getUri(groupJid),
            values, null, null);
    }

    public static boolean isGroupExisting(Context context, String groupJid) {
        Cursor c = context.getContentResolver().query(
            Groups.getUri(groupJid),
            new String[] { Groups.GROUP_JID }, null, null, null);
        boolean exist = c.moveToFirst();
        c.close();
        return exist;
    }

    public static GroupInfo getGroupInfo(Context context, String groupJid) {
        Cursor c = context.getContentResolver()
            .query(Groups.getUri(groupJid),
                new String[] {
                    Groups.SUBJECT,
                    Groups.GROUP_TYPE,
                    Groups.MEMBERSHIP
                },
                null, null, null);

        try {
            if (c.moveToNext()) {
                return new GroupInfo(JidCreate.fromOrThrowUnchecked(groupJid),
                    c.getString(0), c.getString(1), c.getInt(2));
            }
            return null;
        }
        finally {
            c.close();
        }
    }

    public static String[] getGroupMembers(Context context, String groupJid, int flags) {
        String where;
        if (flags > 0) {
            where = "(" + Groups.PENDING + " & " + flags + ") = " + flags;
        }
        else if (flags == 0) {
            // handle zero flags special case (means all flags cleared)
            where = Groups.PENDING + "=0";
        }
        else {
            // any flag
            where = null;
        }

        Cursor c = context.getContentResolver()
            .query(Groups.getMembersUri(groupJid),
                new String[] { Groups.PEER },
                where, null, null);

        String[] members = new String[c.getCount()];
        int i = 0;
        while (c.moveToNext()) {
            members[i++] = c.getString(0);
        }
        c.close();
        return members;
    }

    public static int setGroupMembership(Context context, String groupJid, int membership) {
        ContentValues values = new ContentValues(1);
        values.put(Groups.MEMBERSHIP, membership);
        return context.getContentResolver().update(Groups.getUri(groupJid),
            values, null, null);
    }

    /** Returns the current known membership of a user in a group. */
    public static boolean isGroupMember(Context context, String groupJid, String jid) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(Groups.getMembersUri(groupJid),
                new String[] { Groups.PENDING },  Groups.PEER + "=? COLLATE NOCASE", new String[] { jid }, null);
            return c.moveToNext() && c.getInt(0) == 0;
        }
        finally {
            if (c != null)
                c.close();
        }
    }

    public static final class GroupThreadContent {
        public final String sender;
        public final String command;

        /** Parse thread content text for a special case: incoming group command. */
        public GroupThreadContent(String sender, String command) {
            this.sender = sender;
            this.command = command;
        }

        public static GroupThreadContent parseIncoming(String content) {
            String[] parsed = content.split(";", 2);
            if (parsed.length < 2) {
                return new GroupThreadContent(null, content);
            }

            if (parsed[1].length() == 0)
                parsed[1] = null;

            return new GroupThreadContent(parsed[0], parsed[1]);
        }
    }

    public static int setEncryption(Context context, long threadId, boolean encryption) {
        ContentValues values = new ContentValues(1);
        values.put(Threads.ENCRYPTION, encryption);
        return context.getContentResolver().update(
            ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
            values, null, null);
    }

    public static int setArchived(Context context, long threadId, boolean archived) {
        ContentValues values = new ContentValues(1);
        values.put(Threads.ARCHIVED, archived);
        return context.getContentResolver().update(
            ContentUris.withAppendedId(Threads.CONTENT_URI, threadId),
            values, null, null);
    }

    public static int getPendingMessagesCount(Context context) {
        Cursor c = context.getContentResolver().query(
            Messages.CONTENT_URI, new String[] { Messages._COUNT },
            Messages.STATUS + " IN (" + Messages.STATUS_SENDING + ", " +
                Messages.STATUS_QUEUED + ")", null, null);
        if (c != null && c.moveToFirst()) {
            return c.getInt(0);
        }
        return 0;
    }

}

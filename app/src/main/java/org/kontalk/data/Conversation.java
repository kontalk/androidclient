/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.data;

import java.util.Random;

import org.jivesoftware.smack.util.StringUtils;

import org.kontalk.message.TextComponent;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.ui.MessagingNotification;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.util.Log;


/**
 * A class represeting a conversation thread.
 * @author Daniele Ricci
 * @version 1.0
 */
public class Conversation {

    private static final String[] ALL_THREADS_PROJECTION = {
        Threads._ID,
        Threads.PEER,
        Threads.COUNT,
        Threads.UNREAD,
        Threads.MIME,
        Threads.CONTENT,
        Threads.TIMESTAMP,
        Threads.STATUS,
        Threads.ENCRYPTED,
        Threads.DRAFT,
        Threads.REQUEST_STATUS,
        Threads.Groups.GROUP_ID,
        Threads.Groups.GROUP_OWNER,
    };

    private static final int COLUMN_ID = 0;
    private static final int COLUMN_PEER = 1;
    private static final int COLUMN_COUNT = 2;
    private static final int COLUMN_UNREAD = 3;
    private static final int COLUMN_MIME = 4;
    private static final int COLUMN_CONTENT = 5;
    private static final int COLUMN_TIMESTAMP = 6;
    private static final int COLUMN_STATUS = 7;
    private static final int COLUMN_ENCRYPTED = 8;
    private static final int COLUMN_DRAFT = 9;
    private static final int COLUMN_REQUEST_STATUS = 10;
    private static final int COLUMN_GROUP_ID = 11;
    private static final int COLUMN_GROUP_OWNER = 12;

    private final Context mContext;

    private long mThreadId;
    private Contact mContact;

    private String mRecipient;
    private long mDate;
    private int mMessageCount;
    private String mMime;
    private String mSubject;
    private int mUnreadCount;
    private int mStatus;
    private String mDraft;
    private String mNumberHint;
    private boolean mEncrypted;
    private int mRequestStatus;

    // from groups table
    private String mGroupId;
    private String mGroupOwner;
    private String[] mGroupPeers;

    private Conversation(Context context) {
        mContext = context;
        mThreadId = 0;
    }

    private Conversation(Context context, Cursor c) {
        mContext = context;
        synchronized (this) {
            mThreadId = c.getLong(COLUMN_ID);
            mDate = c.getLong(COLUMN_TIMESTAMP);

            mRecipient = c.getString(COLUMN_PEER);
            mMime = c.getString(COLUMN_MIME);
            mSubject = c.getString(COLUMN_CONTENT);

            mUnreadCount = c.getInt(COLUMN_UNREAD);
            mMessageCount = c.getInt(COLUMN_COUNT);
            mStatus = c.getInt(COLUMN_STATUS);
            mEncrypted = c.getInt(COLUMN_ENCRYPTED) != 0;
            mDraft = c.getString(COLUMN_DRAFT);
            mRequestStatus = c.getInt(COLUMN_REQUEST_STATUS);

            mGroupId = c.getString(COLUMN_GROUP_ID);
            mGroupOwner = c.getString(COLUMN_GROUP_OWNER);
            // group peers are loaded on demand

            loadContact();
        }
    }

    public static Conversation createNew(Context context) {
        return new Conversation(context);
    }

    public static Conversation createFromCursor(Context context, Cursor cursor) {
        return new Conversation(context, cursor);
    }

    public static Conversation loadFromUserId(Context context, String userId) {
        Conversation cv = null;
        Cursor cp = context.getContentResolver().query(Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, Threads.PEER + " = ?", new String[] { userId }, null);
        if (cp.moveToFirst())
            cv = createFromCursor(context, cp);

        cp.close();
        return cv;
    }

    public static Conversation loadFromId(Context context, long id) {
        Conversation cv = null;
        Cursor cp = context.getContentResolver().query(
                ContentUris.withAppendedId(Threads.CONTENT_URI, id),
                ALL_THREADS_PROJECTION, null, null, null);
        if (cp.moveToFirst())
            cv = createFromCursor(context, cp);

        cp.close();
        return cv;
    }

    public static void deleteFromCursor(Context context, Cursor cursor) {
        MessagesProvider.deleteThread(context, cursor.getLong(COLUMN_ID));
    }

    private void loadContact() {
        mContact = Contact.findByUserId(mContext, mRecipient, mNumberHint);
    }

    public Contact getContact() {
        return mContact;
    }

    public long getDate() {
        return mDate;
    }

    public void setDate(long date) {
        this.mDate = date;
    }

    public String getMime() {
        return mMime;
    }

    public String getSubject() {
        return mSubject;
    }

    public String getRecipient() {
        return mRecipient;
    }

    public void setRecipient(String recipient) {
        mRecipient = recipient;
        // reload contact
        loadContact();
    }

    public int getMessageCount() {
        return mMessageCount;
    }

    public int getUnreadCount() {
        return mUnreadCount;
    }

    public long getThreadId() {
        return mThreadId;
    }

    public int getStatus() {
        return mStatus;
    }

    public boolean isEncrypted() {
        return mEncrypted;
    }

    public String getDraft() {
        return mDraft;
    }

    public int getRequestStatus() {
        return mRequestStatus;
    }

    public String getNumberHint() {
        return mNumberHint;
    }

    /**
     * Sets a phone number hint that will be used if there is no match in the
     * users database.
     */
    public void setNumberHint(String numberHint) {
        mNumberHint = numberHint;
    }

    public String getGroupId() {
        return mGroupId;
    }

    public String getGroupOwner() {
        return mGroupOwner;
    }

    public String[] getGroupPeers() {
        return mGroupPeers;
    }

    public boolean isGroupChat() {
        loadGroupPeers(false);
        return mGroupId != null;
    }

    public void cancelGroupChat() {
        mGroupId = null;
        mGroupOwner = null;
        mGroupPeers = null;
    }

    private void loadGroupPeers(boolean force) {
        if (mGroupId != null && (mGroupPeers == null || force)) {
            Cursor c = mContext.getContentResolver()
                .query(Threads.Groups.MEMBERS_CONTENT_URI,
                    new String[] { Threads.Groups.PEER },
                    Threads.Groups.GROUP_ID + "=? AND " + Threads.Groups.GROUP_OWNER + "=?",
                    new String[] { mGroupId, mGroupOwner }, null);

            mGroupPeers = new String[c.getCount()];
            int i = 0;
            while (c.moveToNext()) {
                mGroupPeers[i++] = c.getString(0);
            }
            c.close();
        }
    }

    public static void startQuery(AsyncQueryHandler handler, int token) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, null, null, Threads.DEFAULT_SORT_ORDER);
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, Threads._ID + " = " + threadId, null, null);
    }

    public static Cursor startQuery(Context context) {
        return context.getContentResolver().query(Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, null, null, Threads.DEFAULT_SORT_ORDER);
    }

    public static Cursor startQuery(Context context, long threadId) {
        return context.getContentResolver().query(Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, Threads._ID + " = " + threadId, null, null);
    }

    /**
     * Turns a 1-to-1 conversation into a group chat.
     * @return the given thread ID or a newly created thread ID.
     */
    public static long initGroupChat(Context context, long threadId, String owner, String[] members) {
        String gid = StringUtils.randomString(20);

        // insert group
        ContentValues values = new ContentValues();
        values.put(Threads.Groups.GROUP_ID, gid);
        values.put(Threads.Groups.GROUP_OWNER, owner);

        if (threadId > 0) {
            // reuse existing conversation
            ContentValues threadValues = new ContentValues();
            threadValues.put(Threads.PEER, gid);
            context.getContentResolver().update(ContentUris
                .withAppendedId(Threads.CONTENT_URI, threadId), threadValues, null, null);

            values.put(Threads.Groups.THREAD_ID, threadId);
        }
        else {
            // create new conversation first
            threadId = MessagesProvider.insertEmptyThread(context, gid, "");
            values.put(Threads.Groups.THREAD_ID, threadId);
        }

        context.getContentResolver().insert(Threads.Groups.CONTENT_URI, values);

        // remove values not for members table
        values.remove(Threads.Groups.THREAD_ID);

        // insert group members
        for (String member : members) {
            // FIXME turn this into batch operations
            values.put(Threads.Groups.PEER, member);
            context.getContentResolver()
                .insert(Threads.Groups.MEMBERS_CONTENT_URI, values);
        }

        return threadId;
    }

    public static void addUser() {
        // TODO
    }

    public void markAsRead() {
        if (mThreadId > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MessagesProvider.markThreadAsRead(mContext, mThreadId);

                    MessagingNotification.updateMessagesNotification(mContext.getApplicationContext(), false);
                }
            }).start();
        }
    }

}

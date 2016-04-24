/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import org.kontalk.message.GroupCommandComponent;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.Preferences;


/**
 * A class represeting a conversation thread.
 * @author Daniele Ricci
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
        Groups.GROUP_JID,
        Groups.SUBJECT,
        Groups.GROUP_TYPE,
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
    private static final int COLUMN_GROUP_JID = 11;
    private static final int COLUMN_GROUP_SUBJECT = 12;
    private static final int COLUMN_GROUP_TYPE = 13;

    private final Context mContext;

    private long mThreadId;
    private Contact mContact;

    // for group chats it will be the group JID
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
    private String mGroupJid;
    private String[] mGroupPeers;
    private String mGroupSubject;
    private String mGroupType;

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

            mGroupJid = c.getString(COLUMN_GROUP_JID);
            mGroupSubject = c.getString(COLUMN_GROUP_SUBJECT);
            mGroupType = c.getString(COLUMN_GROUP_TYPE);
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

    public static long getMessageId(Cursor cursor) {
        return cursor.getLong(COLUMN_ID);
    }

    public static void deleteFromCursor(Context context, Cursor cursor) {
        String groupJid = cursor.getString(COLUMN_GROUP_JID);
        String[] groupPeers = null;
        if (groupJid != null)
            groupPeers = loadGroupPeersInternal(context, groupJid);
        deleteInternal(context, cursor.getLong(COLUMN_ID), groupJid, groupPeers);
    }

    private void loadContact() {
        if (isGroupChat())
            mContact = null;
        else
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

    public String getGroupJid() {
        return mGroupJid;
    }

    public String[] getGroupPeers() {
        return getGroupPeers(false);
    }

    public String[] getGroupPeers(boolean force) {
        loadGroupPeers(force);
        return mGroupPeers;
    }

    public boolean isGroupChat() {
        loadGroupPeers(false);
        return mGroupJid != null;
    }

    public String getGroupSubject() {
        return mGroupSubject;
    }

    public void cancelGroupChat() {
        mGroupJid = null;
        mGroupPeers = null;
    }

    public void delete() {
        loadGroupPeers(false);
        deleteInternal(mContext, mThreadId, mGroupJid, mGroupPeers);
    }

    private static void deleteInternal(Context context, long threadId, String groupJid, String[] groupPeers) {
        // it makes sense to leave a group if we have someone to tell about it
        boolean groupChat = groupJid != null && groupPeers.length > 1;
        MessagesProvider.deleteThread(context, threadId, groupChat);
        if (groupChat) {
            boolean encrypted = Preferences.getEncryptionEnabled(context);
            MessageCenterService.leaveGroup(context, groupJid, groupPeers, encrypted);
        }
    }

    private void loadGroupPeers(boolean force) {
        if (mGroupJid != null && (mGroupPeers == null || force)) {
            mGroupPeers = loadGroupPeersInternal(mContext, mGroupJid);
        }
    }

    private static String[] loadGroupPeersInternal(Context context, String groupJid) {
        return MessagesProvider.getGroupMembers(context, groupJid);
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
     * Creates a new group chat.
     * @return a newly created thread ID.
     */
    public static long initGroupChat(Context context, String groupJid, String subject, String[] members, String draft) {
        // insert group
        ContentValues values = new ContentValues();
        values.put(Groups.GROUP_JID, groupJid);

        // create new conversation
        long threadId = MessagesProvider.insertEmptyThread(context, groupJid, draft);

        values.put(Groups.THREAD_ID, threadId);
        values.put(Groups.SUBJECT, subject);
        values.put(Groups.GROUP_TYPE, GroupCommandComponent.GROUP_TYPE);
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

    public static void addUsers(Context context, String groupJid, String[] members) {
        ContentValues values = new ContentValues();
        values.put(Groups.GROUP_JID, groupJid);
        for (String member : members) {
            // FIXME turn this into batch operations
            values.put(Groups.PEER, member);
            context.getContentResolver()
                .insert(Groups.getMembersUri(groupJid), values);
        }
    }

    public static void setGroupSubject(Context context, String groupJid, String subject) {
        ContentValues values = new ContentValues();
        if (subject != null)
            values.put(Groups.SUBJECT, subject);
        else
            values.putNull(Groups.SUBJECT);

        context.getContentResolver().update(Groups.getUri(groupJid),
            values, null, null);
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

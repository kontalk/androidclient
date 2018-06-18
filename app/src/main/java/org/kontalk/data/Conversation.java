/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.kontalk.provider.KontalkGroupCommands;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.group.GroupControllerFactory;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MessageUtils;


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
        Threads.STICKY,
        Threads.ENCRYPTION,
        Groups.GROUP_JID,
        Groups.SUBJECT,
        Groups.GROUP_TYPE,
        Groups.MEMBERSHIP,
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
    private static final int COLUMN_STICKY = 11;
    private static final int COLUMN_ENCRYPTION = 12;
    private static final int COLUMN_GROUP_JID = 13;
    private static final int COLUMN_GROUP_SUBJECT = 14;
    private static final int COLUMN_GROUP_TYPE = 15;
    private static final int COLUMN_GROUP_MEMBERSHIP = 16;

    @SuppressWarnings("WeakerAccess")
    final Context mContext;

    @SuppressWarnings("WeakerAccess")
    long mThreadId;
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
    private boolean mSticky;
    // set encryption disabled for this chat
    private boolean mEncryption;

    // from groups table
    private String mGroupJid;
    private String[] mGroupPeers;
    private String mGroupSubject;
    private String mGroupType;
    private int mGroupMembership;

    private Conversation(Context context) {
        mContext = context;
        mThreadId = 0;
        mEncryption = true;
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
            mSticky = c.getInt(COLUMN_STICKY) != 0;
            mEncryption = c.getInt(COLUMN_ENCRYPTION) != 0;

            mGroupJid = c.getString(COLUMN_GROUP_JID);
            mGroupSubject = c.getString(COLUMN_GROUP_SUBJECT);
            mGroupType = c.getString(COLUMN_GROUP_TYPE);
            mGroupMembership = c.getInt(COLUMN_GROUP_MEMBERSHIP);
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

    public static String getPeer(Cursor cursor) {
        return cursor.getString(COLUMN_PEER);
    }

    /** Holder for thread delete information. */
    public static final class DeleteThreadHolder {
        long id;
        String groupJid;
        String groupType;
        int groupMembership;
        boolean encrypted;

        public DeleteThreadHolder(Cursor cursor) {
            id = cursor.getLong(COLUMN_ID);
            groupJid = cursor.getString(COLUMN_GROUP_JID);
            groupType = cursor.getString(COLUMN_GROUP_TYPE);
            groupMembership = cursor.getInt(COLUMN_GROUP_MEMBERSHIP);
            encrypted = cursor.getInt(COLUMN_ENCRYPTED) != 0;
        }
    }

    public static boolean isGroup(Cursor cursor, int requiredMembership) {
        return cursor.getString(COLUMN_GROUP_JID) != null && cursor.getInt(COLUMN_GROUP_MEMBERSHIP) == requiredMembership;
    }

    public static boolean isGroup(DeleteThreadHolder holder, int requiredMembership) {
        return holder.groupJid != null && holder.groupMembership == requiredMembership;
    }

    public static void archiveFromCursor(Context context, Cursor cursor) {
        long threadId = cursor.getLong(COLUMN_ID);
        MessagesProviderClient.setArchived(context, threadId, true);
    }

    public static void deleteFromCursor(Context context, DeleteThreadHolder holder, boolean leaveGroup) {
        String[] groupPeers = null;
        if (holder.groupJid != null) {
            groupPeers = loadGroupPeersInternal(context, holder.groupJid);
        }
        boolean encrypted = MessageUtils.sendEncrypted(context, holder.encrypted);
        deleteInternal(context, holder.id, holder.groupJid, groupPeers, holder.groupType, leaveGroup, encrypted);
    }

    public static void deleteFromCursor(Context context, Cursor cursor, boolean leaveGroup) {
        String groupJid = cursor.getString(COLUMN_GROUP_JID);
        String[] groupPeers = null;
        String groupType = null;
        if (groupJid != null) {
            groupType = cursor.getString(COLUMN_GROUP_TYPE);
            groupPeers = loadGroupPeersInternal(context, groupJid);
        }
        boolean encrypted = MessageUtils.sendEncrypted(context, cursor.getInt(COLUMN_ENCRYPTED) != 0);
        deleteInternal(context, cursor.getLong(COLUMN_ID), groupJid, groupPeers, groupType, leaveGroup, encrypted);
    }

    public static void deleteAll(Context context, boolean leaveGroups) {
        Cursor c = context.getContentResolver().query(Threads.CONTENT_URI,
            ALL_THREADS_PROJECTION, null, null, null);
        while (c.moveToNext()) {
            deleteFromCursor(context, c, leaveGroups);
        }
        c.close();
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

    public boolean isSticky() {
        return mSticky;
    }

    public String getNumberHint() {
        return mNumberHint;
    }

    public boolean isEncryptionEnabled() {
        return mEncryption;
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

    public int getGroupMembership() {
        return mGroupMembership;
    }

    public void leaveGroup() {
        // it makes sense to leave a group if we have someone to tell about it
        loadGroupPeers(false);
        if (mGroupJid != null) {
            boolean encrypted = MessageUtils.sendEncrypted(mContext, mEncryption);

            // send the command if there is someone to talk to
            boolean actuallySend = GroupControllerFactory.canSendCommandsWithEmptyGroup(mGroupType) ||
                getGroupPeers().length > 0;

            String msgId = MessageCenterService.messageId();
            Uri cmdMsg = KontalkGroupCommands
                .leaveGroup(mContext, mThreadId, mGroupJid, msgId, encrypted, !actuallySend);
            // TODO check for null

            // mark group as parted
            MessagesProviderClient.setGroupMembership(mContext, mGroupJid, Groups.MEMBERSHIP_PARTED);

            if (actuallySend) {
                MessageCenterService.leaveGroup(mContext, mGroupJid, mGroupPeers, encrypted,
                    ContentUris.parseId(cmdMsg), msgId);
            }
        }
    }

    public void delete(boolean leaveGroup) {
        loadGroupPeers(false);
        deleteInternal(mContext, mThreadId, mGroupJid, mGroupPeers, mGroupType, leaveGroup, mEncryption);
    }

    private static void deleteInternal(Context context, long threadId, String groupJid,
            String[] groupPeers, String groupType, boolean leaveGroup, boolean encrypted) {
        // it makes sense to leave a group if we have someone to tell about it
        boolean groupChat = groupJid != null;
        boolean actuallySend = groupChat &&
            (GroupControllerFactory.canSendCommandsWithEmptyGroup(groupType) || groupPeers.length > 0);

        boolean groupCreateSent = false;
        if (groupChat && actuallySend && leaveGroup) {
            // retrieve status of the group creation message
            // otherwise don't send the leave message at all
            groupCreateSent = KontalkGroupCommands.isGroupCreatedSent(context, threadId);
        }

        // delete messages and thread
        MessagesProviderClient.deleteThread(context, threadId, groupChat && !leaveGroup);

        // send leave message only if the group was created in the first place
        if (groupChat && leaveGroup) {
            if (groupCreateSent) {
                String msgId = MessageCenterService.messageId();
                Uri cmdMsg = KontalkGroupCommands
                    .leaveGroup(context, Messages.NO_THREAD, groupJid, msgId, encrypted, false);
                // TODO check for null

                MessageCenterService.leaveGroup(context, groupJid, groupPeers, encrypted,
                    ContentUris.parseId(cmdMsg), msgId);
            }
            else {
                // delete group immediately (members will cascade)
                context.getContentResolver()
                    .delete(Groups.getUri(groupJid), null, null);
            }
        }
    }

    public void setSticky(boolean sticky) {
        mSticky = sticky;
        if (mThreadId > 0)
            MessagesProviderClient.setThreadSticky(mContext, mThreadId, sticky);
    }

    private void loadGroupPeers(boolean force) {
        if (mGroupJid != null && (mGroupPeers == null || force)) {
            mGroupPeers = loadGroupPeersInternal(mContext, mGroupJid);
        }
    }

    private static String[] loadGroupPeersInternal(Context context, String groupJid) {
        return MessagesProviderClient.getGroupMembers(context, groupJid, 0);
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

    public static Cursor startQuery(Context context, boolean archived) {
        return context.getContentResolver().query(Threads.CONTENT_URI,
            ALL_THREADS_PROJECTION, Threads.ARCHIVED + " = " + (archived ? "1" : "0"),
            null, Threads.DEFAULT_SORT_ORDER);
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
        return MessagesProviderClient.createGroupThread(context, groupJid, subject, members, draft);
    }

    public void addUsers(String[] members) {
        if (!isGroupChat())
            throw new UnsupportedOperationException("Not a group chat conversation");

        // add members to the group
        MessagesProviderClient.addGroupMembers(mContext, mGroupJid, members, true);

        // store add group member command to outbox
        boolean encrypted = MessageUtils.sendEncrypted(mContext, mEncryption);
        String msgId = MessageCenterService.messageId();
        Uri cmdMsg = KontalkGroupCommands
            .addGroupMembers(mContext, getThreadId(), mGroupJid, members, msgId, encrypted);
        // TODO check for null

        // send add group member command now
        Set<String> allMembers = new HashSet<>();
        Collections.addAll(allMembers, getGroupPeers());
        Collections.addAll(allMembers, members);
        MessageCenterService.addGroupMembers(mContext, mGroupJid,
            mGroupSubject, allMembers.toArray(new String[allMembers.size()]),
            members, encrypted, ContentUris.parseId(cmdMsg), msgId);
    }

    public void removeUsers(String[] members) {
        if (!isGroupChat())
            throw new UnsupportedOperationException("Not a group chat conversation");

        // remove members to the group
        MessagesProviderClient.removeGroupMembers(mContext, mGroupJid, members, true);

        // store remove group member command to outbox
        boolean encrypted = MessageUtils.sendEncrypted(mContext, mEncryption);
        String msgId = MessageCenterService.messageId();
        Uri cmdMsg = KontalkGroupCommands
            .removeGroupMembers(mContext, getThreadId(), mGroupJid, members, msgId, encrypted);
        // TODO check for null

        // send add group member command now
        MessageCenterService.removeGroupMembers(mContext, mGroupJid,
            mGroupSubject, getGroupPeers(), members, encrypted,
            ContentUris.parseId(cmdMsg), msgId);
    }

    public void setGroupSubject(String subject) {
        if (!isGroupChat())
            throw new UnsupportedOperationException("Not a group chat conversation");

        // set group subject
        MessagesProviderClient.setGroupSubject(mContext, mGroupJid, subject);

        // send the command if there is someone to talk to
        boolean actuallySend = GroupControllerFactory.canSendCommandsWithEmptyGroup(mGroupType) ||
            getGroupPeers().length > 0;

        // store set group subject command to outbox
        boolean encrypted = MessageUtils.sendEncrypted(mContext, mEncryption);
        String msgId = MessageCenterService.messageId();
        Uri cmdMsg = KontalkGroupCommands
            .setGroupSubject(mContext, getThreadId(), mGroupJid, subject, msgId, encrypted, !actuallySend);
        // TODO check for null

        if (actuallySend) {
            // send set group subject command now
            String[] currentMembers = getGroupPeers();
            MessageCenterService.setGroupSubject(mContext, mGroupJid,
                subject, currentMembers, encrypted,
                ContentUris.parseId(cmdMsg), msgId);
        }
    }

    public void setEncryptionEnabled(boolean encryptionEnabled) {
        mEncryption = encryptionEnabled;
        if (mThreadId > 0)
            MessagesProviderClient.setEncryption(mContext, mThreadId, encryptionEnabled);
    }

    public void markAsRead() {
        if (mThreadId > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MessagesProviderClient.markThreadAsRead(mContext, mThreadId);
                    MessagingNotification.updateMessagesNotification(mContext.getApplicationContext(), false);
                }
            }).start();
        }
    }

}

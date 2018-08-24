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

package org.kontalk;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.WorkerThread;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.Coder;
import org.kontalk.data.Conversation;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.GroupComponent;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.InReplyToComponent;
import org.kontalk.message.LocationComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyUsers;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.DownloadService;
import org.kontalk.service.MediaService;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.group.KontalkGroupController;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Utility class to handle message transmission transactions.
 *
 * @author Daniele Ricci
 */
public class MessagesController {
    private static final String TAG = MessagesController.class.getSimpleName();

    private static final String[] RESEND_PROJECTION = new String[] {
        MyMessages.Messages._ID,
        MyMessages.Messages.THREAD_ID,
        MyMessages.Messages.MESSAGE_ID,
        MyMessages.Messages.PEER,
        MyMessages.Messages.BODY_CONTENT,
        MyMessages.Messages.BODY_MIME,
        MyMessages.Messages.SECURITY_FLAGS,
        MyMessages.Messages.ATTACHMENT_MIME,
        MyMessages.Messages.ATTACHMENT_LOCAL_URI,
        MyMessages.Messages.ATTACHMENT_FETCH_URL,
        MyMessages.Messages.ATTACHMENT_PREVIEW_PATH,
        MyMessages.Messages.ATTACHMENT_LENGTH,
        MyMessages.Messages.ATTACHMENT_COMPRESS,
        // TODO Messages.ATTACHMENT_SECURITY_FLAGS,
        MyMessages.Groups.GROUP_JID,
        MyMessages.Groups.SUBJECT,
        MyMessages.Messages.GEO_LATITUDE,
        MyMessages.Messages.GEO_LONGITUDE,
        MyMessages.Messages.GEO_TEXT,
        MyMessages.Messages.GEO_STREET,
        MyMessages.Messages.IN_REPLY_TO,
    };

    private final Context mContext;
    private final MessageQueueThread mWorker;
    private final Queue<MessageStub> mQueue;

    private boolean mUploadServiceFound;

    MessagesController(Context context) {
        mContext = context;
        new MessageCenterListener(context);

        mQueue = new LinkedBlockingQueue<>();
        mWorker = new MessageQueueThread();
        mWorker.start();
    }

    public Uri sendTextMessage(Conversation conv, String text, long inReplyTo) {
        boolean encrypted = MessageUtils.sendEncrypted(mContext, conv.isEncryptionEnabled());

        String msgId = MessageUtils.messageId();
        String userId = conv.isGroupChat() ? conv.getGroupJid() : conv.getRecipient();

        // save to local storage
        Uri newMsg = MessagesProviderClient.newOutgoingMessage(mContext,
                msgId, userId, text, encrypted, inReplyTo);
        if (newMsg != null) {
            // send message!
            if (conv.isGroupChat()) {
                MessageCenterService.sendGroupTextMessage(mContext,
                        conv.getGroupJid(), conv.getGroupPeers(),
                        text, encrypted, ContentUris.parseId(newMsg), msgId, inReplyTo);
            }
            else {
                MessageCenterService.sendTextMessage(mContext, userId, text,
                        encrypted, ContentUris.parseId(newMsg), msgId, inReplyTo);
            }

            return newMsg;
        }
        else {
            throw new SQLiteDiskIOException();
        }
    }

    public Uri sendLocationMessage(Conversation conv, String text, double lat, double lon,
        String geoText, String geoStreet) {
        boolean encrypted = MessageUtils.sendEncrypted(mContext, conv.isEncryptionEnabled());

        String msgId = MessageUtils.messageId();
        String userId = conv.isGroupChat() ? conv.getGroupJid() : conv.getRecipient();

        // save to local storage
        Uri newMsg = MessagesProviderClient.newOutgoingMessage(mContext,
                msgId, userId, text, lat, lon, geoText, geoStreet, encrypted);
        if (newMsg != null) {
            // send message!
            if (conv.isGroupChat()) {
                MessageCenterService.sendGroupLocationMessage(mContext,
                        conv.getGroupJid(), conv.getGroupPeers(),
                        text, lat, lon, geoText, geoStreet, encrypted, ContentUris.parseId(newMsg), msgId);
            }
            else {
                MessageCenterService.sendLocationMessage(mContext, userId, text, lat, lon,
                        geoText, geoStreet, encrypted, ContentUris.parseId(newMsg), msgId);
            }

            return newMsg;
        }
        else {
            throw new SQLiteDiskIOException();
        }
    }

    public Uri sendBinaryMessage(Conversation conv, Uri uri, String mime, boolean media,
                                 Class<? extends MessageComponent<?>> klass) throws SQLiteDiskIOException {
        String msgId = MessageCenterService.messageId();

        boolean encrypted = MessageUtils.sendEncrypted(mContext, conv.isEncryptionEnabled());
        int compress = 0;
        if (klass == ImageComponent.class) {
            compress = Preferences.getImageCompression(mContext);
        }

        // save to database
        String userId = conv.isGroupChat() ? conv.getGroupJid() : conv.getRecipient();
        Uri newMsg = MessagesProviderClient.newOutgoingMessage(mContext, msgId,
                userId, mime, uri, 0, compress, null, encrypted);

        if (newMsg != null) {
            // prepare message and send (thumbnail, compression -> send)
            MediaService.prepareMessage(mContext, msgId, ContentUris.parseId(newMsg), uri, mime, media, compress);
            return newMsg;
        }
        else {
            throw new SQLiteDiskIOException();
        }
    }

    /**
     * Set the trust level for the given key and, if the trust level is high
     * enough, send pending messages to the given user.
     *
     * @return true if the trust level is high enough to retry messages
     */
    public boolean setTrustLevelAndRetryMessages(Context context, String jid, String fingerprint, int trustLevel) {
        if (fingerprint == null)
            throw new NullPointerException("fingerprint");

        Keyring.setTrustLevel(context, jid, fingerprint, trustLevel);
        if (trustLevel >= MyUsers.Keys.TRUST_IGNORED) {
            MessageCenterService.retryMessagesTo(context, jid);
            return true;
        }
        return false;
    }

    private void addMembersInternal(GroupCommandComponent group, String[] members) {
        ContentValues membersValues = new ContentValues();

        for (String member : members) {
            // do not add ourselves...
            if (Authenticator.isSelfJID(mContext, member)) {
                // ...but mark our membership
                MessagesProviderClient.setGroupMembership(mContext,
                        group.getContent().getJID(), MyMessages.Groups.MEMBERSHIP_MEMBER);
                continue;
            }

            // add member to group
            membersValues.put(MyMessages.Groups.PEER, member);
            mContext.getContentResolver().insert(MyMessages.Groups
                    .getMembersUri(group.getContent().getJID()), membersValues);
        }
    }

    /**
     * Process an incoming message.
     */
    public Uri incoming(CompositeMessage msg) {
        final String sender = msg.getSender(true);

        // save to local storage
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.MESSAGE_ID, msg.getId());
        values.put(MyMessages.Messages.PEER, sender);

        MessageUtils.fillContentValues(values, msg);

        GroupCommandComponent group = msg.getComponent(GroupCommandComponent.class);
        // notify for 1-to-1 messages and group creation and part group commands
        boolean notify = (group == null || group.isCreateCommand() || group.isPartCommand());

        values.put(MyMessages.Messages.STATUS, msg.getStatus());
        // group commands don't get notifications
        values.put(MyMessages.Messages.UNREAD, notify);
        values.put(MyMessages.Messages.NEW, notify);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_IN);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());

        LocationComponent loc = msg.getComponent(LocationComponent.class);
        if (loc != null) {
            values.put(MyMessages.Messages.BODY_MIME, LocationComponent.MIME_TYPE);
            values.put(MyMessages.Messages.GEO_LATITUDE, loc.getLatitude());
            values.put(MyMessages.Messages.GEO_LONGITUDE, loc.getLongitude());
            if (!TextUtils.isEmpty(loc.getText()))
                values.put(MyMessages.Messages.GEO_TEXT, loc.getText());
            if (!TextUtils.isEmpty(loc.getStreet()))
                values.put(MyMessages.Messages.GEO_STREET, loc.getStreet());
        }

        InReplyToComponent inReplyTo = msg.getComponent(InReplyToComponent.class);
        if (inReplyTo != null) {
            values.put(MyMessages.Messages.IN_REPLY_TO, inReplyTo.getContent().getId());
        }

        GroupComponent groupInfo = msg.getComponent(GroupComponent.class);
        if (groupInfo != null) {
            values.put(MyMessages.Groups.GROUP_JID, groupInfo.getContent().getJid());
            values.put(MyMessages.Groups.GROUP_TYPE, KontalkGroupController.GROUP_TYPE);

            String groupSubject = groupInfo.getContent().getSubject();
            if (groupSubject != null)
                values.put(MyMessages.Groups.SUBJECT, groupSubject);
        }

        if (group != null) {
            // the following operations will work because we are operating with
            // groups and group_members table directly (that is, no foreign keys)

            String[] added = null, removed = null, existing = null;

            // group creation
            if (group.isCreateCommand()) {
                added = group.getCreateMembers();
            }

            // add/remove users
            else if (group.isAddOrRemoveCommand()) {
                added = group.getAddedMembers();
                removed = group.getRemovedMembers();
                existing = group.getExistingMembers();
            }

            if (added != null) {
                ContentValues membersValues = new ContentValues();
                if (added.length > 0) {
                    addMembersInternal(group, added);
                }

                if (existing != null && existing.length > 0) {
                    addMembersInternal(group, existing);
                }

                // add owner as member (since the owner is adding us)
                membersValues.put(MyMessages.Groups.PEER, group.getContent().getOwner());
                mContext.getContentResolver().insert(MyMessages.Groups
                        .getMembersUri(group.getContent().getJID()), membersValues);
            }

            if (removed != null) {
                // remove members from group
                MessagesProviderClient.removeGroupMembers(mContext, group.getContent().getJID(),
                        removed, false);
                // set our membership to parted if we were removed from the group
                for (String removedJid : removed) {
                    if (Authenticator.isSelfJID(mContext, removedJid)) {
                        MessagesProviderClient.setGroupMembership(mContext,
                                group.getContent().getJID(), MyMessages.Groups.MEMBERSHIP_KICKED);
                        break;
                    }
                }
            }

            // set subject
            if (group.isSetSubjectCommand()) {
                ContentValues groupValues = new ContentValues();
                groupValues.put(MyMessages.Groups.SUBJECT, group.getContent().getSubject());
                mContext.getContentResolver().update(MyMessages.Groups
                        .getUri(group.getContent().getJID()), groupValues, null, null);
            }

            // a user is leaving the group
            else if (group.isPartCommand()) {
                String partMember = group.getFrom();
                // remove member from group
                mContext.getContentResolver().delete(MyMessages.Groups.getMembersUri(group.getContent().getJID())
                                .buildUpon().appendEncodedPath(partMember).build(),
                        null, null);
            }
        }

        // will be null if something went wrong
        Uri msgUri = MessagesProviderClient.newIncomingMessage(mContext, values);

        if (groupInfo == null) {
            // mark sender as registered in the users database
            final Context context = mContext.getApplicationContext();
            new Thread(new Runnable() {
                public void run() {
                    try {
                        UsersProvider.markRegistered(context, sender);
                    } catch (SQLiteConstraintException e) {
                        // this might happen during an online/offline switch
                    }
                }
            }).start();
        }

        // fire notification only if message was actually inserted to database
        // and the conversation is not open already
        String paused = groupInfo != null ? groupInfo.getContent().getJid() : sender;
        if (notify && msgUri != null) {
            if (!MessagingNotification.isPaused(paused)) {
                // update notifications (delayed)
                MessagingNotification.delayedUpdateMessagesNotification(mContext.getApplicationContext(), true);
            }
            else {
                // play in-conversation sound
                MediaStorage.playNotificationSound(mContext.getApplicationContext(), R.raw.sound_incoming);
            }
        }

        // check if we need to autodownload
        @SuppressWarnings("unchecked")
        Class<AttachmentComponent>[] tryComponents = new Class[]{
                ImageComponent.class,
                VCardComponent.class,
                AudioComponent.class,
        };

        for (Class<AttachmentComponent> klass : tryComponents) {
            AttachmentComponent att = msg.getComponent(klass);
            if (att != null && att.getFetchUrl() != null &&
                    Preferences.canAutodownloadMedia(mContext, att.getLength())) {
                long databaseId = ContentUris.parseId(msgUri);
                DownloadService.start(mContext, databaseId, sender,
                        att.getMime(), msg.getTimestamp(),
                        att.getSecurityFlags() != Coder.SECURITY_CLEARTEXT,
                        att.getFetchUrl(), false);

                // only one attachment is supported
                break;
            }
        }

        return msgUri;
    }

    private final class MessageCenterListener extends BroadcastReceiver {

        MessageCenterListener(Context context) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(MessageCenterService.ACTION_CONNECTED);
            filter.addAction(MessageCenterService.ACTION_ROSTER_LOADED);
            filter.addAction(MessageCenterService.ACTION_SUBSCRIBED);
            filter.addAction(MessageCenterService.ACTION_UPLOAD_SERVICE_FOUND);
            filter.addAction(MessageCenterService.ACTION_GROUP_CREATED);
            filter.addAction(MediaService.ACTION_MEDIA_READY);
            LocalBroadcastManager.getInstance(context)
                .registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();
            switch (action != null ? action : "") {
                case MessageCenterService.ACTION_CONNECTED:
                    connected();
                    break;

                case MessageCenterService.ACTION_ROSTER_LOADED:
                    mWorker.postAction(new Runnable() {
                        @Override
                        public void run() {
                            readyForMessages();
                        }
                    });
                    break;

                case MessageCenterService.ACTION_SUBSCRIBED:
                    mWorker.postAction(new Runnable() {
                        @Override
                        public void run() {
                            subscribed(intent.getStringExtra(MessageCenterService.EXTRA_FROM));
                        }
                    });
                    break;

                case MessageCenterService.ACTION_UPLOAD_SERVICE_FOUND:
                    mWorker.postAction(new Runnable() {
                        @Override
                        public void run() {
                            uploadServiceFound();
                        }
                    });
                    break;

                case MessageCenterService.ACTION_GROUP_CREATED:
                    mWorker.postAction(new Runnable() {
                        @Override
                        public void run() {
                            groupCreated();
                        }
                    });
                    break;

                case MediaService.ACTION_MEDIA_READY:
                    mWorker.postAction(new Runnable() {
                        @Override
                        public void run() {
                            readyMedia(intent.getLongExtra("org.kontalk.message.msgId", 0));
                        }
                    });
                    break;
            }
        }
    }

    private final class MessageQueueThread extends HandlerThread implements Handler.Callback {
        private Handler mHandler;

        MessageQueueThread() {
            super(MessagesController.class.getSimpleName());
        }

        @Override
        protected void onLooperPrepared() {
            mHandler = new Handler(getLooper(), this);
        }

        public Handler getHandler() {
            return mHandler;
        }

        public void postMessage(MessageStub msg) {
            Message env = mHandler.obtainMessage();
            env.obj = msg;
            mHandler.sendMessage(env);
        }

        public void postAction(Runnable action) {
            mHandler.post(action);
        }

        @Override
        public boolean handleMessage(Message message) {
            // TODO
            Log.d(TAG, "got message: " + message);
            return false;
        }
    }

    private final class MessageStub {
        // TODO
    }

    @WorkerThread
    void connected() {
        mUploadServiceFound = false;
    }

    @WorkerThread
    void readyForMessages() {
        // send pending subscription replies
        sendPendingSubscriptionReplies();
        // resend failed and pending messages
        resendPendingMessages(false, false);
        // resend failed and pending received receipts
        resendPendingReceipts();
    }

    @WorkerThread
    void subscribed(String from) {
        resendPending(false, true, from);
    }

    @WorkerThread
    void uploadServiceFound() {
        mUploadServiceFound = true;
        resendPendingMessages(true, false);
    }

    @WorkerThread
    void groupCreated() {
        resendPending(false, false, null);
    }

    @WorkerThread
    void readyMedia(long databaseId) {
        sendReadyMedia(databaseId);
    }

    private void sendPendingSubscriptionReplies() {
        Cursor c = mContext.getContentResolver().query(MyMessages.Threads.CONTENT_URI,
            new String[] {
                MyMessages.Threads.PEER,
                MyMessages.Threads.REQUEST_STATUS,
            },
            MyMessages.Threads.REQUEST_STATUS + "=" + MyMessages.Threads.REQUEST_REPLY_PENDING_ACCEPT + " OR " +
                MyMessages.Threads.REQUEST_STATUS + "=" + MyMessages.Threads.REQUEST_REPLY_PENDING_BLOCK,
            null, MyMessages.Threads._ID);

        while (c.moveToNext()) {
            String to = c.getString(0);
            int reqStatus = c.getInt(1);

            int action;

            switch (reqStatus) {
                case MyMessages.Threads.REQUEST_REPLY_PENDING_ACCEPT:
                    action = MessageCenterService.PRIVACY_ACCEPT;
                    break;

                case MyMessages.Threads.REQUEST_REPLY_PENDING_BLOCK:
                    action = MessageCenterService.PRIVACY_BLOCK;
                    break;

                case MyMessages.Threads.REQUEST_REPLY_PENDING_UNBLOCK:
                    action = MessageCenterService.PRIVACY_UNBLOCK;
                    break;

                default:
                    // skip this one
                    continue;
            }

            MessageCenterService.replySubscription(mContext, to, action);
        }

        c.close();
    }

    private void sendReadyMedia(long databaseId) {
        Cursor c = mContext.getContentResolver().query(ContentUris
            .withAppendedId(MyMessages.Messages.CONTENT_URI, databaseId), RESEND_PROJECTION, null, null, null);

        sendMessages(c, false);

        c.close();
    }


    private void resendPending(final boolean retrying, final boolean forcePending, final String to) {
        resendPendingMessages(retrying, forcePending, to);
        resendPendingReceipts();
    }

    private void resendPendingMessages(boolean retrying, boolean forcePending) {
        resendPendingMessages(retrying, forcePending, null);
    }

    /**
     * Queries for pending messages and send them through.
     *
     * @param retrying     if true, we are retrying to send media messages after
     *                     receiving upload info (non-media messages will be filtered out)
     * @param forcePending true to include pending user review messages
     * @param to           filter by recipient (optional)
     */
    private void resendPendingMessages(boolean retrying, boolean forcePending, String to) {
        String[] filterArgs = null;

        StringBuilder filter = new StringBuilder()
            .append(MyMessages.Messages.DIRECTION)
            .append('=')
            .append(MyMessages.Messages.DIRECTION_OUT)
            .append(" AND ")
            .append(MyMessages.Messages.STATUS)
            .append("<>")
            .append(MyMessages.Messages.STATUS_SENT)
            .append(" AND ")
            .append(MyMessages.Messages.STATUS)
            .append("<>")
            .append(MyMessages.Messages.STATUS_RECEIVED)
            .append(" AND ")
            .append(MyMessages.Messages.STATUS)
            .append("<>")
            .append(MyMessages.Messages.STATUS_NOTDELIVERED)
            .append(" AND ")
            .append(MyMessages.Messages.STATUS)
            .append("<>")
            .append(MyMessages.Messages.STATUS_QUEUED);


        // filter out pending messages
        if (!forcePending) filter
            .append(" AND ")
            .append(MyMessages.Messages.STATUS)
            .append("<>")
            .append(MyMessages.Messages.STATUS_PENDING);

        // filter out non-media non-uploaded messages
        if (retrying) filter
            .append(" AND ")
            .append(MyMessages.Messages.ATTACHMENT_FETCH_URL)
            .append(" IS NULL AND ")
            .append(MyMessages.Messages.ATTACHMENT_LOCAL_URI)
            .append(" IS NOT NULL");

        if (to != null) {
            filter
                .append(" AND (")
                .append(MyMessages.Messages.PEER)
                .append("=? OR EXISTS (SELECT 1 FROM group_members WHERE ")
                .append(MyMessages.Groups.GROUP_JID)
                .append("=")
                .append(MyMessages.Messages.PEER)
                .append(" AND ")
                .append(MyMessages.Groups.PEER)
                .append("=?))");
            filterArgs = new String[]{to, to};
        }

        Cursor c = mContext.getContentResolver().query(MyMessages.Messages.CONTENT_URI,
            RESEND_PROJECTION, filter.toString(), filterArgs, MyMessages.Messages._ID);

        sendMessages(c, retrying);

        c.close();
    }

    private void sendMessages(Cursor c, boolean retrying) {
        // this set will cache thread IDs within this cursor with
        // pending group commands (i.e. just processed group commands)
        // This will be looked up when sending consecutive message in the group
        // and stop them
        Set<Long> pendingGroupCommandThreads = new HashSet<>();

        while (c.moveToNext()) {
            // TODO constants for column indexes
            long id = c.getLong(0);
            long threadId = c.getLong(1);
            String msgId = c.getString(2);
            String peer = c.getString(3);
            byte[] textContent = c.getBlob(4);
            String bodyMime = c.getString(5);
            int securityFlags = c.getInt(6);
            String attMime = c.getString(7);
            String attFileUri = c.getString(8);
            String attFetchUrl = c.getString(9);
            String attPreviewPath = c.getString(10);
            long attLength = c.getLong(11);
            int compress = c.getInt(12);
            // TODO int attSecurityFlags = c.getInt(13);

            String groupJid = c.getString(13); // 14
            String groupSubject = c.getString(14); // 15

            long inReplyToId = c.getLong(19); // 20

            if (pendingGroupCommandThreads.contains(threadId)) {
                Log.v(TAG, "group message for pending group command - delaying");
                continue;
            }

            final boolean isGroupCommand = GroupCommandComponent.supportsMimeType(bodyMime);
            if (isGroupCommand) {
                if (groupJid == null) {
                    // orphan group command waiting to be sent
                    groupJid = peer;
                }
                else {
                    // cache the thread -- it will block future messages until
                    // this command is received by the server
                    pendingGroupCommandThreads.add(threadId);
                }
            }

            String[] groupMembers = null;
            if (groupJid != null) {
                /*
                 * Huge potential issue here. Selecting all members, regardless of pending flags,
                 * might e.g. deliver messages to removed users if there is a content message right
                 * after a remove command.
                 * However, selecting members with zero flags will make a remove command to be sent
                 * only to existing members and not to the ones being removed.
                 */
                groupMembers = MessagesProviderClient.getGroupMembers(mContext, groupJid, -1);
                if (groupMembers.length == 0) {
                    // no group member left - skip message
                    // this might be a pending message that was queued before we realized there were no members left
                    // since the group might get populated again, we just skip the message but keep it
                    Log.d(TAG, "no members in group - skipping message");
                    continue;
                }
            }

            // media message encountered and no upload service available - delay message
            if (attFileUri != null && attFetchUrl == null && !mUploadServiceFound && !retrying) {
                Log.w(TAG, "no upload info received yet, delaying media message");
                continue;
            }

            Bundle b = new Bundle();
            // mark as retrying
            b.putBoolean("org.kontalk.message.retrying", true);

            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.packetId", msgId);

            if (groupJid != null) {
                b.putString("org.kontalk.message.group.jid", groupJid);
                // will be replaced by the group command (if any)
                b.putStringArray("org.kontalk.message.to", groupMembers);
            }
            else {
                b.putString("org.kontalk.message.to", peer);
            }

            // TODO shouldn't we pass security flags directly here??
            b.putBoolean("org.kontalk.message.encrypt", securityFlags != Coder.SECURITY_CLEARTEXT);

            if (isGroupCommand) {
                int cmd = 0;
                byte[] _command = c.getBlob(4);
                String command = new String(_command);

                String[] createMembers;
                String[] addMembers;
                String[] removeMembers = null;
                String subject;
                if ((createMembers = GroupCommandComponent.getCreateCommandMembers(command)) != null) {
                    cmd = MessageCenterService.GROUP_COMMAND_CREATE;
                    b.putStringArray("org.kontalk.message.to", createMembers);
                    b.putString("org.kontalk.message.group.subject", groupSubject);
                }
                else if (command.equals(GroupCommandComponent.COMMAND_PART)) {
                    cmd = MessageCenterService.GROUP_COMMAND_PART;
                }
                else if ((addMembers = GroupCommandComponent.getAddCommandMembers(command)) != null ||
                    (removeMembers = GroupCommandComponent.getRemoveCommandMembers(command)) != null) {
                    cmd = MessageCenterService.GROUP_COMMAND_MEMBERS;
                    b.putStringArray("org.kontalk.message.group.add", addMembers);
                    b.putStringArray("org.kontalk.message.group.remove", removeMembers);
                    b.putString("org.kontalk.message.group.subject", groupSubject);
                }
                else if ((subject = GroupCommandComponent.getSubjectCommand(command)) != null) {
                    cmd = MessageCenterService.GROUP_COMMAND_SUBJECT;
                    b.putString("org.kontalk.message.group.subject", subject);
                }

                b.putInt("org.kontalk.message.group.command", cmd);
            }
            else if (textContent != null) {
                b.putString("org.kontalk.message.body", MessageUtils.toString(textContent));
            }

            // message has already been uploaded - just send media
            if (attFetchUrl != null) {
                b.putString("org.kontalk.message.mime", attMime);
                b.putString("org.kontalk.message.fetch.url", attFetchUrl);
                b.putString("org.kontalk.message.preview.uri", attFileUri);
                b.putString("org.kontalk.message.preview.path", attPreviewPath);
            }
            // check if the message contains some large file to be sent
            else if (attFileUri != null) {
                b.putString("org.kontalk.message.mime", attMime);
                b.putString("org.kontalk.message.media.uri", attFileUri);
                b.putString("org.kontalk.message.preview.path", attPreviewPath);
                b.putLong("org.kontalk.message.length", attLength);
                b.putInt("org.kontalk.message.compress", compress);
            }

            if (!c.isNull(15)) {
                double lat = c.getDouble(15);
                double lon = c.getDouble(16);
                b.putDouble("org.kontalk.message.geo_lat", lat);
                b.putDouble("org.kontalk.message.geo_lon", lon);

                if (!c.isNull(17)) {
                    String geoText = c.getString(17);
                    b.putString("org.kontalk.message.geo_text", geoText);
                }

                if (!c.isNull(18)) {
                    String geoStreet = c.getString(18);
                    b.putString("org.kontalk.message.geo_street", geoStreet);
                }
            }

            if (inReplyToId > 0) {
                b.putLong("org.kontalk.message.inReplyTo", inReplyToId);
            }

            Log.v(TAG, "resending pending message " + id);

            MessageCenterService.sendMessage(mContext, b);
        }
    }

    private void resendPendingReceipts() {
        Cursor c = mContext.getContentResolver().query(MyMessages.Messages.CONTENT_URI,
            new String[]{
                MyMessages.Messages._ID,
                MyMessages.Messages.MESSAGE_ID,
                MyMessages.Messages.PEER,
            },
            MyMessages.Messages.DIRECTION + " = " + MyMessages.Messages.DIRECTION_IN + " AND " +
                MyMessages.Messages.STATUS + " = " + MyMessages.Messages.STATUS_INCOMING,
            null, MyMessages.Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String msgId = c.getString(1);
            String peer = c.getString(2);

            Bundle b = new Bundle();

            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.packetId", msgId);
            b.putString("org.kontalk.message.to", peer);
            b.putString("org.kontalk.message.ack", msgId);

            Log.v(TAG, "resending pending receipt for message " + id);
            MessageCenterService.sendMessage(mContext, b);
        }

        c.close();
    }

}

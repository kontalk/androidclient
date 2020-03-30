/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.greenrobot.eventbus.Subscribe;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;

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
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.WorkerThread;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.widget.Toast;

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
import org.kontalk.provider.KontalkGroupCommands;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyUsers;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.DownloadService;
import org.kontalk.service.MediaService;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.PrivacyCommand;
import org.kontalk.service.msgcenter.event.ConnectedEvent;
import org.kontalk.service.msgcenter.event.DisconnectedEvent;
import org.kontalk.service.msgcenter.event.GroupCreatedEvent;
import org.kontalk.service.msgcenter.event.RosterLoadedEvent;
import org.kontalk.service.msgcenter.event.SendDeliveryReceiptRequest;
import org.kontalk.service.msgcenter.event.SendMessageRequest;
import org.kontalk.service.msgcenter.event.SetUserPrivacyRequest;
import org.kontalk.service.msgcenter.event.UploadAttachmentRequest;
import org.kontalk.service.msgcenter.event.UploadServiceFoundEvent;
import org.kontalk.service.msgcenter.event.UserSubscribedEvent;
import org.kontalk.service.msgcenter.group.KontalkGroupController;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;


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
        MyMessages.Messages.PEER,
        MyMessages.Messages.BODY_MIME,
        MyMessages.Messages.ATTACHMENT_LOCAL_URI,
        MyMessages.Messages.ATTACHMENT_FETCH_URL,
        MyMessages.Groups.GROUP_JID,
    };

    private final Context mContext;
    private final MessageQueueThread mWorker;

    /** True if the message center reported being connected. Only used for a few checks */
    private boolean mConnected;

    /** True if the message center reported upload services were found. */
    private boolean mUploadServiceFound;

    MessagesController(Context context) {
        mContext = context;
        new MessageCenterListener(context);

        mWorker = new MessageQueueThread();
        mWorker.start();

        MessageCenterService.bus().register(this);
    }

    public Future<Uri> sendTextMessage(final Conversation conv, final String text, final long inReplyTo) {
        return sendTextMessage(conv, text, inReplyTo, false);
    }

    /** For use by direct reply. Wakes up the message center if necessary if you set <code>wakeService</code> to true. */
    public Future<Uri> sendTextMessage(final Conversation conv, final String text, final long inReplyTo, final boolean wakeService) {
        FutureTask<Uri> result = new FutureTask<>(new Callable<Uri>() {
            @Override
            public Uri call() throws Exception {
                boolean encrypted = MessageUtils.sendEncrypted(mContext, conv.isEncryptionEnabled());

                String msgId = MessageUtils.messageId();
                String userId = conv.isGroupChat() ? conv.getGroupJid() : conv.getRecipient();

                // save to local storage
                Uri newMsg = MessagesProviderClient.newOutgoingMessage(mContext,
                    msgId, userId, text, encrypted, inReplyTo);
                if (newMsg != null) {
                    // wake the message center if necessary
                    if (wakeService)
                        MessageCenterService.start(mContext);

                    // send message!
                    MessageCenterService.bus()
                        .post(new SendMessageRequest(ContentUris.parseId(newMsg)));

                    return newMsg;
                }
                else {
                    throw new SQLiteDiskIOException();
                }
            }
        });
        mWorker.postAction(result);
        return result;
    }

    // TODO go through the message queue and return a promise
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
            MessageCenterService.bus()
                .post(new SendMessageRequest(ContentUris.parseId(newMsg)));
            return newMsg;
        }
        else {
            throw new SQLiteDiskIOException();
        }
    }

    // TODO go through the message queue and return a promise
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

    public void retryMessage(final long id, final boolean chatEncryptionEnabled) {
        mWorker.postAction(new Runnable() {
            @Override
            public void run() {
                boolean encrypted = Preferences.getEncryptionEnabled(mContext) && chatEncryptionEnabled;
                Uri uri = ContentUris.withAppendedId(MyMessages.Messages.CONTENT_URI, id);
                MessagesProviderClient.retryMessage(mContext, uri, encrypted);
                // TODO we should retry only the requested message(s)
                resendPendingMessages(false, false);
            }
        });
    }

    public void retryMessagesTo(final String to) {
        mWorker.postAction(new Runnable() {
            @Override
            public void run() {
                MessagesProviderClient.retryMessagesTo(mContext, to);
                // TODO we should retry only message to the request user
                resendPendingMessages(false, false);
            }
        });
    }

    public void retryAllMessages() {
        mWorker.postAction(new Runnable() {
            @Override
            public void run() {
                MessagesProviderClient.retryAllMessages(mContext);
                resendPendingMessages(false, false);
            }
        });
    }

    /** Creates a group chat. */
    public Future<Uri> createGroup(final String[] users, final String groupJid, final String title) {
        FutureTask<Uri> result = new FutureTask<>(new Callable<Uri>() {
            @Override
            public Uri call() throws Exception {
                long groupThreadId = Conversation.initGroupChat(mContext,
                    groupJid, title, users, "");

                // store create group command to outbox
                // NOTE: group chats can currently only be created with chat encryption enabled
                boolean encrypted = Preferences.getEncryptionEnabled(mContext);
                String msgId = MessageCenterService.messageId();
                Uri cmdMsg = KontalkGroupCommands.createGroup(mContext,
                    groupThreadId, groupJid, users, msgId, encrypted);

                if (cmdMsg != null) {
                    // send create group command now
                    MessageCenterService.bus()
                        .post(new SendMessageRequest(ContentUris.parseId(cmdMsg)));
                    return cmdMsg;
                }
                else {
                    throw new SQLiteDiskIOException();
                }

            }
        });
        mWorker.postAction(result);
        return result;
    }

    /**
     * Set the trust level for the given key and, if the trust level is high
     * enough, send pending messages to the given user.
     *
     * @return true if the trust level is high enough to retry messages
     */
    public boolean setTrustLevelAndRetryMessages(String jid, String fingerprint, int trustLevel) {
        if (fingerprint == null)
            throw new NullPointerException("fingerprint");

        Keyring.setTrustLevel(mContext, jid, fingerprint, trustLevel);
        if (trustLevel >= MyUsers.Keys.TRUST_IGNORED) {
            retryMessagesTo(jid);
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
                        group.getContent().getJid().toString(), MyMessages.Groups.MEMBERSHIP_MEMBER);
                continue;
            }

            // add member to group
            membersValues.put(MyMessages.Groups.PEER, member);
            mContext.getContentResolver().insert(MyMessages.Groups
                    .getMembersUri(group.getContent().getJid()), membersValues);
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
            values.put(MyMessages.Groups.GROUP_JID, groupInfo.getContent().getJid().toString());
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
                membersValues.put(MyMessages.Groups.PEER, group.getContent().getOwner().toString());
                mContext.getContentResolver().insert(MyMessages.Groups
                        .getMembersUri(group.getContent().getJid()), membersValues);
            }

            if (removed != null) {
                // remove members from group
                MessagesProviderClient.removeGroupMembers(mContext, group.getContent().getJid().toString(),
                        removed, false);
                // set our membership to parted if we were removed from the group
                for (String removedJid : removed) {
                    if (Authenticator.isSelfJID(mContext, removedJid)) {
                        MessagesProviderClient.setGroupMembership(mContext,
                                group.getContent().getJid().toString(), MyMessages.Groups.MEMBERSHIP_KICKED);
                        break;
                    }
                }
            }

            // set subject
            if (group.isSetSubjectCommand()) {
                ContentValues groupValues = new ContentValues();
                groupValues.put(MyMessages.Groups.SUBJECT, group.getContent().getSubject());
                mContext.getContentResolver().update(MyMessages.Groups
                        .getUri(group.getContent().getJid()), groupValues, null, null);
            }

            // a user is leaving the group
            else if (group.isPartCommand()) {
                String partMember = group.getFrom();
                // remove member from group
                mContext.getContentResolver().delete(MyMessages.Groups.getMembersUri(group.getContent().getJid())
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
        String paused = groupInfo != null ? groupInfo.getContent().getJid().toString() : sender;
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
                String conversation = groupInfo != null ?
                    groupInfo.getContent().getJid().toString() : msg.getSender();
                DownloadService.start(mContext, databaseId, sender,
                        att.getMime(), msg.getTimestamp(),
                        att.getSecurityFlags() != Coder.SECURITY_CLEARTEXT,
                        att.getFetchUrl(), conversation, false);

                // only one attachment is supported
                break;
            }
        }

        return msgUri;
    }

    private final class MessageCenterListener extends BroadcastReceiver {

        MessageCenterListener(Context context) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(MediaService.ACTION_MEDIA_READY);
            filter.addAction(MediaService.ACTION_MEDIA_FAILED);
            LocalBroadcastManager.getInstance(context)
                .registerReceiver(this, filter);
        }

        @Override
        public void onReceive(Context context, final Intent intent) {
            String action = intent.getAction();
            switch (action != null ? action : "") {
                case MediaService.ACTION_MEDIA_READY: {
                    final long messageId = intent.getLongExtra("org.kontalk.message.msgId", 0);
                    mWorker.postAction(new Runnable() {
                        @Override
                        public void run() {
                            readyMedia(messageId);
                        }
                    });
                    break;
                }
                case MediaService.ACTION_MEDIA_FAILED: {
                    Toast.makeText(context,
                        R.string.err_store_message_failed,
                        Toast.LENGTH_LONG).show();
                    break;
                }
            }
        }
    }

    private static final class MessageQueueThread extends HandlerThread {
        private Handler mHandler;

        MessageQueueThread() {
            super(MessagesController.class.getSimpleName());
        }

        public void postAction(Runnable action) {
            mHandler.post(action);
        }

        @Override
        public synchronized void start() {
            super.start();
            // getLooper will block until ready
            mHandler = new Handler(getLooper());
        }
    }

    @Subscribe
    public void onConnected(ConnectedEvent event) {
        if (!mConnected) {
            mConnected = true;
            mUploadServiceFound = false;
        }
    }

    @Subscribe
    public void onDisconnected(DisconnectedEvent event) {
        mConnected = false;
        mUploadServiceFound = false;
    }

    @Subscribe
    public void onRosterLoaded(RosterLoadedEvent event) {
        mWorker.postAction(new Runnable() {
            @Override
            public void run() {
                readyForMessages();
            }
        });
    }

    @Subscribe
    public void onGroupCreated(GroupCreatedEvent event) {
        mWorker.postAction(new Runnable() {
            @Override
            public void run() {
                groupCreated();
            }
        });
    }

    @Subscribe
    public void onUserSubscribed(UserSubscribedEvent event) {
        final String jid = event.jid.asBareJid().toString();
        mWorker.postAction(new Runnable() {
            @Override
            public void run() {
                subscribed(jid);
            }
        });
    }

    @Subscribe
    public void onUploadServiceFound(UploadServiceFoundEvent event) {
        mWorker.postAction(new Runnable() {
            @Override
            public void run() {
                uploadServiceFound();
            }
        });
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

            PrivacyCommand action;

            switch (reqStatus) {
                case MyMessages.Threads.REQUEST_REPLY_PENDING_ACCEPT:
                    action = PrivacyCommand.ACCEPT;
                    break;

                case MyMessages.Threads.REQUEST_REPLY_PENDING_BLOCK:
                    action = PrivacyCommand.BLOCK;
                    break;

                case MyMessages.Threads.REQUEST_REPLY_PENDING_UNBLOCK:
                    action = PrivacyCommand.UNBLOCK;
                    break;

                default:
                    // skip this one
                    continue;
            }

            BareJid jid = JidCreate.bareFromOrThrowUnchecked(to);
            MessageCenterService.bus()
                .post(new SetUserPrivacyRequest(jid, action));
        }

        c.close();
    }

    private void sendReadyMedia(long databaseId) {
        Cursor c = mContext.getContentResolver().query(ContentUris
            .withAppendedId(MyMessages.Messages.CONTENT_URI, databaseId), RESEND_PROJECTION, null, null, null);

        sendMessages(c);

        c.close();
    }


    private void resendPending(final boolean retryingMedia, final boolean forcePending, final String to) {
        resendPendingMessages(retryingMedia, forcePending, to);
        resendPendingReceipts();
    }

    private void resendPendingMessages(boolean retryingMedia, boolean forcePending) {
        resendPendingMessages(retryingMedia, forcePending, null);
    }

    /**
     * Queries for pending messages and send them through.
     *
     * @param retryingMedia if true, we are retrying to send media messages after
     *                      receiving upload info (non-media messages will be filtered out)
     * @param forcePending  true to include pending user review messages
     * @param to            filter by recipient (optional)
     */
    private void resendPendingMessages(boolean retryingMedia, boolean forcePending, String to) {
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

        // include only messages that need to be uploaded
        if (retryingMedia) {
            filter.append(" AND ")
                // null fetch URL: message hasn't been uploaded yet
                .append(MyMessages.Messages.ATTACHMENT_FETCH_URL)
                .append(" IS NULL AND ")
                // non-null local URI: media message
                .append(MyMessages.Messages.ATTACHMENT_LOCAL_URI)
                .append(" IS NOT NULL");
        }
        // include only messages that don't require upload
        else {
            filter.append(" AND (")
                // non-media messages or...
                .append(MyMessages.Messages.ATTACHMENT_LOCAL_URI)
                .append(" IS NULL OR ")
                // ...already uploaded messages
                .append(MyMessages.Messages.ATTACHMENT_FETCH_URL)
                .append(" IS NOT NULL)");
        }

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

        sendMessages(c);

        c.close();
    }

    /** A somewhat smart send message procedure. Consumes all rows in cursor. */
    private void sendMessages(Cursor c) {
        // this set will cache thread IDs within this cursor with
        // pending group commands (i.e. just processed group commands)
        // This will be looked up when sending consecutive messages in the group
        // and stop them
        Set<Long> pendingGroupCommandThreads = new HashSet<>();

        // a list of messages to be deleted
        List<Long> messagesToDelete = new LinkedList<>();

        while (c.moveToNext()) {
            // TODO constants for column indexes
            long id = c.getLong(0);
            long threadId = c.getLong(1);
            String peer = c.getString(2);
            String bodyMime = c.getString(3);
            String attFileUri = c.getString(4);
            String attFetchUrl = c.getString(5);
            String groupJid = c.getString(6);

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

            if (groupJid != null) {
                /*
                 * Huge potential issue here. Selecting all members, regardless of pending flags,
                 * might e.g. deliver messages to removed users if there is a content message right
                 * after a remove command.
                 * However, selecting members with zero flags will make a remove command to be sent
                 * only to existing members and not to the ones being removed.
                 */
                String[] groupMembers = MessagesProviderClient.getGroupMembers(mContext, groupJid, -1);
                if (groupMembers.length == 0) {
                    if (threadId == MyMessages.Messages.NO_THREAD) {
                        // no group member left and ghost thread
                        // Finally delete the message
                        Log.d(TAG, "message " + id + " for ghost group with no members - marking for deletion");
                        messagesToDelete.add(id);
                    }
                    else {
                        // no group member left - skip message
                        // This might be a pending message that was queued before we realized there were no members left
                        // since the group might get populated again, we just skip the message but keep it
                        Log.d(TAG, "no members in group - skipping message " + id);
                    }
                    continue;
                }
            }

            // media message encountered and no upload service available - delay message
            if (attFileUri != null && attFetchUrl == null && !mUploadServiceFound) {
                Log.w(TAG, "no upload info received yet, delaying media message");
                continue;
            }

            Log.v(TAG, "resending pending message " + id);

            Object event;
            // non-uploaded media messages must follow another path
            if (attFileUri != null && attFetchUrl == null) {
                event = new UploadAttachmentRequest(id);
            }
            else {
                event = new SendMessageRequest(id);
            }

            MessageCenterService.bus().post(event);
        }

        // very inefficient, but it rarely happens
        for (long id : messagesToDelete) {
            MessagesProviderClient.deleteMessage(mContext, id);
        }
    }

    private void resendPendingReceipts() {
        Cursor c = mContext.getContentResolver().query(MyMessages.Messages.CONTENT_URI,
            new String[]{ MyMessages.Messages._ID },
            MyMessages.Messages.DIRECTION + " = " + MyMessages.Messages.DIRECTION_IN + " AND " +
                MyMessages.Messages.STATUS + " = " + MyMessages.Messages.STATUS_INCOMING,
            null, MyMessages.Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);

            Log.v(TAG, "resending pending receipt for message " + id);
            MessageCenterService.bus()
                .post(new SendDeliveryReceiptRequest(id));
        }

        c.close();
    }

}

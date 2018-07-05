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

package org.kontalk;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
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


/**
 * Utility class to handle message transmission transactions.
 *
 * @author Daniele Ricci
 */
public class MessagesController {

    private final Context mContext;

    MessagesController(Context context) {
        mContext = context;
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
            } else {
                MessageCenterService.sendTextMessage(mContext, userId, text,
                        encrypted, ContentUris.parseId(newMsg), msgId, inReplyTo);
            }

            return newMsg;
        } else {
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
            } else {
                MessageCenterService.sendLocationMessage(mContext, userId, text, lat, lon,
                        geoText, geoStreet, encrypted, ContentUris.parseId(newMsg), msgId);
            }

            return newMsg;
        } else {
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

}

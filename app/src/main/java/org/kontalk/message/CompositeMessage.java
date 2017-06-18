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

package org.kontalk.message;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jxmpp.util.XmppStringUtils;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.GroupExtension;
import org.kontalk.data.GroupInfo;
import org.kontalk.provider.MessagesProviderUtils;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads.Conversations;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;


/**
 * A composite message, made up of one or more {@link MessageComponent}.
 * TODO make it a {@link Parcelable}
 * @author Daniele Ricci
 * @version 1.0
 */
public class CompositeMessage {

    public static final int USERID_LENGTH = 40;
    public static final int USERID_LENGTH_RESOURCE = 48;

    @SuppressWarnings("unchecked")
    private static final Class<AttachmentComponent>[] TRY_COMPONENTS = new Class[] {
        ImageComponent.class,
        AudioComponent.class,
        VCardComponent.class,
    };

    private static final String[] MESSAGE_LIST_PROJECTION = {
        Messages._ID,
        Messages.MESSAGE_ID,
        Messages.PEER,
        Messages.DIRECTION,
        Messages.TIMESTAMP,
        Messages.SERVER_TIMESTAMP,
        Messages.STATUS_CHANGED,
        Messages.STATUS,
        Messages.ENCRYPTED,
        Messages.SECURITY_FLAGS,
        Messages.BODY_MIME,
        Messages.BODY_CONTENT,
        Messages.BODY_LENGTH,
        Messages.ATTACHMENT_MIME,
        Messages.ATTACHMENT_PREVIEW_PATH,
        Messages.ATTACHMENT_LOCAL_URI,
        Messages.ATTACHMENT_FETCH_URL,
        Messages.ATTACHMENT_LENGTH,
        Messages.ATTACHMENT_ENCRYPTED,
        Messages.ATTACHMENT_SECURITY_FLAGS,
        Messages.GEO_LATITUDE,
        Messages.GEO_LONGITUDE,
        Messages.GEO_TEXT,
        Messages.GEO_STREET,
        Groups.GROUP_JID,
        Groups.SUBJECT,
        Groups.GROUP_TYPE,
        Groups.MEMBERSHIP,
    };

    // these indexes matches MESSAGE_LIST_PROJECTION
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_MESSAGE_ID = 1;
    public static final int COLUMN_PEER = 2;
    public static final int COLUMN_DIRECTION = 3;
    public static final int COLUMN_TIMESTAMP = 4;
    public static final int COLUMN_SERVER_TIMESTAMP = 5;
    public static final int COLUMN_STATUS_CHANGED = 6;
    public static final int COLUMN_STATUS = 7;
    public static final int COLUMN_ENCRYPTED = 8;
    public static final int COLUMN_SECURITY = 9;
    public static final int COLUMN_BODY_MIME = 10;
    public static final int COLUMN_BODY_CONTENT = 11;
    public static final int COLUMN_BODY_LENGTH = 12;
    public static final int COLUMN_ATTACHMENT_MIME = 13;
    public static final int COLUMN_ATTACHMENT_PREVIEW_PATH = 14;
    public static final int COLUMN_ATTACHMENT_LOCAL_URI = 15;
    public static final int COLUMN_ATTACHMENT_FETCH_URL = 16;
    public static final int COLUMN_ATTACHMENT_LENGTH = 17;
    public static final int COLUMN_ATTACHMENT_ENCRYPTED = 18;
    public static final int COLUMN_ATTACHMENT_SECURITY_FLAGS = 19;
    public static final int COLUMN_GEO_LATITUDE = 20;
    public static final int COLUMN_GEO_LONGITUDE = 21;
    public static final int COLUMN_GEO_TEXT = 22;
    public static final int COLUMN_GEO_STREET = 23;
    public static final int COLUMN_GROUP_JID = 24;
    public static final int COLUMN_GROUP_SUBJECT = 25;
    public static final int COLUMN_GROUP_TYPE = 26;
    public static final int COLUMN_GROUP_MEMBERSHIP = 27;


    public static final String MSG_ID = "org.kontalk.message.id";
    public static final String MSG_SERVER_ID = "org.kontalk.message.serverId";
    public static final String MSG_SENDER = "org.kontalk.message.sender";
    public static final String MSG_MIME = "org.kontalk.message.mime";
    public static final String MSG_CONTENT = "org.kontalk.message.content";
    public static final String MSG_RECIPIENTS = "org.kontalk.message.recipients";
    public static final String MSG_GROUP = "org.kontalk.message.group";
    public static final String MSG_TIMESTAMP = "org.kontalk.message.timestamp";
    public static final String MSG_ENCRYPTED = "org.kontalk.message.encrypted";
    public static final String MSG_COMPRESS = "org.kontalk.message.compress";

    private static final int SUFFIX_LENGTH = "Component".length();

    protected Context mContext;
    protected long mDatabaseId;
    protected String mId;
    protected String mSender;
    protected long mTimestamp;
    protected long mServerTimestamp;
    protected long mStatusChanged;
    protected int mStatus;
    protected boolean mEncrypted;
    protected int mSecurityFlags;

    /**
     * Recipients (outgoing) - will contain one element for incoming
     */
    protected List<String> mRecipients;

    /** Message components. */
    protected List<MessageComponent<?>> mComponents;

    /** Creates a new composite message. */
    public CompositeMessage(Context context, String id, long timestamp, String sender, boolean encrypted, int securityFlags) {
        this(context);

        mId = id;
        mSender = sender;
        mRecipients = new ArrayList<String>();
        // will be updated if necessary
        mTimestamp = System.currentTimeMillis();
        mServerTimestamp = timestamp;

        mEncrypted = encrypted;
        mSecurityFlags = securityFlags;
    }

    /** Empty constructor for local use. */
    private CompositeMessage(Context context) {
        mContext = context;
        mComponents = new ArrayList<>();
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public String getSender(boolean generic) {
        return generic && XmppStringUtils.isFullJID(mSender) ?
            XmppStringUtils.parseBareJid(mSender) : mSender;
    }

    public String getSender() {
        return getSender(false);
    }

    public List<String> getRecipients() {
        return mRecipients;
    }

    public void addRecipient(String userId) {
        mRecipients.add(userId);
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }

    public long getStatusChanged() {
        return mStatusChanged;
    }

    public void setStatusChanged(long statusChanged) {
        mStatusChanged = statusChanged;
    }

    public long getServerTimestamp() {
        return mServerTimestamp;
    }

    // for internal use only.
    public void setStatus(int status) {
        mStatus = status;
    }

    public int getStatus() {
        return mStatus;
    }

    @Override
    public String toString() {
        // FIXME include components
        return getClass().getSimpleName() + ": id=" + mId;
    }

    public int getDirection() {
        return (mSender != null) ?
                Messages.DIRECTION_IN : Messages.DIRECTION_OUT;
    }

    public void setDatabaseId(long databaseId) {
        mDatabaseId = databaseId;
    }

    public long getDatabaseId() {
        return mDatabaseId;
    }

    public boolean isEncrypted() {
        return mEncrypted;
    }

    public void setEncrypted(boolean encrypted) {
        mEncrypted = encrypted;
    }

    public int getSecurityFlags() {
        return mSecurityFlags;
    }

    public void setSecurityFlags(int flags) {
        mSecurityFlags = flags;
    }

    public void addComponent(MessageComponent<?> c) {
        mComponents.add(c);
    }

    public void clearComponents() {
        mComponents.clear();
    }

    public <T extends MessageComponent<?>> boolean hasComponent(Class<T> type) {
        return getComponent(type) != null;
    }

    /** Returns the first component of the given type. */
    public <T extends MessageComponent<?>> T getComponent(Class<T> type) {
        for (MessageComponent<?> cmp : mComponents) {
            if (type.isInstance(cmp))
                return (T) cmp;
        }

        return null;
    }

    public List<MessageComponent<?>> getComponents() {
        return mComponents;
    }

    private void populateFromCursor(Cursor c) {
        // be sure to stick to our projection array
        mDatabaseId = c.getLong(COLUMN_ID);
        mId = c.getString(COLUMN_MESSAGE_ID);
        mTimestamp = c.getLong(COLUMN_TIMESTAMP);
        mStatusChanged = c.getLong(COLUMN_STATUS_CHANGED);
        mStatus = c.getInt(COLUMN_STATUS);
        mRecipients = new ArrayList<>();
        mEncrypted = (c.getShort(COLUMN_ENCRYPTED) > 0);
        mSecurityFlags = c.getInt(COLUMN_SECURITY);
        mServerTimestamp = c.getLong(COLUMN_SERVER_TIMESTAMP);

        String peer = c.getString(COLUMN_PEER);
        int direction = c.getInt(COLUMN_DIRECTION);
        if (direction == Messages.DIRECTION_OUT) {
            // we are the origin
            mSender = null;
            mRecipients.add(peer);
        }
        else {
            mSender = peer;
            // we are the origin - no recipient
        }

        byte[] body = c.getBlob(COLUMN_BODY_CONTENT);

        // encrypted message - single raw encrypted component
        if (mEncrypted) {
            RawComponent raw = new RawComponent(body, true, mSecurityFlags);
            addComponent(raw);
        }

        else {

            if (!c.isNull(COLUMN_GEO_LATITUDE)) {
                double lat = c.getDouble(COLUMN_GEO_LATITUDE);
                double lon = c.getDouble(COLUMN_GEO_LONGITUDE);
                String text = c.getString(COLUMN_GEO_TEXT);
                String street = c.getString(COLUMN_GEO_STREET);

                LocationComponent location = new LocationComponent(lat, lon, text, street);
                addComponent(location);
            }


            String mime = c.getString(COLUMN_BODY_MIME);
            String groupJid = c.getString(COLUMN_GROUP_JID);
            String groupSubject = c.getString(COLUMN_GROUP_SUBJECT);
            String groupType = c.getString(COLUMN_GROUP_TYPE);
            int groupMembership = c.getInt(COLUMN_GROUP_MEMBERSHIP);

            if (body != null) {
                // remove trailing zero
                String bodyText = MessageUtils.toString(body);

                // text data
                if (TextComponent.supportsMimeType(mime)) {
                    if (!hasComponent(LocationComponent.class)) {
                        TextComponent txt = new TextComponent(bodyText);
                        addComponent(txt);
                    }
                }

                // group command
                else if (GroupCommandComponent.supportsMimeType(mime)) {
                    String groupId = XmppStringUtils.parseLocalpart(groupJid);
                    String groupOwner = XmppStringUtils.parseDomain(groupJid);
                    GroupExtension ext = null;

                    String subject;
                    String[] createMembers;
                    if ((createMembers = GroupCommandComponent.getCreateCommandMembers(bodyText)) != null) {
                        ext = new GroupExtension(groupId, groupOwner, GroupExtension.Type.CREATE,
                            groupSubject, GroupCommandComponent.membersFromJIDs(createMembers));
                    }
                    else if (GroupCommandComponent.COMMAND_PART.equals(bodyText)) {
                        ext = new GroupExtension(groupId, groupOwner, GroupExtension.Type.PART);
                    }
                    else if ((subject = GroupCommandComponent.getSubjectCommand(bodyText)) != null) {
                        ext = new GroupExtension(groupId, groupOwner, GroupExtension.Type.SET, subject);
                    }
                    else {
                        String[] addMembers = GroupCommandComponent.getAddCommandMembers(bodyText);
                        String[] removeMembers = GroupCommandComponent.getRemoveCommandMembers(bodyText);
                        if (addMembers != null || removeMembers != null) {
                            ext = new GroupExtension(groupId, groupOwner, GroupExtension.Type.SET,
                                groupSubject, GroupCommandComponent
                                    // TODO what about existing members here?
                                    .membersFromJIDs(null, addMembers, removeMembers));
                        }
                    }

                    if (ext != null)
                        addComponent(new GroupCommandComponent(ext, peer,
                            Authenticator.getSelfJID(mContext)));
                }

                // unknown data
                else {
                    RawComponent raw = new RawComponent(body, false, mSecurityFlags);
                    addComponent(raw);
                }
            }

            // attachment
            String attMime = c.getString(COLUMN_ATTACHMENT_MIME);
            if (attMime != null) {

                String attPreview = c.getString(COLUMN_ATTACHMENT_PREVIEW_PATH);
                String attLocal = c.getString(COLUMN_ATTACHMENT_LOCAL_URI);
                String attFetch = c.getString(COLUMN_ATTACHMENT_FETCH_URL);
                long attLength = c.getLong(COLUMN_ATTACHMENT_LENGTH);
                boolean attEncrypted = c.getInt(COLUMN_ATTACHMENT_ENCRYPTED) > 0;
                int attSecurityFlags = c.getInt(COLUMN_ATTACHMENT_SECURITY_FLAGS);

                AttachmentComponent att = null;
                File previewFile = (attPreview != null) ? new File(attPreview) : null;
                Uri localUri = (attLocal != null) ? Uri.parse(attLocal) : null;

                if (ImageComponent.supportsMimeType(attMime)) {
                    att = new ImageComponent(attMime, previewFile,
                            localUri, attFetch, attLength,
                            attEncrypted, attSecurityFlags);
                }

                else if (VCardComponent.supportsMimeType(attMime)) {
                    att = new VCardComponent(previewFile,
                            localUri, attFetch, attLength,
                            attEncrypted, attSecurityFlags);
                }

                else if (AudioComponent.supportsMimeType(attMime)) {
                    att = new AudioComponent(attMime,
                            localUri, attFetch,
                            attLength, attEncrypted, attSecurityFlags);
                }

                else {
                    att = new DefaultAttachmentComponent(attMime,
                            localUri, attFetch,
                            attLength, attEncrypted, attSecurityFlags);
                }

                // TODO other type of attachments

                if (att != null) {
                    att.populateFromCursor(mContext, c);
                    addComponent(att);
                }

            }

            // group information
            if (groupJid != null) {
                GroupInfo groupInfo = new GroupInfo(groupJid, groupSubject, groupType, groupMembership);
                addComponent(new GroupComponent(groupInfo));
            }

        }
    }

    /** Clears all local fields for recycle. */
    protected void clear() {
        // clear all fields
        mContext = null;
        mDatabaseId = 0;
        mId = null;
        mSender = null;
        mTimestamp = 0;
        mServerTimestamp = 0;
        mStatusChanged = 0;
        mStatus = 0;
        mEncrypted = false;
        mSecurityFlags = 0;
    }

    /** Builds an instance from a {@link Cursor} row. */
    public static CompositeMessage fromCursor(Context context, Cursor cursor) {
        CompositeMessage msg = new CompositeMessage(context);
        msg.populateFromCursor(cursor);
        // TODO
        return msg;
    }

    public static void deleteFromCursor(Context context, Cursor cursor) {
        MessagesProviderUtils.deleteMessage(context, cursor.getLong(COLUMN_ID));
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId, long count, long lastId) {
        Uri.Builder builder = ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId)
            .buildUpon()
            .appendQueryParameter("count", String.valueOf(count));
        if (lastId > 0) {
            builder.appendQueryParameter("last", String.valueOf(lastId));
        }

        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, lastId > 0 ? "append" : null, builder.build(),
                MESSAGE_LIST_PROJECTION, null, null, Messages.DEFAULT_SORT_ORDER);
    }

    /** A sample text content from class name and mime type. */
    public static String getSampleTextContent(String mime) {
        Class<AttachmentComponent> klass = getSupportingComponent(mime);
        if (klass != null) {
            String cname = klass.getSimpleName();
            return cname.substring(0, cname.length() - SUFFIX_LENGTH) +
                ": " + mime;
        }

        // no supporting component - return mime
        // TODO i18n
        return "Unknown: " + mime;
    }

    private static Class<AttachmentComponent> getSupportingComponent(String mime) {
        // FIXME using reflection BAD BAD BAD !!!
        for (Class<AttachmentComponent> klass : TRY_COMPONENTS) {
            Boolean supported = null;
            try {
                Method m = klass.getMethod("supportsMimeType", String.class);
                supported = (Boolean) m.invoke(klass, mime);
            }
            catch (Exception e) {
                // ignored
            }

            if (supported != null && supported) {
                return klass;
            }
        }

        return null;
    }

    /**
     * Returns a correct file object for an incoming message.
     * @param mime MIME type of the incoming attachment
     * @param timestamp timestamp of the message
     */
    public static File getIncomingFile(String mime, @NonNull Date timestamp) {
        if (mime != null) {
            if (ImageComponent.supportsMimeType(mime)) {
                String ext = ImageComponent.getFileExtension(mime);
                return MediaStorage.getIncomingImageFile(timestamp, ext);
            }
            else if (AudioComponent.supportsMimeType(mime)) {
                String ext = AudioComponent.getFileExtension(mime);
                return MediaStorage.getIncomingAudioFile(timestamp, ext);
            }
            // TODO maybe other file types?
        }
        return null;
    }

    /**
     * Returns a correct file name for the given MIME.
     * @param mime MIME type of the incoming attachment
     * @param timestamp timestamp of the message
     */
    public static String getFilename(String mime, @NonNull Date timestamp) {
        if (ImageComponent.supportsMimeType(mime)) {
            String ext = ImageComponent.getFileExtension(mime);
            return MediaStorage.getOutgoingPictureFilename(timestamp, ext);
        }
        else if (AudioComponent.supportsMimeType(mime)) {
            String ext = AudioComponent.getFileExtension(mime);
            return MediaStorage.getOutgoingAudioFilename(timestamp, ext);
        }

        return null;
    }

    /** Still unused.
    public static void startQuery(AsyncQueryHandler handler, int token, String peer) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Messages.CONTENT_URI,
                MESSAGE_LIST_PROJECTION, "peer = ?", new String[] { peer },
                    Messages.DEFAULT_SORT_ORDER);
    }
    */

}

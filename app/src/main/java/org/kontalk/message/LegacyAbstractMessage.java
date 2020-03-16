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

package org.kontalk.message;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.kontalk.crypto.Coder;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads.Conversations;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;


/**
 * An abstract message.
 * FIXME it should be a {@link Parcelable}
 * @author Daniele Ricci
 * @version 2.0
 */
@Deprecated
public abstract class LegacyAbstractMessage<T> {

    public static final int USERID_LENGTH = 40;
    public static final int USERID_LENGTH_RESOURCE = 48;

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
    };

    // these indexes matches MESSAGE_LIST_PROJECTION
    public static final int COLUMN_ID = 0;
    public static final int COLUMN_MESSAGE_ID = 1;
    public static final int COLUMN_REAL_ID = 2;
    public static final int COLUMN_PEER = 3;
    public static final int COLUMN_DIRECTION = 4;
    public static final int COLUMN_TIMESTAMP = 5;
    public static final int COLUMN_SERVER_TIMESTAMP = 6;
    public static final int COLUMN_STATUS_CHANGED = 7;
    public static final int COLUMN_STATUS = 8;
    public static final int COLUMN_ENCRYPTED = 9;
    public static final int COLUMN_SECURITY = 10;
    public static final int COLUMN_BODY_MIME = 11;
    public static final int COLUMN_BODY_CONTENT = 12;
    public static final int COLUMN_BODY_LENGTH = 13;

    public static final String MSG_ID = "org.kontalk.message.id";
    public static final String MSG_SENDER = "org.kontalk.message.sender";
    public static final String MSG_MIME = "org.kontalk.message.mime";
    public static final String MSG_CONTENT = "org.kontalk.message.content";
    public static final String MSG_RECIPIENTS = "org.kontalk.message.recipients";
    public static final String MSG_GROUP = "org.kontalk.message.group";
    public static final String MSG_TIMESTAMP = "org.kontalk.message.timestamp";

    protected Context mContext;
    protected long databaseId;
    protected String id;
    protected String sender;
    protected long timestamp;
    protected long serverTimestamp;
    protected long statusChanged;
    protected int status;
    protected boolean encrypted;
    protected int security;

    /**
     * Recipients (outgoing) - will contain one element for incoming
     */
    protected List<String> recipients;

    /**
     * Recipients (incoming) - will be null for outgoing
     */
    protected List<String> group;

    /** Remote fetch URL (if any). */
    protected String fetchUrl;

    /** Local file {@link Uri}. */
    protected Uri localUri;

    /** Preview file path. */
    protected File previewFile;

    /** Message length (original file length for media messages). */
    protected long length;

    /** Message components. */
    protected List<MessageComponent<?>> mComponents;

    public LegacyAbstractMessage(Context context, String id, long timestamp, String sender, boolean encrypted, List<String> group) {
        this(context, id, timestamp, sender, encrypted);
        this.group = group;
    }

    public LegacyAbstractMessage(Context context, String id, long timestamp, String sender, boolean encrypted) {
        this.mContext = context;

        this.id = id;
        this.sender = sender;
        this.recipients = new ArrayList<String>();
        // will be updated if necessary
        this.timestamp = System.currentTimeMillis();
        this.serverTimestamp = timestamp;

        if (encrypted) {
            this.encrypted = encrypted;
            // only basic encryption for now
            this.security = Coder.SECURITY_BASIC;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender(boolean generic) {
        return (generic && sender.length() > USERID_LENGTH) ?
                sender.substring(0, USERID_LENGTH) : sender;
    }

    public String getSender() {
        return getSender(false);
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void addRecipient(String userId) {
        recipients.add(userId);
    }

    public List<String> getGroup() {
        return group;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getStatusChanged() {
        return statusChanged;
    }

    public void setStatusChanged(long statusChanged) {
        this.statusChanged = statusChanged;
    }

    public long getServerTimestamp() {
        return serverTimestamp;
    }

    public int getStatus() {
        return status;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": id=" + id;
    }

    public int getDirection() {
        return (sender != null) ?
                Messages.DIRECTION_IN : Messages.DIRECTION_OUT;
    }

    public void setDatabaseId(long databaseId) {
        this.databaseId = databaseId;
    }

    public long getDatabaseId() {
        return databaseId;
    }

    /**
     * Returns a rapid text representation of the message.
     * The returned value is useful for notification tickers.
     * @return text representation of this message
     * @throws UnsupportedEncodingException
     */
    public abstract String getTextContent() throws UnsupportedEncodingException;

    /**
     * Binary contents of this message.
     * Used for storing data representation into the database.
     * @return the byte contents of this message
     */
    public abstract byte[] getBinaryContent();

    /** A sample text content from class name and mime type. */
    public static String getSampleTextContent(Class<? extends LegacyAbstractMessage> klass,
            String mime) {
        String cname = klass.getSimpleName();
        return cname.substring(0, cname.length() - "Message".length()) +
            ": " + mime;
    }

    /** Sets a URL for fetching big contents. */
    public void setFetchUrl(String url) {
        fetchUrl = url;
    }

    public String getFetchUrl() {
        return fetchUrl;
    }

    /*
    public void setFetched(boolean fetched) {
        this.fetched = fetched;
    }

    public boolean isFetched() {
        return this.fetched;
    }
    */

    /** Sets a pointer to the local resource. */
    public void setLocalUri(Uri uri) {
        localUri = uri;
    }

    public Uri getLocalUri() {
        return localUri;
    }

    /** Sets a pointer to the preview resource. */
    public void setPreviewFile(File file) {
        previewFile = file;
    }

    public File getPreviewFile() {
        return previewFile;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted() {
        encrypted = true;
    }

    public int getSecurityFlags() {
        return security;
    }

    public void setSecurityFlags(int flags) {
        this.security = flags;
    }

    /** Decrypts the message. */
    public abstract void decrypt(Coder coder) throws GeneralSecurityException;

    protected void populateFromCursor(Cursor c) {
        // be sure to stick to our projection array
        databaseId = c.getLong(COLUMN_ID);
        id = c.getString(COLUMN_MESSAGE_ID);
        //realId = c.getString(COLUMN_REAL_ID);
        timestamp = c.getLong(COLUMN_TIMESTAMP);
        statusChanged = c.getLong(COLUMN_STATUS_CHANGED);
        status = c.getInt(COLUMN_STATUS);
        recipients = new ArrayList<String>();
        encrypted = (c.getShort(COLUMN_ENCRYPTED) > 0);
        security = c.getInt(COLUMN_SECURITY);
        serverTimestamp = c.getLong(COLUMN_SERVER_TIMESTAMP);
        /*
        fetchUrl = c.getString(COLUMN_FETCH_URL);
        length = c.getLong(COLUMN_LENGTH);
        String _localUri = c.getString(COLUMN_LOCAL_URI);
        // load local uri
        if (_localUri != null && _localUri.length() > 0)
            localUri = Uri.parse(_localUri);
         */

        String peer = c.getString(COLUMN_PEER);
        int direction = c.getInt(COLUMN_DIRECTION);
        if (direction == Messages.DIRECTION_OUT) {
            // we are the origin
            sender = null;
            recipients.add(peer);
        }
        else {
            sender = peer;
            // we are the origin - no recipient
        }

        // TODO groups??
    }

    /** Clears all local fields for recycle. */
    protected void clear() {
        // clear all fields
        mContext = null;
        databaseId = 0;
        id = null;
        sender = null;
        timestamp = 0;
        serverTimestamp = 0;
        statusChanged = 0;
        status = 0;
        encrypted = false;
        security = 0;
    }

    /** Release this message for later use in the global pool. */
    public abstract void recycle();

    public static LegacyAbstractMessage fromCursor(Context context, Cursor cursor) {
        // TODO
        return null;
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null,
                ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId),
                MESSAGE_LIST_PROJECTION, null, null, Messages.DEFAULT_SORT_ORDER);
    }

    public static String buildMediaFilename(LegacyAbstractMessage msg) {
        // TODO
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

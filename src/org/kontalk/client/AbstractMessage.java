/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.client;

import java.io.File;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.kontalk.crypto.Coder;
import org.kontalk.data.MessageID;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads.Conversations;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;
import android.util.Log;


/**
 * An abstract message.
 * FIXME it should be a {@link Parcelable}
 * @author Daniele Ricci
 * @version 1.0
 */
public abstract class AbstractMessage<T> {
    private static final String TAG = AbstractMessage.class.getSimpleName();

    public static final int USERID_LENGTH = 40;
    public static final int USERID_LENGTH_RESOURCE = 48;

    private static final String[] MESSAGE_LIST_PROJECTION = {
        Messages._ID,
        Messages.MESSAGE_ID,
        Messages.REAL_ID,
        Messages.PEER,
        Messages.DIRECTION,
        Messages.TIMESTAMP,
        Messages.STATUS_CHANGED,
        Messages.MIME,
        Messages.CONTENT,
        Messages.STATUS,
        Messages.FETCH_URL,
        Messages.FETCHED,
        Messages.LOCAL_URI,
        Messages.PREVIEW_PATH,
        Messages.ENCRYPTED,
        Messages.ENCRYPT_KEY
    };

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
    protected String realId;
    protected String sender;
    protected String mime;
    protected T content;
    protected long timestamp;
    protected long statusChanged;
    protected int status;
    protected boolean fetched;
    protected MessageID messageId;
    protected boolean encrypted;
    /** Of course this is used only for outgoing messages. */
    protected String encryptKey;

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

    public AbstractMessage(Context context, String id, String sender, String mime, T content, boolean encrypted, List<String> group) {
        this(context, id, sender, mime, content, encrypted);
        this.group = group;
    }

    public AbstractMessage(Context context, String id, String sender, String mime, T content, boolean encrypted) {
        this.mContext = context;

        if (id != null) setId(id);
        this.sender = sender;
        this.mime = mime;
        this.content = content;
        this.recipients = new ArrayList<String>();
        // will be updated if necessary
        this.timestamp = System.currentTimeMillis();

        if (encrypted) {
            this.encrypted = encrypted;
            // with this we avoid of making it null
            encryptKey = "";
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        try {
            this.messageId = MessageID.parse(id);
        }
        catch (ParseException e) {
            if (id == null || !id.startsWith("draft"))
                Log.e(TAG, "invalid server message id - " + id);
        }
    }

    public String getRealId() {
        return (realId != null) ? realId : id;
    }

    public void setRealId(String realId) {
        this.realId = realId;
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

    public String getMime() {
        return mime;
    }

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
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

    /** Returns the timestamp extracted from the server message ID. */
    public Date getServerTimestamp() {
        return (messageId != null) ? messageId.getDate() : null;
    }

    public int getStatus() {
        return status;
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
     */
    public abstract String getTextContent();

    /**
     * Binary contents of this message.
     * Used for storing data representation into the database.
     * @return the byte contents of this message
     */
    public abstract byte[] getBinaryContent();

    /** A sample text content from class name and mime type. */
    public static String getSampleTextContent(Class<? extends AbstractMessage<?>> klass,
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

    public void setFetched(boolean fetched) {
        this.fetched = fetched;
    }

    public boolean isFetched() {
        return this.fetched;
    }

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

    /** Returns true if the message is or was sent encrypted. */
    public boolean wasEncrypted() {
        return (encryptKey != null);
    }

    public void setEncrypted() {
        encrypted = true;
        encryptKey = "";
    }

    /** Reserved for the {@link PollingClient}. */
    protected void setWasEncrypted(boolean encrypted) {
        this.encryptKey = encrypted ? "" : null;
    }

    /** Decrypts the message. */
    public abstract void decrypt(Coder coder) throws GeneralSecurityException;

    protected void populateFromCursor(Cursor c) {
        databaseId = c.getLong(c.getColumnIndex(Messages._ID));
        setId(c.getString(c.getColumnIndex(Messages.MESSAGE_ID)));
        realId = c.getString(c.getColumnIndex(Messages.REAL_ID));
        mime = c.getString(c.getColumnIndex(Messages.MIME));
        timestamp = c.getLong(c.getColumnIndex(Messages.TIMESTAMP));
        statusChanged = c.getLong(c.getColumnIndex(Messages.STATUS_CHANGED));
        status = c.getInt(c.getColumnIndex(Messages.STATUS));
        recipients = new ArrayList<String>();
        fetchUrl = c.getString(c.getColumnIndex(Messages.FETCH_URL));
        fetched = (c.getShort(c.getColumnIndex(Messages.FETCHED)) > 0);
        encrypted = (c.getShort(c.getColumnIndex(Messages.ENCRYPTED)) > 0);
        encryptKey = c.getString(c.getColumnIndex(Messages.ENCRYPT_KEY));

        String peer = c.getString(c.getColumnIndex(Messages.PEER));
        int direction = c.getInt(c.getColumnIndex(Messages.DIRECTION));
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

    public static AbstractMessage<?> fromCursor(Context context, Cursor cursor) {
        String mime = cursor.getString(cursor.getColumnIndex(Messages.MIME));

        if (PlainTextMessage.supportsMimeType(mime)) {
            PlainTextMessage msg = new PlainTextMessage(context);
            msg.populateFromCursor(cursor);
            return msg;
        }

        else if (ImageMessage.supportsMimeType(mime)) {
            ImageMessage msg = new ImageMessage(context);
            msg.populateFromCursor(cursor);
            return msg;
        }

        return null;
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null,
                ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId),
                MESSAGE_LIST_PROJECTION, null, null, Messages.DEFAULT_SORT_ORDER);
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

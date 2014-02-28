/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.List;

import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads.Conversations;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcelable;


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

    public static final String MSG_ID = "org.kontalk.message.id";
    public static final String MSG_SENDER = "org.kontalk.message.sender";
    public static final String MSG_MIME = "org.kontalk.message.mime";
    public static final String MSG_CONTENT = "org.kontalk.message.content";
    public static final String MSG_RECIPIENTS = "org.kontalk.message.recipients";
    public static final String MSG_GROUP = "org.kontalk.message.group";
    public static final String MSG_TIMESTAMP = "org.kontalk.message.timestamp";

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
    	this();

        mContext = context;

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
    private CompositeMessage() {
    	mComponents = new ArrayList<MessageComponent<?>>();
    }

    public String getId() {
        return mId;
    }

    public void setId(String id) {
        mId = id;
    }

    public String getSender(boolean generic) {
        return (generic && mSender.length() > USERID_LENGTH) ?
                mSender.substring(0, USERID_LENGTH) : mSender;
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

    /** Returns the first component of the given type. */
    public MessageComponent<?> getComponent(Class<? extends MessageComponent<?>> type) {
        for (MessageComponent<?> cmp : mComponents) {
            if (type.isInstance(cmp))
                return cmp;
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
        mRecipients = new ArrayList<String>();
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

	        String mime = c.getString(COLUMN_BODY_MIME);

	        if (body != null) {

		        // text data
		        if (TextComponent.supportsMimeType(mime)) {
		        	TextComponent txt = new TextComponent(new String(body));
		        	addComponent(txt);
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
	        		att.populateFromCursor(mContext, c);
	        	}

	        	else if (VCardComponent.supportsMimeType(attMime)) {
	        		att = new VCardComponent(previewFile,
	        				localUri, attFetch, attLength,
	        				attEncrypted, attSecurityFlags);
	        		att.populateFromCursor(mContext, c);
	        	}

	        	// TODO other type of attachments


	        	if (att != null)
	        		addComponent(att);

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
    	CompositeMessage msg = new CompositeMessage();
    	msg.populateFromCursor(cursor);
    	// TODO
        return msg;
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null,
                ContentUris.withAppendedId(Conversations.CONTENT_URI, threadId),
                MESSAGE_LIST_PROJECTION, null, null, Messages.DEFAULT_SORT_ORDER);
    }

    /** A sample text content from class name and mime type. */
    public static String getSampleTextContent(String mime) {
    	// TODO i18n
    	// FIXME using reflection BAD BAD BAD !!!
    	for (Class<AttachmentComponent> klass : TRY_COMPONENTS) {
    		Boolean supported = null;
    		try {
				Method m = klass.getMethod("supportsMimeType", new Class[] { String.class });
				supported = (Boolean) m.invoke(klass, mime);
			}
    		catch (Exception e) {
    			// ignored
			}

    		if (supported != null && supported.booleanValue()) {
		        String cname = klass.getSimpleName();
		        return cname.substring(0, cname.length() - SUFFIX_LENGTH) +
		            ": " + mime;
    		}
    	}

    	// no supporting component - return mime
    	return "Unknown: " + mime;
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

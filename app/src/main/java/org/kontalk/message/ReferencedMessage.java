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

import android.content.Context;
import android.database.Cursor;

import org.kontalk.util.MessageUtils;

import static org.kontalk.provider.MyMessages.*;


/**
 * A simple message object used in contextual reply.
 * Only text messages are supported.
 * @author Daniele Ricci
 * TODO refactor {@link CompositeMessage} to implement an interface that we can use in this class.
 */
public class ReferencedMessage {

    private static final String[] MESSAGE_PROJECTION = {
        Messages._ID,
        Messages.MESSAGE_ID,
        Messages.PEER,
        Messages.DIRECTION,
        Messages.TIMESTAMP,
        Messages.BODY_MIME,
        Messages.BODY_CONTENT,
        //Messages.BODY_LENGTH,
    };

    // these indexes matches MESSAGE_PROJECTION
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_MESSAGE_ID = 1;
    private static final int COLUMN_PEER = 2;
    private static final int COLUMN_DIRECTION = 3;
    private static final int COLUMN_TIMESTAMP = 4;
    private static final int COLUMN_BODY_MIME = 5;
    private static final int COLUMN_BODY_CONTENT = 6;
    //private static final int COLUMN_BODY_LENGTH = 7;

    private long mId;
    private String mMessageId;
    private String mPeer;
    private int mDirection;
    private long mTimestamp;
    private String mTextContent;

    public ReferencedMessage(long id, String messageId, String peer, int direction, long timestamp, String textContent) {
        mId = id;
        mMessageId = messageId;
        mPeer = peer;
        mDirection = direction;
        mTimestamp = timestamp;
        mTextContent = textContent;
    }

    public long getId() {
        return mId;
    }

    public String getMessageId() {
        return mMessageId;
    }

    public String getPeer() {
        return mPeer;
    }

    public int getDirection() {
        return mDirection;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public String getTextContent() {
        return mTextContent;
    }

    private static ReferencedMessage fromCursor(Cursor c) {
        String mime = c.getString(COLUMN_BODY_MIME);
        if (!TextComponent.supportsMimeType(mime))
            throw new IllegalArgumentException("Only text messages are supported");

        long id = c.getLong(COLUMN_ID);
        String msgId = c.getString(COLUMN_MESSAGE_ID);
        String peer = c.getString(COLUMN_PEER);
        int direction = c.getInt(COLUMN_DIRECTION);
        long timestamp = c.getLong(COLUMN_TIMESTAMP);
        byte[] body = c.getBlob(COLUMN_BODY_CONTENT);

        // remove trailing zero
        String bodyText = MessageUtils.toString(body);
        return new ReferencedMessage(id, msgId, peer, direction, timestamp, bodyText);
    }

    public static ReferencedMessage load(Context context, long id) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(Messages.getUri(id),
                MESSAGE_PROJECTION, null, null, null);
            return (c != null && c.moveToNext()) ?
                fromCursor(c) : null;
        }
        finally {
            if (c != null)
                c.close();
        }

    }

    public static ReferencedMessage load(Context context, String messageId) {
        Cursor c = null;
        try {
            c = context.getContentResolver().query(Messages.getUri(messageId),
                MESSAGE_PROJECTION, null, null, null);
            return (c != null && c.moveToNext()) ?
                fromCursor(c) : null;
        }
        finally {
            if (c != null)
                c.close();
        }

    }

}

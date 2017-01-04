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

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.List;

import org.kontalk.R;
import org.kontalk.crypto.Coder;

import android.content.Context;
import android.database.Cursor;
import android.os.Message;


/**
 * A plain text message.
 * Mime type: text/plain
 * @author Daniele Ricci
 * @version 1.0
 */
@Deprecated
public class LegacyPlainTextMessage extends LegacyAbstractMessage<byte[]> {
    //private static final String TAG = PlainTextMessage.class.getSimpleName();

    private static final Object sPoolSync = new Object();
    private static LegacyPlainTextMessage sPool;
    private static int sPoolSize = 0;

    /** Global pool max size. */
    private static final int MAX_POOL_SIZE = 10;

    /** Used for pooling. */
    protected LegacyPlainTextMessage next;

    public static final String MIME_TYPE = "text/plain";

    protected LegacyPlainTextMessage(Context context) {
        super(context, null, 0, null, false);
    }

    public LegacyPlainTextMessage(Context context, String id, long timestamp, String sender, byte[] content, boolean encrypted) {
        this(context, id, timestamp, sender, content, encrypted, null);
    }

    public LegacyPlainTextMessage(Context context, String id, long timestamp, String sender, byte[] content, boolean encrypted, List<String> group) {
        super(context, id, timestamp, sender, encrypted, group);
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        //content = c.getBlob(COLUMN_CONTENT);
    }

    @Override
    public String getTextContent() throws UnsupportedEncodingException {
        if (encrypted)
            return mContext.getResources().getString(R.string.text_encrypted);
        return "ciao";
    }

    @Override
    public byte[] getBinaryContent() {
        return null;
    }

    @Override
    public void decrypt(Coder coder) throws GeneralSecurityException {
        /*
        if (isEncrypted()) {
            // FIXME ehm :)
            StringBuilder clearText = new StringBuilder();
            coder.decryptText(content, true, clearText, null, null);
            content = clearText.toString().getBytes();
            length = content.length;
            encrypted = false;
        }
        */
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

    public void recycle() {
        clear();

        synchronized (sPoolSync) {
            if (sPoolSize < MAX_POOL_SIZE) {
                next = sPool;
                sPool = this;
                sPoolSize++;
            }
        }
    }

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases. Inspired by {@link Message}.
     */
    public static LegacyPlainTextMessage obtain(Context context) {
        synchronized (sPoolSync) {
            if (sPool != null) {
                LegacyPlainTextMessage m = sPool;
                sPool = m.next;
                m.next = null;
                sPoolSize--;
                m.mContext = context;
                return m;
            }
        }
        return new LegacyPlainTextMessage(context);
    }

}

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

import java.security.GeneralSecurityException;
import java.util.List;

import org.kontalk.client.Protocol;
import org.kontalk.crypto.Coder;
import org.kontalk.message.ReceiptEntry.ReceiptEntryList;

import android.content.Context;
import android.util.Log;


/**
 * A receipt messages. Wraps an object of type {@link ReceiptEntryList}.
 * @author Daniele Ricci
 */
public final class ReceiptMessage extends AbstractMessage<ReceiptEntryList> {
    private static final String TAG = ReceiptMessage.class.getSimpleName();

    /** A special mime type for a special type of message. */
    private static final String MIME_TYPE = "internal/receipt";

    private byte[] rawContent;

    protected ReceiptMessage(Context context) {
        super(context, null, null, null, null, null, false);
    }

    public ReceiptMessage(Context context, String id, String timestamp, String sender, byte[] content) {
        this(context, id, timestamp, sender, content, null);
    }

    public ReceiptMessage(Context context, String id, String timestamp, String sender, byte[] content, List<String> group) {
        // receipts are not encrypted
        super(context, id, timestamp, sender, MIME_TYPE, null, false, group);

        this.rawContent = content;
        this.content = new ReceiptEntryList();
        parseContent();
    }

    private void parseContent() {
        try {
            Protocol.ReceiptMessage entries = Protocol.ReceiptMessage.parseFrom(rawContent);
            for (int i = 0; i < entries.getEntryCount(); i++)
                content.add(entries.getEntry(i));
        }
        catch (Exception e) {
            Log.e(TAG, "cannot parse receipt", e);
        }
    }

    @Override
    public String getTextContent() {
        return rawContent.toString();
    }

    @Override
    public byte[] getBinaryContent() {
        return rawContent;
    }

    @Override
    public void decrypt(Coder coder) throws GeneralSecurityException {
        // not implemented
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

    @Override
    public void recycle() {
        // TODO
    }

}

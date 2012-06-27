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

package org.kontalk.message;

import java.security.GeneralSecurityException;
import java.util.List;

import org.kontalk.client.Protocol;
import org.kontalk.crypto.Coder;

import android.content.Context;
import android.util.Log;


/**
 * A message representing a user presence notification.
 * @author Daniele Ricci
 */
public class UserPresenceMessage extends AbstractMessage<UserPresenceData> {
    private static final String TAG = UserPresenceMessage.class.getSimpleName();

    /** A special mime type for a special type of message. */
    private static final String MIME_TYPE = "internal/presence";

    protected UserPresenceMessage(Context context) {
        super(context, null, null, null, null, null, false);
    }

    public UserPresenceMessage(Context context, String id, String timestamp, String sender, byte[] content) {
        this(context, id, timestamp, sender, content, null);
    }

    public UserPresenceMessage(Context context, String id, String timestamp, String sender, byte[] content, List<String> group) {
        // presence messages are not encrypted
        super(context, id, timestamp, sender, MIME_TYPE, null, false, group);

        this.content = parseContent(content);
    }

    private UserPresenceData parseContent(byte[] content) {
        try {
            Protocol.UserPresence entry = Protocol.UserPresence.parseFrom(content);
            String statusMsg = (entry.hasStatusMessage()) ? entry.getStatusMessage() : null;
            return new UserPresenceData(entry.getEvent().getNumber(), statusMsg);
        }
        catch (Exception e) {
            Log.e(TAG, "cannot parse presence message", e);
            return null;
        }
    }

    @Override
    public String getTextContent() {
        if (content != null)
            return sender + "/" + content.event;
        return null;
    }

    @Override
    public byte[] getBinaryContent() {
        return null;
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

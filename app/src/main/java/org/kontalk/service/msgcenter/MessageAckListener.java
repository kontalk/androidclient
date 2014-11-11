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

package org.kontalk.service.msgcenter;

import java.util.Map;

import org.jivesoftware.smack.packet.Packet;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;

import org.kontalk.provider.MyMessages.Messages;


/**
 * Packet listener for message ack processing.
 * @author Daniele Ricci
 */
class MessageAckListener extends MessageCenterPacketListener {

    private static final String selectionOutgoing = Messages.DIRECTION + "=" + Messages.DIRECTION_OUT;

    public MessageAckListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        Map<String, Long> waitingReceipt = getWaitingReceiptList();

        synchronized (waitingReceipt) {
            String id = packet.getPacketID();
            Long _msgId = waitingReceipt.remove(id);
            long msgId = (_msgId != null) ? _msgId : 0;
            ContentResolver cr = getContext().getContentResolver();

            long now = System.currentTimeMillis();

            if (msgId > 0) {
                // we have a message awaiting ack from server
                ContentValues values = new ContentValues(3);
                values.put(Messages.STATUS, Messages.STATUS_SENT);
                values.put(Messages.STATUS_CHANGED, now);
                values.put(Messages.SERVER_TIMESTAMP, now);
                cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                    values, selectionOutgoing, null);

                // we can now release the message center. Hopefully
                // there will be one hold and one matching release.
                getIdleHandler().release();
            }
            else {
                // the user wasn't expecting ack for this message
                // so we simply update it using the packet id as key
                Uri msg = Messages.getUri(id);
                ContentValues values = new ContentValues(3);
                values.put(Messages.STATUS, Messages.STATUS_SENT);
                values.put(Messages.STATUS_CHANGED, now);
                values.put(Messages.SERVER_TIMESTAMP, now);
                cr.update(msg, values, selectionOutgoing, null);
            }

        }

    }
}

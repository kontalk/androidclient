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

package org.kontalk.service.msgcenter;

import java.util.Map;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;

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

    // condition on delivered status in case we receive the receipt before the ack
    private static final String selectionOutgoing = Messages.DIRECTION + "=" + Messages.DIRECTION_OUT + " AND " +
        Messages.STATUS + " NOT IN (" + Messages.STATUS_RECEIVED + "," + Messages.STATUS_NOTDELIVERED + ")";
    private static final String selectionIncoming = Messages.DIRECTION + "=" + Messages.DIRECTION_IN;

    public MessageAckListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        if (!(packet instanceof Message)) {
            return;
        }

        Map<String, Long> waitingReceipt = getWaitingReceiptList();

        synchronized (waitingReceipt) {
            String id = packet.getStanzaId();
            Long _msgId = waitingReceipt.remove(id);
            long msgId = (_msgId != null) ? _msgId : 0;
            ContentResolver cr = getContext().getContentResolver();

            long now = System.currentTimeMillis();

            DeliveryReceipt receipt = DeliveryReceipt.from((Message) packet);
            if (receipt != null) {
                // ack received for outgoing delivery receipt
                // mark message as confirmed
                ContentValues values = new ContentValues(1);
                values.put(Messages.STATUS, Messages.STATUS_CONFIRMED);
                cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                    values, selectionIncoming, null);

                waitingReceipt.remove(id);
            }

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
                release();
            }
            else if (id != null) {
                // the user wasn't expecting ack for this message
                // so we simply update it using the packet id as key
                // FIXME this could lead to fake acks because message IDs are client-generated
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

/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;

import org.kontalk.provider.MessagesProviderClient.MessageUpdater;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.util.WakefulHashSet;


/**
 * Packet listener for message ack processing.
 * @author Daniele Ricci
 */
class MessageAckListener extends MessageCenterPacketListener {

    private static final String SELECTION_SENT_EXCLUDE =
        Messages.STATUS + " NOT IN (" + Messages.STATUS_RECEIVED + "," + Messages.STATUS_NOTDELIVERED + ")";

    private final long mDatabaseId;

    public MessageAckListener(MessageCenterService instance, long databaseId) {
        super(instance);
        mDatabaseId = databaseId;
    }

    @Override
    public void processStanza(Stanza packet) {
        // stanzas coming here are always messages

        WakefulHashSet<Long> waitingReceipt = getWaitingReceiptList();

        synchronized (waitingReceipt) {
            long now = System.currentTimeMillis();

            DeliveryReceipt receipt = DeliveryReceipt.from((Message) packet);
            if (receipt != null) {
                // ack received for outgoing delivery receipt
                // mark message as confirmed
                MessageUpdater.forMessage(getContext(), mDatabaseId)
                    .setStatus(Messages.STATUS_CONFIRMED)
                    .commit();
            }
            else {
                // we have a message awaiting ack from server
                MessageUpdater.forMessage(getContext(), mDatabaseId)
                    .setStatus(Messages.STATUS_SENT, now)
                    .setServerTimestamp(now)
                    .notifyOutgoing(packet.getTo().asBareJid().toString())
                    // this will handle receipts that came before the message was acked by the server
                    .appendWhere(SELECTION_SENT_EXCLUDE)
                    .commit();
            }

            // remove the packet from the waiting list
            // this will also release the wake lock
            waitingReceipt.remove(mDatabaseId);

            // we can now release the message center. Hopefully
            // there will be one hold and one matching release.
            release();
        }
    }
}

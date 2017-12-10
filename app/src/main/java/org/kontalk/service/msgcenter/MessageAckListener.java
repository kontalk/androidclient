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

import org.kontalk.provider.MessagesProviderClient.MessageUpdater;
import org.kontalk.provider.MyMessages.Messages;


/**
 * Packet listener for message ack processing.
 * @author Daniele Ricci
 */
class MessageAckListener extends MessageCenterPacketListener {

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
            Long _msgId = waitingReceipt.get(id);
            long msgId = (_msgId != null) ? _msgId : 0;

            long now = System.currentTimeMillis();

            DeliveryReceipt receipt = DeliveryReceipt.from((Message) packet);
            if (receipt != null) {
                // ack received for outgoing delivery receipt
                // mark message as confirmed
                MessageUpdater.forMessage(getContext(), msgId)
                    .setStatus(Messages.STATUS_CONFIRMED)
                    .commit();
            }

            if (msgId > 0) {
                // we have a message awaiting ack from server
                MessageUpdater.forMessage(getContext(), msgId)
                    .setStatus(Messages.STATUS_SENT, now)
                    .setServerTimestamp(now)
                    .commit();

                // we can now release the message center. Hopefully
                // there will be one hold and one matching release.
                release();
            }
            else if (id != null) {
                // the user wasn't expecting ack for this message
                // so we simply update it using the packet id as key
                // FIXME this could lead to fake acks because message IDs are client-generated
                MessageUpdater.forMessage(getContext(), id, false)
                    .setStatus(Messages.STATUS_SENT, now)
                    .setServerTimestamp(now)
                    .commit();
            }

            // remove the packet from the waiting list
            // this will also release the wake lock
            waitingReceipt.remove(id);
        }
    }
}

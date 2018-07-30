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

import java.util.List;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;


/**
 * Packet listener for service discovery (items).
 * @author Daniele Ricci
 */
class DiscoverItemsListener extends MessageCenterPacketListener {

    public DiscoverItemsListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        XMPPConnection conn = getConnection();

        // we don't need this listener anymore
        conn.removeAsyncStanzaListener(this);

        DiscoverItems query = (DiscoverItems) packet;
        List<DiscoverItems.Item> items = query.getItems();
        for (DiscoverItems.Item item : items) {
            DiscoverInfo info = new DiscoverInfo();
            info.setTo(item.getEntityID());

            StanzaFilter filter = new StanzaIdFilter(info.getStanzaId());
            conn.addAsyncStanzaListener(new DiscoverInfoListener(getInstance()), filter);
            sendPacket(info);
        }
    }
}


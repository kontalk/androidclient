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

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_ROSTER;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TYPE;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_JIDLIST;

import java.util.Collection;
import java.util.Iterator;

import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.RosterPacket;

import android.content.Intent;


/**
 * Packet listener for roster iq stanzas.
 * @author Daniele Ricci
 */
class RosterListener extends MessageCenterPacketListener {

    public RosterListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        RosterPacket p = (RosterPacket) packet;
        Intent i = new Intent(ACTION_ROSTER);
        i.putExtra(EXTRA_FROM, p.getFrom());
        i.putExtra(EXTRA_TO, p.getTo());
        // here we are not using() because Type is a class, not an enum
        i.putExtra(EXTRA_TYPE, p.getType().toString());
        i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

        Collection<RosterPacket.Item> items = p.getRosterItems();
        String[] list = new String[items.size()];

        int index = 0;
        for (Iterator<RosterPacket.Item> iter = items.iterator(); iter.hasNext(); ) {
            RosterPacket.Item item = iter.next();
            list[index] = item.getUser();
            index++;
        }

        i.putExtra(EXTRA_JIDLIST, list);

        sendBroadcast(i);
    }
}

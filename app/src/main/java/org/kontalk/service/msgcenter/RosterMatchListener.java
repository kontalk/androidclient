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

import java.util.List;

import org.jivesoftware.smack.packet.Stanza;

import android.content.Intent;

import org.kontalk.client.RosterMatch;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_ROSTER_MATCH;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_JIDLIST;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TYPE;


/**
 * Packet listener for roster match iq stanzas.
 * @author Daniele Ricci
 */
class RosterMatchListener extends MessageCenterPacketListener {

    public RosterMatchListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        if (packet.getError() == null) {
            RosterMatch p = (RosterMatch) packet;
            Intent i = new Intent(ACTION_ROSTER_MATCH);
            i.putExtra(EXTRA_FROM, p.getFrom().toString());
            i.putExtra(EXTRA_TO, p.getTo().toString());
            i.putExtra(EXTRA_TYPE, p.getType().toString());
            i.putExtra(EXTRA_PACKET_ID, p.getStanzaId());

            List<String> items = p.getItems();
            if (items != null) {
                String[] list = new String[items.size()];
                i.putExtra(EXTRA_JIDLIST, items.toArray(list));
            }

            sendBroadcast(i);
        }
    }
}

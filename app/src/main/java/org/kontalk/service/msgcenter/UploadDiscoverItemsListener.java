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

import java.util.List;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jxmpp.util.XmppStringUtils;

import org.kontalk.client.EndpointServer;
import org.kontalk.client.UploadInfo;


/**
 * Packet listener for requesting upload services information.
 * @author Daniele Ricci
 */
class UploadDiscoverItemsListener extends MessageCenterPacketListener {

    public UploadDiscoverItemsListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        XMPPConnection conn = getConnection();
        EndpointServer server = getServer();

        // we don't need this listener anymore
        conn.removePacketListener(this);

        initUploadServices();

        // store available services
        DiscoverItems query = (DiscoverItems) packet;
        List<DiscoverItems.Item> items = query.getItems();
        for (DiscoverItems.Item item : items) {
            String jid = item.getEntityID();
            if (jid != null && server.getNetwork().equals(XmppStringUtils.parseDomain(jid))) {
                setUploadService(item.getNode(), null);

                // request upload url
                UploadInfo iq = new UploadInfo(item.getNode());
                iq.setType(IQ.Type.get);
                iq.setTo("upload@" + server.getNetwork());

                conn.addPacketListener(new UploadInfoListener(getInstance()), new PacketIDFilter(iq.getPacketID()));
                sendPacket(iq);
            }
        }
    }
}


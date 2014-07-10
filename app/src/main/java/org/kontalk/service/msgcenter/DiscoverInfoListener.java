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
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.PushRegistration;
import org.kontalk.client.UploadExtension;


/**
 * Packet listener for service discovery.
 * @author Daniele Ricci
 */
class DiscoverInfoListener extends MessageCenterPacketListener {

    public DiscoverInfoListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        XMPPConnection conn = getConnection();
        EndpointServer server = getServer();

        // we don't need this listener anymore
        conn.removePacketListener(this);

        DiscoverInfo query = (DiscoverInfo) packet;
        List<DiscoverInfo.Feature> features = query.getFeatures();
        for (DiscoverInfo.Feature feat : features) {

            /*
             * TODO do not request info about push if disabled by user.
             * Of course if user enables push notification we should
             * reissue this discovery again.
             */
            if (PushRegistration.NAMESPACE.equals(feat.getVar())) {
                // push notifications are enabled on this server
                // request items to check if gcm is supported and obtain the server id
                DiscoverItems items = new DiscoverItems();
                items.setNode(PushRegistration.NAMESPACE);
                items.setTo(server.getNetwork());

                PacketFilter filter = new PacketIDFilter(items.getPacketID());
                conn.addPacketListener(new PushDiscoverItemsListener(getInstance()), filter);

                sendPacket(items);
            }

            /*
             * TODO upload info should be requested only when needed and
             * cached. This discovery should of course be issued before any
             * media message gets requeued.
             * Actually, delay any message from being requeued if at least
             * 1 media message is present; do the discovery first.
             */
            else if (UploadExtension.NAMESPACE.equals(feat.getVar())) {
                // media upload is available on this server
                // request items to check what services are available
                DiscoverItems items = new DiscoverItems();
                items.setNode(UploadExtension.NAMESPACE);
                items.setTo(server.getNetwork());

                PacketFilter filter = new PacketIDFilter(items.getPacketID());
                conn.addPacketListener(new UploadDiscoverItemsListener(getInstance()), filter);

                sendPacket(items);
            }
        }
    }
}


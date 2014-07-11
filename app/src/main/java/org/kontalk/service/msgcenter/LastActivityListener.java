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

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_LAST_ACTIVITY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM_USERID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_GROUP_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_GROUP_COUNT;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_SECONDS;

import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.kontalk.client.StanzaGroupExtension;

import android.content.Intent;
import android.util.Log;


/**
 * Packet listener for last activity iq.
 * @author Daniele Ricci
 */
class LastActivityListener extends MessageCenterPacketListener {

    public LastActivityListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        LastActivity p = (LastActivity) packet;
        Intent i = new Intent(ACTION_LAST_ACTIVITY);
        i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

        String from = p.getFrom();
        String network = StringUtils.parseServer(from);
        // our network - convert to userId
        if (network.equalsIgnoreCase(getServer().getNetwork())) {
            StringBuilder b = new StringBuilder();
            b.append(StringUtils.parseName(from));
            b.append(StringUtils.parseResource(from));
            i.putExtra(EXTRA_FROM_USERID, b.toString());
        }

        i.putExtra(EXTRA_FROM, from);
        i.putExtra(EXTRA_TO, p.getTo());
        i.putExtra(EXTRA_SECONDS, p.lastActivity);

        // non-standard stanza group extension
        PacketExtension ext = p.getExtension(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE);
        if (ext != null && ext instanceof StanzaGroupExtension) {
            StanzaGroupExtension g = (StanzaGroupExtension) ext;
            i.putExtra(EXTRA_GROUP_ID, g.getId());
            i.putExtra(EXTRA_GROUP_COUNT, g.getCount());
        }

        Log.v(MessageCenterService.TAG, "broadcasting presence: " + i);
        sendBroadcast(i);
    }
}

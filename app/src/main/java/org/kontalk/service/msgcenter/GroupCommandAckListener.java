/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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


import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Stanza;

import org.kontalk.client.KontalkGroupManager;

/**
 * Packet listener to handle acks for group command messages.
 * @author Daniele Ricci
 */
public class GroupCommandAckListener extends MessageCenterPacketListener {

    private final KontalkGroupManager.KontalkGroup mGroup;

    GroupCommandAckListener(MessageCenterService instance, KontalkGroupManager.KontalkGroup group) {
        super(instance);
        mGroup = group;
    }

    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        // TODO handle command
    }
}

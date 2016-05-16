/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import android.net.Uri;

import org.kontalk.client.GroupExtension;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.provider.MyMessages.Groups;


/**
 * Packet listener to handle acks for group command messages.
 * @author Daniele Ricci
 */
public class GroupCommandAckListener extends MessageCenterPacketListener {

    private final KontalkGroupManager.KontalkGroup mGroup;
    private final GroupExtension mExtension;
    private final Uri mCommandMessage;

    /**
     * Builds a new group command ack listener.
     * @param commandMessage if not null, this Uri will be deleted after successful confirmation
     */
    GroupCommandAckListener(MessageCenterService instance, KontalkGroupManager.KontalkGroup group, GroupExtension extension, Uri commandMessage) {
        super(instance);
        mGroup = group;
        mExtension = extension;
        mCommandMessage = commandMessage;
    }

    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        GroupExtension.Type type = mExtension.getType();

        switch (type) {
            case SET:
                // TODO clear pending subject or member add/remove
                break;
            case PART:
                // delete group (members will cascade)
                getContext().getContentResolver()
                    .delete(Groups.getUri(mGroup.getJID()), null, null);
                if (mCommandMessage != null) {
                    getContext().getContentResolver()
                        .delete(mCommandMessage, null, null);
                }
                break;
        }
    }
}

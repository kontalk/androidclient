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

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.Stanza;

import android.net.Uri;

import org.kontalk.client.GroupExtension;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.service.msgcenter.event.GroupCreatedEvent;


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
    public GroupCommandAckListener(MessageCenterService instance, KontalkGroupManager.KontalkGroup group, GroupExtension extension, Uri commandMessage) {
        super(instance);
        mGroup = group;
        mExtension = extension;
        mCommandMessage = commandMessage;
    }

    @Override
    public void processStanza(Stanza packet) throws SmackException.NotConnectedException {
        GroupExtension.Type type = mExtension.getType();

        switch (type) {
            case SET:
                // clear pending member add/remove
                List<GroupExtension.Member> members = mExtension.getMembers();
                for (GroupExtension.Member m : members) {
                    if (m.operation == GroupExtension.Member.Operation.ADD) {
                        // mark user as added (clear pending flag)
                        getContext().getContentResolver().update(Groups
                            .getMembersUri(mGroup.getJID()).buildUpon()
                            .appendPath(m.jid)
                            .appendQueryParameter(Messages.CLEAR_PENDING,
                                String.valueOf(Groups.MEMBER_PENDING_ADDED))
                            .build(), null, null, null);
                    }
                    else if (m.operation == GroupExtension.Member.Operation.REMOVE) {
                        // remove the user
                        getContext().getContentResolver().delete(Groups
                            .getMembersUri(mGroup.getJID()).buildUpon()
                            .appendPath(m.jid)
                            .build(), null, null);
                    }
                }
            case CREATE:
                // resend pending stuff -- this will continue the delivery of
                // group messages that were waiting for this group command
                MessageCenterService.bus()
                    .post(new GroupCreatedEvent());
                break;
            case PART:
                if (mCommandMessage != null) {
                    // delete message only if it's not linked to a thread
                    if (getContext().getContentResolver()
                            .delete(mCommandMessage, Messages.THREAD_ID + " < 0", null) > 0) {
                        // group should be deleted only if the message was not linked to a thread
                        // that is, no thread exists anymore for this group, so no reason to keep the group
                        // delete group (members will cascade)
                        getContext().getContentResolver()
                            .delete(Groups.getUri(mGroup.getJID()), null, null);
                    }
                }
                break;
        }
    }
}

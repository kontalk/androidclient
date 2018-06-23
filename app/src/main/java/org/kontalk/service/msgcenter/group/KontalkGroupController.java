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

package org.kontalk.service.msgcenter.group;

import java.util.HashSet;
import java.util.Set;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.sm.StreamManagementException;
import org.jxmpp.stringprep.XmppStringprepException;

import android.net.Uri;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.client.GroupExtension;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.client.XMPPTCPConnection;
import org.kontalk.provider.MyMessages;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.msgcenter.GroupCommandAckListener;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.SystemUtils;


public class KontalkGroupController implements GroupController<Message> {
    private static final String TAG = KontalkGroupController.class.getSimpleName();

    /** Group type identifier. */
    public static final String GROUP_TYPE = "kontalk";

    private final XMPPTCPConnection mConnection;
    private final MessageCenterService mInstance;

    public KontalkGroupController(XMPPConnection connection, MessageCenterService instance) {
        mConnection = (XMPPTCPConnection) connection;
        mInstance = instance;
    }

    @Override
    public String getGroupType() {
        return GROUP_TYPE;
    }

    @Override
    public Message beforeEncryption(GroupCommand command, Stanza packet) {
        String groupJid = command.getGroupJid();
        KontalkGroupManager.KontalkGroup group;
        try {
            group = KontalkGroupManager.getInstanceFor(mConnection)
                .getGroup(groupJid);
        }
        catch (XmppStringprepException e) {
            Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
            // report it because it's a big deal
            ReportingManager.logException(e);
            return null;
        }

        if (packet == null)
            packet = new Message();

        if (command instanceof CreateGroupCommand) {
            KontalkCreateGroupCommand create = (KontalkCreateGroupCommand) command;
            group.create(create.getSubject(), create.getMembers(), packet);
        }
        else if (command instanceof SetSubjectCommand) {
            KontalkSetSubjectCommand setSubject = (KontalkSetSubjectCommand) command;
            group.setSubject(setSubject.getSubject(), packet);
        }
        else if (command instanceof AddRemoveMembersCommand) {
            KontalkAddRemoveMembersCommand addRemove = (KontalkAddRemoveMembersCommand) command;
            String[] added = addRemove.getAddedMembers();
            String[] members = addRemove.getMembers();
            Set<String> filteredMembers = new HashSet<>();
            for (String member : members) {
                // do not include added users in members list
                if (added == null || !SystemUtils.contains(added, member)) {
                    filteredMembers.add(member);
                }
            }
            group.addRemoveMembers(addRemove.getSubject(),
                filteredMembers.toArray(new String[filteredMembers.size()]),
                addRemove.getAddedMembers(), addRemove.getRemovedMembers(), packet);
        }
        else if (command instanceof PartCommand) {
            group.leave(packet);
        }
        else if (command instanceof InfoCommand) {
            group.groupInfo(packet);
        }

        // add a fallback body for clients not supporting our protocol
        if (!(command instanceof InfoCommand) && ((Message) packet).getBody() == null) {
            ((Message) packet).setBody(mInstance.getResources()
                .getString(R.string.text_group_command_fallback));
        }

        return (Message) packet;
    }

    @Override
    public Message afterEncryption(GroupCommand command, Stanza packet, Stanza original) {
        if (packet == null)
            throw new IllegalArgumentException("packet must be provided");
        if (!(command instanceof KontalkGroupCommand))
            throw new IllegalArgumentException("invalid command");

        String groupJid = command.getGroupJid();
        KontalkGroupManager.KontalkGroup group;
        try {
            group = KontalkGroupManager.getInstanceFor(mConnection)
                .getGroup(groupJid);
        }
        catch (XmppStringprepException e) {
            Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
            // report it because it's a big deal
            ReportingManager.logException(e);
            return null;
        }

        if (command instanceof PartCommand) {
            try {
                String id = packet.getStanzaId();
                long msgId = ((PartCommand) command).getDatabaseId();

                // delete the command afterwards (only for part commands)
                Uri msgUri = (msgId > 0) ? MyMessages.Messages.getUri(msgId) : null;
                // wait for confirmation
                mConnection.addStanzaIdAcknowledgedListener(id,
                    new GroupCommandAckListener(mInstance, group,
                        GroupExtension.from(original), msgUri));
            }
            catch (StreamManagementException.StreamManagementNotEnabledException e) {
                Log.e(TAG, "server does not support stream management?!?");
                // weird situation, report it
                ReportingManager.logException(e);
            }
        }
        else if (command instanceof CreateGroupCommand || command instanceof AddRemoveMembersCommand) {
            try {
                String id = packet.getStanzaId();

                // wait for confirmation
                mConnection.addStanzaIdAcknowledgedListener(id,
                    new GroupCommandAckListener(mInstance, group,
                        GroupExtension.from(original), null));
            }
            catch (StreamManagementException.StreamManagementNotEnabledException e) {
                Log.e(TAG, "server does not support stream management?!?");
                // weird situation, report it
                ReportingManager.logException(e);
            }
        }

        try {
            KontalkGroupCommand cmd = (KontalkGroupCommand) command;
            group.addRouteExtension(cmd.getMembers(), packet);
        }
        catch (XmppStringprepException e) {
            Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
            // report it because it's a big deal
            ReportingManager.logException(e);
            return null;
        }
        return (Message) packet;
    }

    @Override
    public CreateGroupCommand createGroup() {
        return new KontalkCreateGroupCommand();
    }

    @Override
    public SetSubjectCommand setSubject() {
        return new KontalkSetSubjectCommand();
    }

    @Override
    public PartCommand part() {
        return new KontalkPartCommand();
    }

    public AddRemoveMembersCommand addRemoveMembers() {
        return new KontalkAddRemoveMembersCommand();
    }

    @Override
    public InfoCommand info() {
        return new KontalkGroupInfoCommand();
    }

    @Override
    public boolean canSendCommandsWithEmptyGroup() {
        return false;
    }

    @Override
    public boolean canSendWithNoSubscription() {
        return true;
    }

}

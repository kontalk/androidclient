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

import org.jivesoftware.smack.packet.Stanza;


/**
 * Interface for group protocol controllers.
 * @author Daniele Ricci
 */
public interface GroupController<T extends Stanza> {

    /** Returns the group type. */
    String getGroupType();

    /** Returns a new stanza with group command information before encrypting it. */
    T beforeEncryption(GroupCommand command, Stanza packet);

    /** Returns a new stanza with group command information after it's been encrypted. */
    T afterEncryption(GroupCommand command, Stanza packet, Stanza original);

    /** Returns a new create group command. */
    CreateGroupCommand createGroup();

    /** Returns a new set group subject command. */
    SetSubjectCommand setSubject();

    /** Returns a new leave group command. */
    PartCommand part();

    /** Returns a new add/remove command members command. */
    AddRemoveMembersCommand addRemoveMembers();

    /** Returns a new group info command. */
    InfoCommand info();

    /** Returns true if this group controller should allow sending a command to an empty group. */
    boolean canSendCommandsWithEmptyGroup();

    /**
     * Returns true if this group controller should allow sending messages to
     * the group even if not all users have allowed subscription.
     */
    boolean canSendWithNoSubscription();

}

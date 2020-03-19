/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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


import org.jxmpp.jid.Jid;

public class KontalkAddRemoveMembersCommand extends KontalkGroupCommand
        implements AddRemoveMembersCommand {

    private String mSubject;
    private Jid[] mAdded;
    private Jid[] mRemoved;

    @Override
    public void setSubject(String subject) {
        mSubject = subject;
    }

    @Override
    public String getSubject() {
        return mSubject;
    }

    @Override
    public void setAddedMembers(Jid[] members) {
        mAdded = members;
    }

    @Override
    public Jid[] getAddedMembers() {
        return mAdded;
    }

    @Override
    public void setRemovedMembers(Jid[] members) {
        mRemoved = members;
    }

    @Override
    public Jid[] getRemovedMembers() {
        return mRemoved;
    }

}

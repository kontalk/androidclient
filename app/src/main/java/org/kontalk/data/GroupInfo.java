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

package org.kontalk.data;


/**
 * Group chat information.
 * @author Daniele Ricci
 */
public class GroupInfo {
    private final String mJid;
    private final String mSubject;
    private final String[] mMembers;
    private final String[] mAddMembers;
    private final String[] mRemoveMembers;
    private final String mPartMember;

    public GroupInfo(String jid, String subject, String[] members, String[] addMembers, String[] removeMembers, String partMember) {
        mJid = jid;
        mSubject = subject;
        mMembers = members;
        mAddMembers = addMembers;
        mRemoveMembers = removeMembers;
        mPartMember = partMember;
    }

    public String getJid() {
        return mJid;
    }

    public String getSubject() {
        return mSubject;
    }

    public String[] getMembers() {
        return mMembers;
    }

    public String[] getAddMembers() {
        return mAddMembers;
    }

    public String[] getRemoveMembers() {
        return mRemoveMembers;
    }

    public String getPartMember() {
        return mPartMember;
    }

}

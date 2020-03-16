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

package org.kontalk.data;

import org.jxmpp.jid.Jid;


/**
 * Group chat information.
 * @author Daniele Ricci
 */
public class GroupInfo {
    private final Jid mJid;
    private final String mSubject;
    private final String mType;
    private final int mMembership;

    public GroupInfo(Jid jid, String subject, String type, int membership) {
        mJid = jid;
        mSubject = subject;
        mType = type;
        mMembership = membership;
    }

    public Jid getJid() {
        return mJid;
    }

    public String getSubject() {
        return mSubject;
    }

    public String getType() {
        return mType;
    }

    public int getMembership() {
        return mMembership;
    }
}

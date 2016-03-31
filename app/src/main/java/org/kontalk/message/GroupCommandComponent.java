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

package org.kontalk.message;

import java.util.ArrayList;
import java.util.List;

import org.kontalk.client.GroupExtension;


/**
 * Virtual component for group commands.
 * @author Daniele Ricci
 */
public class GroupCommandComponent extends MessageComponent<GroupExtension> {

    public static final String MIME_TYPE = "application/x-kontalk-group";

    private final String mFrom;

    public GroupCommandComponent(GroupExtension content, String from) {
        super(content, 0, false, 0);
        this.mFrom = from;
    }

    public String getFrom() {
        return mFrom;
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

    public static List<GroupExtension.Member> membersFromJIDs(String[] members) {
        List<GroupExtension.Member> list = new ArrayList<>(members.length);
        for (String m : members)
            list.add(new GroupExtension.Member(m, GroupExtension.Member.Operation.NONE));
        return list;
    }

}

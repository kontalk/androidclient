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

package org.kontalk.service.msgcenter.group;

import org.jivesoftware.smack.XMPPConnection;

import org.kontalk.service.msgcenter.MessageCenterService;

/**
 * Factory for creating {@link GroupController}s.
 * @author Daniele Ricci
 */
public class GroupControllerFactory {

    private GroupControllerFactory() {
    }

    public static GroupController createController(String groupType, XMPPConnection connection, MessageCenterService instance) {
        // do not use reflection EVER :)

        if (groupType.equals(KontalkGroupController.GROUP_TYPE)) {
            return new KontalkGroupController(connection, instance);
        }

        throw new IllegalArgumentException("Unsupported group type: " + groupType);
    }

}

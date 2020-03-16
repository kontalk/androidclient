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

import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.XMPPConnection;

import org.kontalk.service.msgcenter.MessageCenterService;

/**
 * Factory for creating {@link GroupController}s.
 * @author Daniele Ricci
 */
public class GroupControllerFactory {

    private static Map<String, GroupController> CHECK_INSTANCES;

    private GroupControllerFactory() {
    }

    private static GroupController getControllerInstance(String groupType) {
        GroupController instance = null;
        if (CHECK_INSTANCES == null) {
            CHECK_INSTANCES = new HashMap<>();
        }
        else {
            instance = CHECK_INSTANCES.get(groupType);
        }
        if (instance == null) {
            instance = createController(groupType);
            CHECK_INSTANCES.put(groupType, instance);
        }
        return instance;
    }

    private static GroupController createController(String groupType) {
        return createController(groupType, null, null);
    }

    public static GroupController createController(String groupType, XMPPConnection connection, MessageCenterService instance) {
        // do not use reflection EVER :)

        if (groupType.equals(KontalkGroupController.GROUP_TYPE)) {
            return new KontalkGroupController(connection, instance);
        }

        throw new IllegalArgumentException("Unsupported group type: " + groupType);
    }

    public static boolean canSendCommandsWithEmptyGroup(String groupType) {
        return getControllerInstance(groupType).canSendCommandsWithEmptyGroup();
    }

}

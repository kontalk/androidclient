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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.Context;
import android.database.Cursor;

import org.kontalk.client.GroupExtension;


/**
 * Component for group commands.
 * @author Daniele Ricci
 */
public class GroupCommandComponent extends MessageComponent<GroupExtension> {

    /** Group type identifier. */
    public static final String GROUP_TYPE = "kontalk";
    /** MIME type for command messages. */
    public static final String MIME_TYPE = "application/x-kontalk-group";

    // create group
    public static final String COMMAND_CREATE = "create";
    // leave group
    public static final String COMMAND_PART = "part";
    // TODO set subject

    private final String mFrom;
    private final String mOwnJid;

    public GroupCommandComponent(GroupExtension content, String from, String ownJid) {
        super(content, 0, false, 0);
        mFrom = from;
        mOwnJid = ownJid;
    }

    public String getFrom() {
        return mFrom;
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

    public boolean isCreateCommand() {
        return mContent.getType() == GroupExtension.Type.CREATE;
    }

    public boolean isPartCommand() {
        return mContent.getType() == GroupExtension.Type.PART;
    }

    public boolean isAddOrRemoveCommand() {
        return mContent.getType() == GroupExtension.Type.SET;
    }

    public String[] getCreateMembers() {
        if (!isCreateCommand())
            throw new UnsupportedOperationException("Not a create command");
        return flattenOperationMembers(GroupExtension.Member.Operation.NONE, true);
    }

    public String[] getExistingMembers() {
        return flattenOperationMembers(GroupExtension.Member.Operation.NONE, true);
    }

    public String[] getAddedMembers() {
        if (!isAddOrRemoveCommand())
            throw new UnsupportedOperationException("Not an add or remove command");
        return flattenOperationMembers(GroupExtension.Member.Operation.ADD, false);
    }

    public String[] getRemovedMembers() {
        if (!isAddOrRemoveCommand())
            throw new UnsupportedOperationException("Not an add or remove command");
        return flattenOperationMembers(GroupExtension.Member.Operation.REMOVE, false);
    }

    private String[] flattenOperationMembers(GroupExtension.Member.Operation operation, boolean includeOwner) {
        List<GroupExtension.Member> members = mContent.getMembers();
        Set<String> output = new HashSet<>();
        if (includeOwner)
            output.add(mContent.getOwner());

        for (int i = 0; i < members.size(); i++) {
            GroupExtension.Member user = members.get(i);
            // exclude own JID from the list
            if (user.operation == operation && !user.jid.equalsIgnoreCase(mOwnJid))
                output.add(user.jid);
        }
        return output.toArray(new String[output.size()]);
    }

    /** Returns the text to be used as the body content in the database. */
    public String getTextContent() {
        if (isCreateCommand())
            return getCreateBodyContent(getCreateMembers());
        else if (isPartCommand())
            return COMMAND_PART;
        else if (isAddOrRemoveCommand()) {
            return getAddMembersBodyContent(getAddedMembers()) +
                getRemoveMembersBodyContent(getRemovedMembers());
        }

        // TODO
        throw new UnsupportedOperationException("Unsupported group command");
    }

    public static String getCreateBodyContent(String[] members) {
        StringBuilder out = new StringBuilder("create:");
        for (String m : members)
            out.append(m).append(";");
        return out.toString();
    }

    public static String getAddMembersBodyContent(String[] members) {
        StringBuilder out = new StringBuilder();
        for (String m : members)
            out.append("add:").append(m).append(";");
        return out.toString();
    }

    public static String getRemoveMembersBodyContent(String[] members) {
        StringBuilder out = new StringBuilder();
        for (String m : members)
            out.append("remove:").append(m).append(";");
        return out.toString();
    }

    public static String[] getCreateCommandMembers(String body) {
        String prefix = GroupCommandComponent.COMMAND_CREATE + ":";
        if (body.startsWith(prefix)) {
            String[] params = body.substring(prefix.length()).split(";");
            Set<String> members = new HashSet<>();
            for (String m : params) {
                if (m.length() > 0)
                    members.add(m);
            }
            return members.toArray(new String[members.size()]);
        }
        return null;
    }

    private static String[] getPrefixCommandMembers(String body, String prefix) {
        String[] cmdParams = body.substring(prefix.length()).split(";");
        Set<String> members = new HashSet<>();
        for (String param : cmdParams) {
            if (param.startsWith(prefix))
                members.add(param.substring(prefix.length()));
        }
        return (members.size() > 0) ?
            members.toArray(new String[members.size()]) : null;
    }

    public static String[] getAddCommandMembers(String body) {
        return getPrefixCommandMembers(body, "add:");
    }

    public static String[] getRemoveCommandMembers(String body) {
        return getPrefixCommandMembers(body, "remove:");
    }

    /**
     * @deprecated This should be discouraged. Copy the body content from the command message instead.
     */
    @Deprecated
    public static String getTextContent(Context context, String bodyContent) {
        if (COMMAND_CREATE.equals(bodyContent)) {
            // TODO i18n
            return "Group created";
        }
        else if (COMMAND_PART.equals(bodyContent)) {
            // TODO i18n
            return "A user left the group";
        }

        return "(Unknown group command)";
    }

    public static List<GroupExtension.Member> membersFromJIDs(String[] members) {
        List<GroupExtension.Member> list = new ArrayList<>(members.length);
        for (String m : members)
            list.add(new GroupExtension.Member(m, GroupExtension.Member.Operation.NONE));
        return list;
    }

    /** Returns true if the given cursor is a group command message. */
    public static boolean isCursor(Cursor cursor) {
        return MIME_TYPE.equals(cursor.getString(CompositeMessage.COLUMN_BODY_MIME));
    }

}

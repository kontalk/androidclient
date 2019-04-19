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

package org.kontalk.message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

import android.content.Context;
import android.database.Cursor;

import org.kontalk.R;
import org.kontalk.client.GroupExtension;


/**
 * Component for group commands.
 * @author Daniele Ricci
 */
public class GroupCommandComponent extends MessageComponent<GroupExtension> {

    /** MIME type for command messages. */
    public static final String MIME_TYPE = "application/x-kontalk-group";

    // create group
    public static final String COMMAND_CREATE = "create";
    // leave group
    public static final String COMMAND_PART = "part";
    // set subject
    public static final String COMMAND_SUBJECT = "subject";
    // add members
    public static final String COMMAND_ADD = "add";
    // remove members
    public static final String COMMAND_REMOVE = "remove";

    private final String mFrom;

    public GroupCommandComponent(GroupExtension content, String from) {
        super(content, 0, false, 0);
        mFrom = from;
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
        return mContent.getType() == GroupExtension.Type.SET &&
            mContent.getMembers().size() > 0;
    }

    public boolean isSetSubjectCommand() {
        return mContent.getType() == GroupExtension.Type.SET &&
            mContent.getMembers().size() == 0;
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
            output.add(mContent.getOwner().toString());

        for (int i = 0; i < members.size(); i++) {
            GroupExtension.Member user = members.get(i);
            // exclude own JID from the list
            if (user.operation == operation)
                output.add(user.jid.toString());
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
        else if (isSetSubjectCommand()) {
            return getSetSubjectCommandBodyContent(mContent.getSubject());
        }

        // TODO
        throw new UnsupportedOperationException("Unsupported group command");
    }

    public static String getCreateBodyContent(String[] members) {
        StringBuilder out = new StringBuilder(COMMAND_CREATE).append(":");
        for (String m : members)
            out.append(m).append(";");
        return out.toString();
    }

    public static String getSetSubjectCommandBodyContent(String subject) {
        return COMMAND_SUBJECT + ":" + subject;
    }

    public static String getLeaveCommandBodyContent() {
        return COMMAND_PART;
    }

    public static String getAddMembersBodyContent(String[] members) {
        String prefix = GroupCommandComponent.COMMAND_ADD + ":";
        StringBuilder out = new StringBuilder();
        for (String m : members)
            out.append(prefix).append(m).append(";");
        return out.toString();
    }

    public static String getRemoveMembersBodyContent(String[] members) {
        String prefix = GroupCommandComponent.COMMAND_REMOVE + ":";
        StringBuilder out = new StringBuilder();
        for (String m : members)
            out.append(prefix).append(m).append(";");
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
        String[] cmdParams = body.split(";");
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

    public static String getSubjectCommand(String body) {
        String prefix = GroupCommandComponent.COMMAND_SUBJECT + ":";
        if (body.startsWith(prefix)) {
            return body.substring(prefix.length());
        }
        return null;
    }

    public static String getTextContent(Context context, String bodyContent, boolean incoming) {
        if (bodyContent.startsWith(COMMAND_CREATE)) {
            return context.getString(R.string.group_command_text_create);
        }
        else if (bodyContent.startsWith(COMMAND_PART)) {
            return context.getString(incoming ?
                R.string.group_command_text_part : R.string.group_command_text_part_self);
        }
        else if (bodyContent.startsWith(COMMAND_SUBJECT)) {
            return context.getString(R.string.group_command_text_subject);
        }
        else if (bodyContent.contains(COMMAND_ADD) || bodyContent.contains(COMMAND_REMOVE)) {
            return context.getString(R.string.group_command_text_add_remove);
        }

        // this shouldn't happen
        throw new UnsupportedOperationException("Unknown group command: " + bodyContent);
    }

    public static List<GroupExtension.Member> membersFromJIDs(String[] members) {
        return membersFromJIDs(members, null, null);
    }

    public static List<GroupExtension.Member> membersFromJIDs(String[] members, String[] added, String[] removed) {
        List<GroupExtension.Member> list = new ArrayList<>(
            (members != null ? members.length : 0) +
            (added != null ? added.length : 0) +
            (removed != null ? removed.length : 0));
        if (members != null) {
            for (String m : members) {
                Jid jid = JidCreate.fromOrThrowUnchecked(m);
                list.add(new GroupExtension.Member(jid, GroupExtension.Member.Operation.NONE));
            }
        }
        if (added != null) {
            for (String m : added) {
                Jid jid = JidCreate.fromOrThrowUnchecked(m);
                list.add(new GroupExtension.Member(jid, GroupExtension.Member.Operation.ADD));
            }
        }
        if (removed != null) {
            for (String m : removed) {
                Jid jid = JidCreate.fromOrThrowUnchecked(m);
                list.add(new GroupExtension.Member(jid, GroupExtension.Member.Operation.REMOVE));
            }
        }
        return list;
    }

    /** Returns true if the given cursor is a group command message. */
    public static boolean isCursor(Cursor cursor) {
        return MIME_TYPE.equals(cursor.getString(CompositeMessage.COLUMN_BODY_MIME));
    }

}

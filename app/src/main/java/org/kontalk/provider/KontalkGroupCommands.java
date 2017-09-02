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

package org.kontalk.provider;

import org.jxmpp.util.XmppStringUtils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.kontalk.crypto.Coder;
import org.kontalk.message.GroupCommandComponent;


/**
 * Utility methods for Kontalk group chat commands stored as messages.
 * @author Daniele Ricci
 */
public class KontalkGroupCommands {

    private KontalkGroupCommands() {
    }

    public static String createGroupJid(String groupId, String groupOwner) {
        return XmppStringUtils.completeJidFrom(groupId, groupOwner);
    }

    public static Uri createGroup(Context context, long threadId,
        String groupJid, String[] members, String msgId, boolean encrypted) {
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, threadId);
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, groupJid);
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.getCreateBodyContent(members).getBytes());
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    public static Uri addGroupMembers(Context context, long threadId, String groupJid, String[] members, String msgId, boolean encrypted) {
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, threadId);
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, groupJid);
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.getAddMembersBodyContent(members).getBytes());
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    public static Uri removeGroupMembers(Context context, long threadId, String groupJid, String[] members, String msgId, boolean encrypted) {
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, threadId);
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, groupJid);
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.getRemoveMembersBodyContent(members).getBytes());
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    public static Uri setGroupSubject(Context context, long threadId, String groupJid, String subject, String msgId, boolean encrypted, boolean fakeSend) {
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, threadId);
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, groupJid);
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.getSetSubjectCommandBodyContent(subject).getBytes());
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, fakeSend ? MyMessages.Messages.STATUS_SENT : MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    public static Uri leaveGroup(Context context, long threadId, String groupJid, String msgId, boolean encrypted, boolean fakeSend) {
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, threadId);
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, groupJid);
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.getLeaveCommandBodyContent().getBytes());
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, fakeSend ? MyMessages.Messages.STATUS_SENT : MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return context.getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    /** Returns true if the create group command for the given thread has been sent. */
    public static boolean isGroupCreatedSent(Context context, long threadId) {
        Cursor c = context.getContentResolver().query(MyMessages.Messages.CONTENT_URI,
            new String[] { MyMessages.Messages._ID },
            MyMessages.Messages.THREAD_ID + "=" + threadId + " AND " +
                MyMessages.Messages.BODY_MIME + "=? AND " +
                "(" + MyMessages.Messages.BODY_CONTENT + " LIKE ? OR " +
                MyMessages.Messages.BODY_CONTENT + " LIKE ?) AND " +
                MyMessages.Messages.STATUS + " IN (" +
                    MyMessages.Messages.STATUS_SENT + ", " + MyMessages.Messages.STATUS_RECEIVED + "," +
                    MyMessages.Messages.STATUS_INCOMING + ", " + MyMessages.Messages.STATUS_CONFIRMED + ")",
            new String[] {
                GroupCommandComponent.MIME_TYPE,
                GroupCommandComponent.COMMAND_CREATE + ":%",
                GroupCommandComponent.COMMAND_ADD + ":%"
            },
            null);
        try {
            return c.moveToNext();
        }
        finally {
            c.close();
        }
    }
}

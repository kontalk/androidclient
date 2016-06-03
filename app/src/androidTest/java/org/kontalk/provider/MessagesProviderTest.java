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

package org.kontalk.provider;

import org.jivesoftware.smack.util.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jxmpp.util.XmppStringUtils;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.MoreAsserts;
import android.test.ProviderTestCase2;

import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.util.MessageUtils;


@RunWith(AndroidJUnit4.class)
public class MessagesProviderTest extends ProviderTestCase2<MessagesProvider> {

    private static final String TEST_USERID = XmppStringUtils
        .completeJidFrom(MessageUtils.sha1("+15555215554"), "prime.kontalk.net");

    public MessagesProviderTest() {
        super(MessagesProvider.class, MessagesProvider.AUTHORITY);
    }

    @Before
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getTargetContext());
        super.setUp();
    }

    @Test
    public void testSetup() throws Exception {
        assertQuery(Messages.CONTENT_URI);
        assertQuery(Threads.CONTENT_URI);
    }

    @Test
    public void testInsertMessages() throws Exception {
        String msgId = MessageUtils.messageId();
        Uri msg = MessagesProviderUtils.newOutgoingMessage(getMockContext(),
            msgId, TEST_USERID, "Test message for you", true);
        assertNotNull(msg);
        assertQueryValues(msg,
            Messages.MESSAGE_ID, msgId,
            Messages.BODY_CONTENT, "Test message for you",
            Messages.BODY_MIME, "text/plain",
            Messages.DIRECTION, String.valueOf(Messages.DIRECTION_OUT));
    }

    @Test
    public void testDeleteMessage() throws Exception {
        String msgId = MessageUtils.messageId();
        Uri msg = MessagesProviderUtils.newOutgoingMessage(getMockContext(),
            msgId, TEST_USERID, "Test message for you", true);
        assertNotNull(msg);
        MessagesProviderUtils.deleteMessage(getMockContext(), ContentUris.parseId(msg));
        assertQueryCount(msg, 0);
    }

    @Test
    public void testDeleteThread() throws Exception {
        String msgId = MessageUtils.messageId();
        Uri msg = MessagesProviderUtils.newOutgoingMessage(getMockContext(),
            msgId, TEST_USERID, "Test message for you", true);
        assertNotNull(msg);
        long threadId = MessagesProviderUtils.getThreadByMessage(getMockContext(), msg);
        assertTrue(threadId > 0);
        MessagesProviderUtils.deleteThread(getMockContext(), threadId, false);
        assertQueryCount(msg, 0);
        assertQueryCount(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), 0);
    }

    @Test
    public void testCreateGroup() throws Exception {
        String groupId = StringUtils.randomString(20);
        String groupOwner = TEST_USERID;
        String groupJid = KontalkGroupCommands.createGroupJid(groupId, groupOwner);
        String[] members = {
            "alice@prime.kontalk.net",
            "bob@prime.kontalk.net",
            "charlie@prime.kontalk.net",
        };
        long threadId = MessagesProviderUtils.createGroupThread(getMockContext(), groupJid, null,
            members, "");
        assertTrue(threadId > 0);
        assertQueryCount(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), 1);

        assertQueryValues(Groups.getUri(groupJid),
            Groups.THREAD_ID, String.valueOf(threadId));

        String[] actualMembers = MessagesProviderUtils.getGroupMembers(getMockContext(), groupJid);
        MoreAsserts.assertEquals(members, actualMembers);
    }

    private void assertQuery(Uri uri) throws Exception {
        Cursor c = getMockContentResolver().query(uri, null, null, null, null);
        assertNotNull(c);
        c.close();
    }

    private void assertQueryCount(Uri uri, int count) throws Exception {
        Cursor c = getMockContentResolver().query(uri, null, null, null, null);
        assertNotNull(c);
        assertEquals(count, c.getCount());
        c.close();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void assertQueryValues(Uri uri, String... columnsExpected) throws Exception {
        String[] columns = new String[columnsExpected.length / 2];
        for (int i = 0; i < columns.length; i++)
            columns[i] = columnsExpected[i*2];

        Cursor c = getMockContentResolver().query(uri, columns, null, null, null);
        assertNotNull(c);
        assertTrue(c.moveToFirst());

        for (int i = 0; i < columns.length; i++) {
            String expected = columnsExpected[i*2+1];
            String actual;
            if (c.getType(i) == Cursor.FIELD_TYPE_BLOB) {
                actual = new String(c.getBlob(i));
            }
            else {
                actual = c.getString(i);
            }
            assertEquals(expected, actual);
        }

        c.close();
    }

}

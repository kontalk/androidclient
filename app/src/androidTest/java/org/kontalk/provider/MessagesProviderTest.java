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

package org.kontalk.provider;

import org.jivesoftware.smack.util.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jxmpp.util.XmppStringUtils;

import android.annotation.TargetApi;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.test.ProviderTestCase2;

import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.XMPPUtils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContainingInAnyOrder;


@RunWith(AndroidJUnit4.class)
public class MessagesProviderTest extends ProviderTestCase2<MessagesProvider> {

    private static final String TEST_USERID = XmppStringUtils
        .completeJidFrom(XMPPUtils.createLocalpart("+15555215554"), "prime.kontalk.net");

    public MessagesProviderTest() {
        super(MessagesProvider.class, MessagesProvider.AUTHORITY);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSetup() {
        assertQuery(Messages.CONTENT_URI);
        assertQuery(Threads.CONTENT_URI);
    }

    @Test
    public void testInsertMessages() {
        String msgId = MessageUtils.messageId();
        Uri msg = MessagesProviderClient.newOutgoingMessage(getMockContext(),
            msgId, TEST_USERID, "Test message for you", true, 0);
        assertNotNull(msg);
        assertQueryValues(msg,
            Messages.MESSAGE_ID, msgId,
            Messages.BODY_CONTENT, "Test message for you",
            Messages.BODY_MIME, "text/plain",
            Messages.DIRECTION, String.valueOf(Messages.DIRECTION_OUT));
    }

    @Test
    public void testDeleteMessage() {
        String msgId = MessageUtils.messageId();
        Uri msg = MessagesProviderClient.newOutgoingMessage(getMockContext(),
            msgId, TEST_USERID, "Test message for you", true, 0);
        assertNotNull(msg);
        MessagesProviderClient.deleteMessage(getMockContext(), ContentUris.parseId(msg));
        assertQueryCount(msg, 0);
    }

    @Test
    public void testDeleteThread() {
        String msgId = MessageUtils.messageId();
        Uri msg = MessagesProviderClient.newOutgoingMessage(getMockContext(),
            msgId, TEST_USERID, "Test message for you", true, 0);
        assertNotNull(msg);
        long threadId = MessagesProviderClient.getThreadByMessage(getMockContext(), msg);
        assertTrue(threadId > 0);
        MessagesProviderClient.deleteThread(getMockContext(), threadId, false);
        assertQueryCount(msg, 0);
        assertQueryCount(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), 0);
    }

    @Test
    public void testCreateGroup() {
        String groupId = StringUtils.randomString(20);
        String groupOwner = TEST_USERID;
        String groupJid = KontalkGroupCommands.createGroupJid(groupId, groupOwner);
        String[] members = {
            "alice@prime.kontalk.net",
            "bob@prime.kontalk.net",
            "charlie@prime.kontalk.net",
        };
        long threadId = MessagesProviderClient.createGroupThread(getMockContext(), groupJid, null,
            members, "");
        assertTrue(threadId > 0);
        assertQueryCount(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), 1);

        assertQueryValues(Groups.getUri(groupJid),
            Groups.THREAD_ID, String.valueOf(threadId));

        String[] actualMembers = MessagesProviderClient.getGroupMembers(getMockContext(), groupJid, 0);
        assertThat(actualMembers, arrayContainingInAnyOrder(members));
    }

    @Test
    public void testGroupAddRemove() {
        String groupId = StringUtils.randomString(20);
        String groupOwner = TEST_USERID;
        String groupJid = KontalkGroupCommands.createGroupJid(groupId, groupOwner);
        String[] members = {
            "alice@prime.kontalk.net",
            "bob@prime.kontalk.net",
        };
        long threadId = MessagesProviderClient.createGroupThread(getMockContext(), groupJid, null,
            members, "");
        assertTrue(threadId > 0);

        // add a user now
        MessagesProviderClient.addGroupMembers(getMockContext(), groupJid,
            new String[] { "charlie@prime.kontalk.net" }, true);

        // user list should return the same list as per create message
        String[] actualMembers = MessagesProviderClient.getGroupMembers(getMockContext(), groupJid, 0);
        assertThat(actualMembers, arrayContainingInAnyOrder(members));

        // clear pending flag
        getMockContext().getContentResolver().update(Groups
            .getMembersUri(groupJid).buildUpon()
            .appendPath("charlie@prime.kontalk.net")
            .appendQueryParameter(Messages.CLEAR_PENDING, String.valueOf(Groups.MEMBER_PENDING_ADDED))
            .build(), null, null, null);

        // user list should return charlie too now
        actualMembers = MessagesProviderClient.getGroupMembers(getMockContext(), groupJid, 0);
        members = SystemUtils.concatenate(members, "charlie@prime.kontalk.net");
        assertThat(actualMembers, arrayContainingInAnyOrder(members));
    }

    /** Tries to reproduce issue #761. */
    @Test
    public void testEmptyPeer() {
        // from MessagingNotification
        String query = MyMessages.Threads.NEW + " <> 0 AND " +
            MyMessages.Threads.DIRECTION + " = " + Messages.DIRECTION_IN +
            " AND " + MyMessages.Threads.PEER + " <> ? AND " +
            Groups.GROUP_JID + " <> ?";
        String[] args = { "", "" };
        Cursor c = getMockContentResolver().query(Threads.CONTENT_URI, null, query, args, null);
        assertNotNull(c);
        c.close();
        args = new String[] { "   ", "       " };
        c = getMockContentResolver().query(Threads.CONTENT_URI, null, query, args, null);
        assertNotNull(c);
        c.close();
    }

    private void assertQuery(Uri uri) {
        Cursor c = getMockContentResolver().query(uri, null, null, null, null);
        assertNotNull(c);
        c.close();
    }

    private void assertQueryCount(Uri uri, int count) {
        Cursor c = getMockContentResolver().query(uri, null, null, null, null);
        assertNotNull(c);
        assertEquals(count, c.getCount());
        c.close();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void assertQueryValues(Uri uri, String... columnsExpected) {
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

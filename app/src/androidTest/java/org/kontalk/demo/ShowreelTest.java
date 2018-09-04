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

package org.kontalk.demo;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.TestServerTest;
import org.kontalk.TestUtils;
import org.kontalk.crypto.Coder;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyUsers;
import org.kontalk.ui.ConversationsActivity;
import org.kontalk.util.MessageUtils;

import static org.junit.Assert.assertNotNull;


/**
 * An integration test to produce random conversations and other fancy things.
 * Device must be already filled with contacts. This test will somewhat ruin the
 * local database, so use with caution.
 * <ul>
 * <li>Mark all contacts as registered</li>
 * <li>Take up to NUM_USERS users and generate random conversations</li>
 * <li>Manual roster write to see all users in a random state</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ShowreelTest extends TestServerTest {

    private static final int NUM_USERS = 20;
    private static final int NUM_MESSAGES = 50;

    private static final int[] TEST_IN_STATUS = {
        //MyMessages.Messages.STATUS_INCOMING,
        MyMessages.Messages.STATUS_CONFIRMED,
    };

    private static final int[] TEST_OUT_STATUS = {
        //MyMessages.Messages.STATUS_SENDING,
        //MyMessages.Messages.STATUS_SENT,
        MyMessages.Messages.STATUS_RECEIVED,
    };

    private static final String[] TEST_MESSAGES = {
        "Suscipit orci interdum id",
        "Litora hendrerit tincidunt neque amet tempus",
        "Laoreet habitant",
        "Rhoncus netus suscipit",
        "Feugiat hac lectus rhoncus purus placerat eu varius",
        "Justo mi ultricies pulvinar nulla",
        "Lobortis varius vel per ante",
        "Interdum dictumst cras metus cursus eleifend",
        "Etiam nulla metus sem laoreet facilisis potenti posuere",
        "Condimentum elementum habitant",
        "Elit eget quisque ullamcorper porttitor nullam quam a",
        "Volutpat conubia nam sodales facilisis blandit",
        "Fringilla duis volutpat",
        "Varius pharetra lacus",
        "Elit",
        "Dolor",
        "Fringilla quisque",
        "Commodo malesuada auctor nisl consequat",
        "Non conubia purus pretium a",
        "Habitant suspendisse urna sit",
        "Senectus habitant est",
        "Nisi sodales ipsum",
        "Fames ad quisque taciti viverra etiam risus",
        "Semper",
        "Urna pharetra donec habitant quisque fames",
        "Feugiat augue praesent placerat tincidunt quis praesent curabitur",
        "Sit per pharetra",
        "Netus posuere velit consequat semper luctus sem sed",
        "Platea integer ultricies nunc",
        "Rutrum aliquam pharetra aenean lorem",
        "Eleifend convallis phasellus aenean magna",
        "Pellentesque duis",
        "Et",
        "Nam mollis urna commodo eros",
        "Arcu felis fermentum suscipit tempus nisl tellus bibendum",
        "Tortor nisl taciti sem donec",
        "Viverra posuere risus ac phasellus feugiat ut",
        "Donec a tempor diam sed tempus sociosqu",
        "Aptent inceptos pharetra primis",
        "Lectus maecenas congue accumsan",
        "Senectus habitant est",
        "Nisi sodales ipsum",
        "Fames ad quisque taciti viverra etiam risus",
        "Semper",
        "Urna pharetra donec habitant quisque fames",
        "Feugiat augue praesent placerat tincidunt quis praesent curabitur",
        "Sit per pharetra",
        "Netus posuere velit consequat semper luctus sem sed",
        "Platea integer ultricies nunc",
        "Rutrum aliquam pharetra aenean lorem",
        "Eleifend convallis phasellus aenean magna",
        "Pellentesque duis",
        "Et",
        "Nam mollis urna commodo eros",
        "Arcu felis fermentum suscipit tempus nisl tellus bibendum",
        "Tortor nisl taciti sem donec",
        "Viverra posuere risus ac phasellus feugiat ut",
        "Donec a tempor diam sed tempus sociosqu",
        "Aptent inceptos pharetra primis",
        "Lectus maecenas congue accumsan",
        "Sociosqu dolor",
        "Integer luctus ut himenaeos eget turpis",
        "Imperdiet proin tincidunt etiam orci taciti rhoncus magna",
        "Tortor habitasse ut etiam tempor",
        "Ante molestie neque",
        "Pellentesque himenaeos ac consequat justo",
        "Porta varius",
        "Arcu",
        "Condimentum eget pharetra",
        "Interdum sem sit nibh ultricies ac maecenas",
        "Sodales ipsum donec leo suspendisse",
        "Taciti facilisis luctus ad bibendum purus quis",
        "Habitasse primis felis tristique sagittis",
        "Consectetur ac interdum",
        "Mattis praesent",
        "Ante ultricies odio",
        "Gravida",
        "Aliquam phasellus sociosqu curabitur viverra odio senectus",
        "A cursus posuere",
        "Dictumst euismod sit condimentum blandit netus",
        "Amet",
        "Magna nullam aenean",
        "Pulvinar massa rhoncus",
        "Tempor fermentum ac massa interdum",
        "Vehicula commodo bibendum pharetra congue ullamcorper libero",
        "Justo faucibus imperdiet habitasse at condimentum",
        "Porta",
        "Morbi suspendisse nisi integer",
        "Mi in fusce per aliquam",
        "Congue ornare",
        "Molestie vehicula lectus",
        "Ultricies mollis eleifend lectus",
        "Accumsan aliquet bibendum ut",
        "Feugiat tristique neque convallis sagittis lorem lectus primis",
        "Condimentum vehicula lacinia venenatis risus proin fringilla per",
        "Commodo odio porttitor leo lorem aliquam",
        "Leo scelerisque",
        "Malesuada hac ullamcorper conubia tristique aptent aenean",
        "Arcu non fusce gravida lectus eu porttitor",
        "Nulla",
        "Suscipit orci interdum id",
        "Litora hendrerit tincidunt neque amet tempus",
        "Laoreet habitant",
        "Rhoncus netus suscipit",
        "Feugiat hac lectus rhoncus purus placerat eu varius",
        "Justo mi ultricies pulvinar nulla",
        "Lobortis varius vel per ante",
        "Interdum dictumst cras metus cursus eleifend",
        "Etiam nulla metus sem laoreet facilisis potenti posuere",
        "Condimentum elementum habitant",
        "Elit eget quisque ullamcorper porttitor nullam quam a",
        "Volutpat conubia nam sodales facilisis blandit",
        "Fringilla duis volutpat",
        "Varius pharetra lacus",
        "Elit",
        "Dolor",
        "Fringilla quisque",
        "Commodo malesuada auctor nisl consequat",
        "Non conubia purus pretium a",
        "Habitant suspendisse urna sit",
    };

    @Rule
    public ActivityTestRule<ConversationsActivity> mActivityTestRule =
        new ActivityTestRule<>(ConversationsActivity.class);

    @Before
    public void setUp() {
        TestUtils.skipIfDefaultAccountDoesNotExist();
        super.setUp();
    }

    private void markAllUsersRegistered() {
        ContentValues registered = new ContentValues(1);
        registered.put(MyUsers.Users.REGISTERED, 1);
        InstrumentationRegistry.getTargetContext()
            .getContentResolver().update(MyUsers.Users.CONTENT_URI, registered, null, null);
    }

    private void deleteAllThreads() {
        InstrumentationRegistry.getTargetContext()
            .getContentResolver().delete(MyMessages.Threads.Conversations.CONTENT_URI, null, null);
    }

    private long randomPastTime() {
        return System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7);
    }

    private Uri insertRandomMessage(String peer, long time) {
        byte[] content = TEST_MESSAGES[new Random()
            .nextInt(TEST_MESSAGES.length)].getBytes();
        int direction = new Random().nextInt(2);
        int status;
        if (direction == MyMessages.Messages.DIRECTION_IN) {
            status = TEST_IN_STATUS[new Random().nextInt(TEST_IN_STATUS.length)];
        }
        else {
            status = TEST_OUT_STATUS[new Random().nextInt(TEST_OUT_STATUS.length)];
        }

        // save to local storage
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.MESSAGE_ID, MessageUtils.messageId());
        values.put(MyMessages.Messages.PEER, peer);

        values.put(MyMessages.Messages.BODY_CONTENT, content);
        values.put(MyMessages.Messages.BODY_LENGTH, content.length);
        values.put(MyMessages.Messages.BODY_MIME, "text/plain");

        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, Coder.SECURITY_BASIC);

        values.put(MyMessages.Messages.SERVER_TIMESTAMP, time);

        values.put(MyMessages.Messages.STATUS, status);
        values.put(MyMessages.Messages.STATUS_CHANGED, time);
        // group commands don't get notifications
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.NEW, false);
        values.put(MyMessages.Messages.DIRECTION, direction);
        values.put(MyMessages.Messages.TIMESTAMP, time);

        return InstrumentationRegistry.getTargetContext()
            .getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    @Test
    public void fillData() {
        markAllUsersRegistered();
        deleteAllThreads();

        Cursor users = InstrumentationRegistry.getTargetContext()
            .getContentResolver().query(MyUsers.Users.CONTENT_URI,
                new String[]{MyUsers.Users.JID}, null, null, null);
        assertNotNull(users);
        int count = 0;
        while (users.moveToNext() && count < NUM_USERS) {
            long time = randomPastTime();
            for (int i = 0; i < NUM_MESSAGES; i++) {
                assertNotNull(insertRandomMessage(users.getString(0), time));
                time += TimeUnit.HOURS.toMillis(new Random().nextInt(6));
            }
            count++;
        }
        users.close();
    }

}

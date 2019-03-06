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

package org.kontalk.ui;

import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.Manifest;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.IdlingResource;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.Kontalk;
import org.kontalk.MockServerTest;
import org.kontalk.TestUtils;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class FakeAccountTest extends MockServerTest {
    private static final String TEST_USERNAME = "dev-5554";
    private static final String TEST_USERID = "+15555215554";

    @Rule
    public ActivityTestRule<ConversationsActivity> mActivityTestRule =
        new ActivityTestRule<>(ConversationsActivity.class);

    @Rule
    public GrantPermissionRule mPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA);

    @BeforeClass
    public static void setUpAccount() throws Exception {
        createFakeAccount(TEST_USERNAME, TEST_USERID);
        Kontalk.setServicesEnabled(InstrumentationRegistry.getTargetContext(), true);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.removeDefaultAccount();
        Collection<IdlingResource> idlingResourceList = IdlingRegistry.getInstance().getResources();
        for (IdlingResource resource : idlingResourceList) {
            IdlingRegistry.getInstance().unregister(resource);
        }
    }

    @Test
    public void fakeAccountTest() {
        // TODO find a way to mock the personal key or create an empty one

        try {
            Thread.sleep(30000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        // we should have an account now
        TestUtils.assertDefaultAccountExists();
    }

}

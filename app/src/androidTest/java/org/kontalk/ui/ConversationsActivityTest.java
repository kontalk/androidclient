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

package org.kontalk.ui;

import com.robotium.solo.Solo;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Instrumentation;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.authenticator.Authenticator;

import static org.junit.Assert.*;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class ConversationsActivityTest {

    @Rule
    public IntentsTestRule<ConversationsActivity> mActivityRule =
        new IntentsTestRule<>(ConversationsActivity.class, false, false);

    private Solo solo;

    @Before
    public void setUp() {
        solo = new Solo(InstrumentationRegistry.getInstrumentation());
    }

    @After
    public void tearDown() {
        solo.finishOpenedActivities();
    }

    @Test
    public void testOpenRegistration() throws Exception {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();

        // account was registered - skip the test
        if (Authenticator.getDefaultAccount(inst.getContext()) != null)
            return;

        // this must be done now otherwise solo won't see it
        mActivityRule.launchActivity(null);

        assertTrue(solo.waitForActivity(NumberValidation.class));
    }

}

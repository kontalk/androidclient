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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Activity;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.BuildConfig;
import org.kontalk.R;

import static org.junit.Assert.*;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class NumberValidationTest {

    @Rule
    public ActivityTestRule<NumberValidation> mActivityRule
        = new ActivityTestRule<>(NumberValidation.class);
    private Solo solo;

    @Before
    public void setUp() throws Exception {
        solo = new Solo(InstrumentationRegistry.getInstrumentation(),
            mActivityRule.getActivity());
    }

    @Test
    public void testCheckInput() throws Exception {
        Activity activity = mActivityRule.getActivity();
        solo.unlockScreen();
        // just click it
        solo.clickOnButton(0);
        assertTrue(solo.waitForDialogToOpen());
        assertTrue(solo.searchText(activity.getString(R.string.msg_no_name)));
        solo.clickOnText(activity.getString(android.R.string.ok));
        // clear everything
        solo.clearEditText(0);
        solo.clearEditText(1);
        solo.clickOnButton(0);
        assertTrue(solo.waitForDialogToOpen());
        assertTrue(solo.searchText(activity.getString(R.string.msg_no_name)));
        solo.clickOnText(activity.getString(android.R.string.ok));
        // this test will work on release build only
        if (!BuildConfig.DEBUG) {
            // fill the name
            solo.enterText(0, "Instrumentation Test");
            solo.clickOnButton(0);
            assertTrue(solo.waitForDialogToOpen());
            assertTrue(solo.searchText(activity.getString(R.string.msg_invalid_number)));
            solo.clickOnText(activity.getString(android.R.string.ok));
        }
    }

}

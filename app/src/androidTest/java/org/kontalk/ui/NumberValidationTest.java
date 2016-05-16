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

package org.kontalk.ui;

import com.robotium.solo.Solo;

import android.test.ActivityInstrumentationTestCase2;

import org.kontalk.BuildConfig;
import org.kontalk.R;


public class NumberValidationTest extends ActivityInstrumentationTestCase2<NumberValidation> {

    private Solo solo;

    public NumberValidationTest() {
        super(NumberValidation.class);
    }

    @Override
    public void setUp() throws Exception {
        solo = new Solo(getInstrumentation());
        getActivity();
    }

    @Override
    public void tearDown() throws Exception {
        solo.finishOpenedActivities();
    }

    public void testCheckInput() throws Exception {
        solo.unlockScreen();
        // just click it
        solo.clickOnButton(0);
        assertTrue(solo.waitForDialogToOpen());
        assertTrue(solo.searchText(getActivity().getString(R.string.msg_no_name)));
        solo.clickOnText(getActivity().getString(android.R.string.ok));
        // clear everything
        solo.clearEditText(0);
        solo.clearEditText(1);
        solo.clickOnButton(0);
        assertTrue(solo.waitForDialogToOpen());
        assertTrue(solo.searchText(getActivity().getString(R.string.msg_no_name)));
        solo.clickOnText(getActivity().getString(android.R.string.ok));
        // this test will work on release build only
        if (!BuildConfig.DEBUG) {
            // fill the name
            solo.enterText(0, "Instrumentation Test");
            solo.clickOnButton(0);
            assertTrue(solo.waitForDialogToOpen());
            assertTrue(solo.searchText(getActivity().getString(R.string.msg_invalid_number)));
            solo.clickOnText(getActivity().getString(android.R.string.ok));
        }
    }

}

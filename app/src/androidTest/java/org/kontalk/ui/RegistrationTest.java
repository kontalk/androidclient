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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.espresso.NoMatchingRootException;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.R;
import org.kontalk.TestServerTest;
import org.kontalk.TestUtils;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.matcher.RootMatchers.isDialog;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class RegistrationTest extends TestServerTest {
    private static final String TEST_PIN_CODE = "123456";
    private static final String TEST_USERNAME = "dev-5554";
    private static final String TEST_USERID = "5555215554";

    @Rule
    public ActivityTestRule<ConversationsActivity> mActivityTestRule =
        new ActivityTestRule<>(ConversationsActivity.class);

    @Before
    public void setUp() {
        TestUtils.skipIfDefaultAccountExists();
        super.setUp();
    }

    @Test
    public void registrationTest() {
        onView(withId(R.id.name))
            .perform(scrollTo(), replaceText(TEST_USERNAME), closeSoftKeyboard());
        onView(withId(R.id.phone_number))
            .perform(scrollTo(), replaceText(TEST_USERID), closeSoftKeyboard());

        onView(withId(R.id.button_validate))
            .perform(scrollTo(), click());

        try {
            onView(allOf(withId(R.id.md_buttonDefaultPositive), isDisplayed()))
                .inRoot(isDialog())
                .perform(click());
        }
        catch (NoMatchingRootException e) {
            // A-EHM... ignoring since the dialog might or might not appear
        }

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        onView(withId(R.id.validation_code))
            .perform(scrollTo(), replaceText(TEST_PIN_CODE), closeSoftKeyboard());

        onView(withId(R.id.send_button))
            .perform(scrollTo(), click());

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        // we should have an account now
        TestUtils.assertDefaultAccountExists();
    }

}

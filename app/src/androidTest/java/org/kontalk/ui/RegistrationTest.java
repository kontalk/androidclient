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
import java.util.concurrent.TimeUnit;

import org.greenrobot.eventbus.EventBus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.Manifest;
import android.support.test.espresso.IdlingPolicies;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.IdlingResource;
import android.support.test.espresso.NoMatchingRootException;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.EventIdlingResource;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.TestServerTest;
import org.kontalk.TestUtils;
import org.kontalk.service.registration.RegistrationService;
import org.kontalk.service.registration.event.AcceptTermsRequest;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.RootMatchers.isDialog;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class RegistrationTest extends TestServerTest {
    private static final String TEST_PIN_CODE = "123456";
    private static final String TEST_USERNAME = "dev-5554";
    private static final String TEST_USERID = "5555215554";

    private EventBus mBus = RegistrationService.bus();

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

    @Before
    public void setUp() throws Exception {
        TestUtils.removeDefaultAccount();
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
    public void registrationTest() {
        onView(withId(R.id.name))
            .perform(scrollTo(), replaceText(TEST_USERNAME), closeSoftKeyboard());
        onView(withId(R.id.phone_number))
            .perform(scrollTo(), replaceText(TEST_USERID), closeSoftKeyboard());

        onView(withId(R.id.button_validate))
            .perform(scrollTo(), click());

        // input confirmation dialog
        onView(withText(R.string.msg_register_confirm_number1))
            .inRoot(isDialog())
            .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.md_buttonDefaultPositive),
                     withText(android.R.string.ok),
                     isDisplayed()))
            .inRoot(isDialog())
            .perform(click());

        // register accept terms event
        IdlingPolicies.setIdlingResourceTimeout(5, TimeUnit.MINUTES);
        IdlingPolicies.setMasterPolicyTimeout(5, TimeUnit.MINUTES);
        IdlingRegistry.getInstance()
            .register(new EventIdlingResource<AcceptTermsRequest>
                (AcceptTermsRequest.class.getSimpleName(), mBus));

        // service terms dialog
        try {
            onView(withText(R.string.registration_accept_terms_title))
                .inRoot(isDialog())
                .check(matches(isDisplayed()));

            onView(allOf(withId(R.id.md_buttonDefaultPositive), withText(R.string.yes), isDisplayed()))
                .inRoot(isDialog())
                .perform(click());
        }
        catch (NoMatchingViewException e) {
            // A-EHM... ignoring since the dialog might or might not appear
            Log.w("TEST", "No matching service terms dialog");
        }

        // wait for connection
        // FIXME non-deterministic
        try {
            Thread.sleep(5000);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

        // force registration dialog
        try {
            onView(allOf(withId(R.id.md_buttonDefaultNeutral), withText(R.string.btn_device_overwrite), isDisplayed()))
                .inRoot(isDialog())
                .perform(click());
        }
        catch (NoMatchingRootException e) {
            // A-EHM... ignoring since the dialog might or might not appear
            Log.w("TEST", "No matching account override dialog");
        }

        // Added a sleep statement to match the app's execution delay.
        // The recommended way to handle such scenarios is to use Espresso idling resources:
        // https://google.github.io/android-testing-support-library/docs/espresso/idling-resource/index.html
        try {
            Thread.sleep(3000);
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

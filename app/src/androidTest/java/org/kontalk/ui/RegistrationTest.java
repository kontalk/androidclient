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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.Manifest;
import android.os.Handler;
import android.os.Looper;
import android.support.test.espresso.Espresso;
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
import org.kontalk.service.registration.event.AccountCreatedEvent;
import org.kontalk.service.registration.event.UserConflictError;
import org.kontalk.service.registration.event.VerificationRequestedEvent;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.replaceText;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.RootMatchers.isDialog;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isRoot;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static net.slideshare.mobile.test.util.OrientationChangeAction.orientationLandscape;
import static net.slideshare.mobile.test.util.OrientationChangeAction.orientationPortrait;
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

    @BeforeClass
    public static void setUpBeforeClass() throws InterruptedException {
        // always start with a clean slate
        TestUtils.removeDefaultAccount();
        RegistrationService.clearSavedState();
    }

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
    public void registrationTest() throws Exception {
        onView(withId(R.id.name))
            .perform(scrollTo(), replaceText(TEST_USERNAME), closeSoftKeyboard());
        onView(withId(R.id.phone_number))
            .perform(scrollTo(), replaceText(TEST_USERID), closeSoftKeyboard());

        onView(withId(R.id.button_validate))
            .perform(scrollTo(), click());

        // register accept terms event
        EventIdlingResource acceptTermsResource = TestUtils
            .registerEventIdlingResource(mBus, AcceptTermsRequest.class);

        // input confirmation dialog
        onView(withText(R.string.msg_register_confirm_number1))
            .inRoot(isDialog())
            .check(matches(isDisplayed()));
        onView(allOf(withId(R.id.md_buttonDefaultPositive),
                     withText(android.R.string.ok),
                     isDisplayed()))
            .inRoot(isDialog())
            .perform(click());

        // introduce a little anarchy
        onView(isRoot()).perform(orientationLandscape());

        acceptTermsResource.start();

        UserConflictOrVerificationRequestedIdlingResource registrationResource =
            TestUtils.registerIdlingResource(new UserConflictOrVerificationRequestedIdlingResource(mBus, 2000));

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

        registrationResource.start();

        TestUtils.unregisterIdlingResource(acceptTermsResource);

        // force registration dialog
        try {
            // we are going to try again
            registrationResource.reset();

            onView(allOf(withId(R.id.md_buttonDefaultNeutral), withText(R.string.btn_device_overwrite), isDisplayed()))
                .inRoot(isDialog())
                .perform(click());

            // introduce a little anarchy
            onView(isRoot()).perform(orientationPortrait());

            registrationResource.start();
        }
        catch (NoMatchingRootException e) {
            // A-EHM... ignoring since the dialog might or might not appear
            Log.w("TEST", "No matching account override dialog");
        }

        onView(withId(R.id.validation_code))
            .perform(scrollTo(), replaceText(TEST_PIN_CODE), closeSoftKeyboard());

        TestUtils.unregisterIdlingResource(registrationResource);

        EventIdlingResource accountResource = TestUtils
            .registerEventIdlingResource(mBus, AccountCreatedEvent.class);

        onView(withId(R.id.send_button))
            .perform(scrollTo(), click());

        accountResource.start();

        Espresso.onIdle();

        TestUtils.unregisterIdlingResource(accountResource);

        // we should have an account now
        TestUtils.assertDefaultAccountExists();
    }

    private static final class UserConflictOrVerificationRequestedIdlingResource
            implements IdlingResource {

        private final EventBus mBus;
        private final long mTimeoutMs;

        private final Handler mHandler;
        private final Runnable mTransitionToIdle;

        private boolean mRunning;
        private ResourceCallback mCallback;
        private boolean mEventReceived;

        UserConflictOrVerificationRequestedIdlingResource(EventBus bus, long timeoutMs) {
            mHandler = new Handler(Looper.getMainLooper());
            mBus = bus;
            mBus.register(this);
            mTimeoutMs = timeoutMs;

            mTransitionToIdle = new Runnable() {
                @Override
                public void run() {
                    mEventReceived = true;
                    if (mCallback != null) {
                        mCallback.onTransitionToIdle();
                    }
                }
            };
        }

        @Override
        public String getName() {
            return getClass().getSimpleName();
        }

        public void reset() {
            mRunning = false;
            mEventReceived = false;
            mHandler.removeCallbacks(mTransitionToIdle);
        }

        @Subscribe(threadMode = ThreadMode.ASYNC)
        public void onUserConflict(UserConflictError event) {
            Log.d("TEST", "got idling event: " + event);
            mHandler.postDelayed(mTransitionToIdle, mTimeoutMs);
        }

        @Subscribe(threadMode = ThreadMode.ASYNC)
        public void onVerificationRequested(VerificationRequestedEvent event) {
            Log.d("TEST", "got idling event: " + event);
            mHandler.postDelayed(mTransitionToIdle, mTimeoutMs);
            mBus.unregister(this);
        }

        @Override
        public boolean isIdleNow() {
            return !mRunning || mEventReceived;
        }

        @Override
        public void registerIdleTransitionCallback(ResourceCallback callback) {
            mCallback = callback;
        }

        public void start() {
            mRunning = true;
        }
    }

    /**
     * Tests the import key workflow when the server doesn't trust the key
     * because it was overruled by another one. User must verify the phone
     * number, but the key will be reused.
     */
    @Test
    public void importUntrustedKeyTest() {
        // TODO
        throw new AssertionError("Not implemented.");
    }

    /**
     * Tries to import a revoked key and should fail permanently.
     */
    @Test
    public void importRevokedKeyTest() {
        // TODO
        throw new AssertionError("Not implemented.");
    }

}

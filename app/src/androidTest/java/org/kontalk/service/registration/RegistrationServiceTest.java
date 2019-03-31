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

package org.kontalk.service.registration;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.DefaultAccountTest;
import org.kontalk.TestServerTest;
import org.kontalk.TestUtils;
import org.kontalk.service.registration.event.AcceptTermsRequest;
import org.kontalk.service.registration.event.AccountCreatedEvent;
import org.kontalk.service.registration.event.ChallengeError;
import org.kontalk.service.registration.event.ChallengeRequest;
import org.kontalk.service.registration.event.ImportKeyError;
import org.kontalk.service.registration.event.ImportKeyRequest;
import org.kontalk.service.registration.event.LoginTestEvent;
import org.kontalk.service.registration.event.TermsAcceptedEvent;
import org.kontalk.service.registration.event.VerificationError;
import org.kontalk.service.registration.event.VerificationRequest;
import org.kontalk.service.registration.event.VerificationRequestedEvent;

import static org.junit.Assert.assertTrue;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class RegistrationServiceTest extends TestServerTest {

    private EventBus mBus = RegistrationService.bus();

    @BeforeClass
    public static void setUpBeforeClass() throws InterruptedException {
        // always start with a clean slate
        TestUtils.removeDefaultAccount();
        RegistrationService.clearSavedState();
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        startService();
    }

    @After
    public void tearDown() throws Exception {
        stopService();
        // remove any created account
        TestUtils.removeDefaultAccount();
    }

    private void startService() {
        InstrumentationRegistry.getTargetContext()
            .startService(new Intent(InstrumentationRegistry.getTargetContext(),
                RegistrationService.class));
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException ignored) {
        }
    }

    private void stopService() {
        InstrumentationRegistry.getTargetContext()
            .stopService(new Intent(InstrumentationRegistry.getTargetContext(),
                RegistrationService.class));
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException ignored) {
        }
    }

    @Test
    public void requestVerificationTest() throws Exception {
        RequestVerificationTestListener listener = new RequestVerificationTestListener();
        mBus.register(listener);

        mBus.post(new VerificationRequest("+15555215554", "Device-5554",
            mTestServerProvider, true, RegistrationService.BRAND_IMAGE_LARGE));

        listener.waitAndReset(10, TimeUnit.SECONDS, 1);

        if (listener.verificationError != null)
            throw listener.verificationError;

        assertTrue(listener.acceptTermsEvent);
        assertTrue(listener.verificationRequestedEvent);

        // send challenge
        mBus.post(new ChallengeRequest("123456"));

        listener.waitAndReset(10, TimeUnit.SECONDS, 1);

        mBus.unregister(listener);

        if (listener.challengeError != null)
            throw listener.challengeError;

        assertTrue(listener.accountCreated);

        // we should have an account now
        TestUtils.assertDefaultAccountExists();
    }

    @Test
    public void saveAndResumeRequestVerificationTest() throws Exception {
        RequestVerificationTestListener listener = new RequestVerificationTestListener();
        mBus.register(listener);

        mBus.post(new VerificationRequest("+15555215554", "Device-5554",
            mTestServerProvider, true, RegistrationService.BRAND_IMAGE_LARGE));

        // a VerificationRequested will also be triggered, so count must be 2
        listener.waitAndReset(600, TimeUnit.SECONDS, 2);

        if (listener.verificationError != null)
            throw listener.verificationError;

        assertTrue(listener.acceptTermsEvent);
        assertTrue(listener.verificationRequestedEvent);

        stopService();

        startService();

        // send challenge
        mBus.post(new ChallengeRequest("123456"));

        listener.waitAndReset(600, TimeUnit.SECONDS, 1);

        mBus.unregister(listener);

        if (listener.challengeError != null)
            throw listener.challengeError;

        assertTrue(listener.accountCreated);

        // we should have an account now
        TestUtils.assertDefaultAccountExists();
    }

    /**
     * Tests registration workflow using secret data passed on by another device.
     */
    @Test
    public void registerDeviceTest() {
        // TODO
        throw new AssertionError("Not implemented.");
    }

    @Test
    public void importKeyTest() throws Exception {
        RequestVerificationTestListener listener = new RequestVerificationTestListener();
        mBus.register(listener);

        InputStream keyPackInput = InstrumentationRegistry.getContext().getAssets().open("keys/kontalk-keys.zip");

        mBus.post(new ImportKeyRequest(mTestServerProvider.next(),
            keyPackInput, DefaultAccountTest.TEST_PASSPHRASE,
            true, RegistrationService.BRAND_IMAGE_LARGE));

        listener.waitAndReset(10, TimeUnit.SECONDS, 1);

        keyPackInput.close();

        if (listener.importKeyError != null)
            throw listener.importKeyError;

        assertTrue(listener.acceptTermsEvent);

        listener.waitAndReset(10, TimeUnit.SECONDS, 1);

        // either the account was created or a verification workflow has started
        assertTrue(listener.verificationRequestedEvent || listener.accountCreated);

        if (listener.verificationRequestedEvent) {
            // send challenge
            mBus.post(new ChallengeRequest("123456"));

            listener.waitAndReset(10, TimeUnit.SECONDS, 1);

            if (listener.challengeError != null)
                throw listener.challengeError;
        }
        else {
            // this shouldn't happen since we set fallbackVerification to true
            if (listener.loginTestError != null)
                throw listener.loginTestError;
        }

        mBus.unregister(listener);

        assertTrue(listener.accountCreated);

        // we should have an account now
        TestUtils.assertDefaultAccountExists();
    }

    @Test
    public void saveAndResumeImportKeyTest() throws Exception {
        // for this test to work we need to first create a new account
        // so the fallback registration procedure will trigger
        requestVerificationTest();
        tearDown();
        setUp();

        RequestVerificationTestListener listener = new RequestVerificationTestListener();
        mBus.register(listener);

        InputStream keyPackInput = InstrumentationRegistry.getContext().getAssets().open("keys/kontalk-keys.zip");

        mBus.post(new ImportKeyRequest(mTestServerProvider.next(),
            keyPackInput, DefaultAccountTest.TEST_PASSPHRASE,
            true, RegistrationService.BRAND_IMAGE_LARGE));

        // wait for error or verification requested
        listener.waitAndReset(10, TimeUnit.SECONDS, 1);

        keyPackInput.close();

        if (listener.importKeyError != null)
            throw listener.importKeyError;

        assertTrue(listener.acceptTermsEvent);
        assertTrue(listener.verificationRequestedEvent);

        stopService();

        listener.verificationRequestedEvent = false;

        startService();

        // wait for verification requested
        listener.waitAndReset(10, TimeUnit.SECONDS, 1);

        assertTrue(listener.verificationRequestedEvent);

        // send challenge
        mBus.post(new ChallengeRequest("123456"));

        listener.waitAndReset(20, TimeUnit.SECONDS, 1);

        if (listener.challengeError != null)
            throw listener.challengeError;

        mBus.unregister(listener);

        assertTrue(listener.accountCreated);

        // we should have an account now
        TestUtils.assertDefaultAccountExists();
    }

    public class RequestVerificationTestListener {
        public CountDownLatch lock = new CountDownLatch(1);

        public boolean acceptTermsEvent;
        public boolean verificationRequestedEvent;
        public boolean accountCreated;

        public Exception verificationError;
        public Exception challengeError;
        public Exception importKeyError;
        public Exception loginTestError;

        public void waitAndReset(long timeout, TimeUnit unit, int count) throws InterruptedException {
            lock.await(timeout, unit);
            reset(count);
        }

        public void reset(int count) {
            lock = new CountDownLatch(count);
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onAcceptTermsRequested(AcceptTermsRequest request) {
            acceptTermsEvent = true;
            mBus.post(new TermsAcceptedEvent());
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onVerificationRequested(VerificationRequestedEvent event) {
            this.verificationRequestedEvent = true;
            lock.countDown();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onVerificationError(VerificationError error) {
            this.verificationError = error.exception;
            lock.countDown();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onChallengeError(ChallengeError error) {
            this.challengeError = error.exception;
            lock.countDown();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onImportKeyError(ImportKeyError error) {
            this.importKeyError = error.exception;
            lock.countDown();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onAccountCreated(AccountCreatedEvent event) {
            this.accountCreated = true;
            lock.countDown();
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onLoginTest(LoginTestEvent event) {
            this.loginTestError = event.exception;
            lock.countDown();
        }
    }

}

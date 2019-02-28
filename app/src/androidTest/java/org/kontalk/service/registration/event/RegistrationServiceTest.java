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

package org.kontalk.service.registration.event;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.TestServerTest;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.service.registration.RegistrationService;

import static org.junit.Assert.assertTrue;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class RegistrationServiceTest extends TestServerTest {

    private EventBus mBus = RegistrationService.bus();

    @BeforeClass
    public static void setUpBeforeClass() throws InterruptedException {
        // always start with a clean slate
        final Object monitor = new Object();
        Authenticator.removeDefaultAccount(InstrumentationRegistry
            .getTargetContext(), new AccountManagerCallback<Boolean>() {
            @Override
            public void run(AccountManagerFuture<Boolean> future) {
                synchronized (monitor) {
                    monitor.notify();
                }
            }
        });
        synchronized (monitor) {
            monitor.wait(3000);
        }
    }

    @Before
    public void setUp() {
        super.setUp();
        startService();
    }

    @After
    public void tearDown() throws Exception {
        stopService();
        // remove any created account
        AccountManagerCallback<Boolean> callback = new AccountManagerCallback<Boolean>() {
            public void run(AccountManagerFuture<Boolean> future) {
                synchronized (RegistrationServiceTest.this) {
                    RegistrationServiceTest.this.notify();
                }
            }
        };
        Authenticator.removeDefaultAccount(InstrumentationRegistry.getTargetContext(), callback);
        synchronized (this) {
            wait(3000);
        }
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
            mTestServerProvider,
            //new EndpointServer.SingleServerProvider("prime.kontalk.net|10.0.2.2"),
            true, RegistrationService.BRAND_IMAGE_LARGE));

        synchronized (this) {
            wait(10000);
        }

        if (listener.verificationError != null)
            throw listener.verificationError;

        assertTrue(listener.acceptTermsEvent);
        assertTrue(listener.verificationRequestedEvent);

        // send challenge
        mBus.post(new ChallengeRequest("123456"));

        synchronized (this) {
            wait(100000);
        }
        mBus.unregister(listener);

        if (listener.challengeError != null)
            throw listener.challengeError;

        assertTrue(listener.accountCreated);
    }

    public class RequestVerificationTestListener {
        public boolean acceptTermsEvent;
        public boolean verificationRequestedEvent;
        public boolean accountCreated;

        private Exception verificationError;
        private Exception challengeError;

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onAcceptTermsRequested(AcceptTermsRequest request) {
            acceptTermsEvent = true;
            mBus.post(new TermsAcceptedEvent());
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onVerificationRequested(VerificationRequestedEvent event) {
            this.verificationRequestedEvent = true;
            synchronized (RegistrationServiceTest.this) {
                RegistrationServiceTest.this.notify();
            }
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onVerificationError(VerificationError error) {
            this.verificationError = error.exception;
            synchronized (RegistrationServiceTest.this) {
                RegistrationServiceTest.this.notify();
            }
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onChallengeError(ChallengeError error) {
            this.challengeError = error.exception;
            synchronized (RegistrationServiceTest.this) {
                RegistrationServiceTest.this.notify();
            }
        }

        @Subscribe(threadMode = ThreadMode.MAIN)
        public void onAccountCreated(AccountCreatedEvent event) {
            this.accountCreated = true;
            synchronized (RegistrationServiceTest.this) {
                RegistrationServiceTest.this.notify();
            }
        }
    }

    @Test
    @Ignore
    public void registerDeviceTest() {
        // TODO
    }

    @Test
    @Ignore
    public void importKeyTest() {
        // TODO
    }

    @Test
    public void saveAndResumeRequestVerificationTest() throws Exception {
        RequestVerificationTestListener listener = new RequestVerificationTestListener();
        mBus.register(listener);

        mBus.post(new VerificationRequest("+15555215554", "Device-5554",
            mTestServerProvider,
            //new EndpointServer.SingleServerProvider("prime.kontalk.net|10.0.2.2"),
            true, RegistrationService.BRAND_IMAGE_LARGE));

        synchronized (this) {
            wait(10000);
        }

        if (listener.verificationError != null)
            throw listener.verificationError;

        assertTrue(listener.acceptTermsEvent);
        assertTrue(listener.verificationRequestedEvent);

        stopService();

        startService();

        // send challenge
        mBus.post(new ChallengeRequest("123456"));

        synchronized (this) {
            wait(10000);
        }
        mBus.unregister(listener);

        if (listener.challengeError != null)
            throw listener.challengeError;

        assertTrue(listener.accountCreated);
    }
}

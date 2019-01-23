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
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.kontalk.TestServerTest;
import org.kontalk.service.registration.RegistrationService;

import static org.junit.Assert.*;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class RegistrationServiceTest extends TestServerTest {

    private EventBus mBus = RegistrationService.bus();

    @Before
    public void setUp() {
        super.setUp();
        InstrumentationRegistry.getTargetContext()
            .startService(new Intent(InstrumentationRegistry.getTargetContext(),
                RegistrationService.class));
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException ignored) {
        }
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getTargetContext()
            .stopService(new Intent(InstrumentationRegistry.getTargetContext(),
                RegistrationService.class));
    }

    @Test
    public void requestVerificationTest() throws InterruptedException {
        RequestVerificationTestListener listener = new RequestVerificationTestListener();
        mBus.register(listener);

        mBus.post(new VerificationRequest("+15555215554", "Device-5554",
            TEST_SERVER_PROVIDER, true, RegistrationService.BRAND_IMAGE_LARGE));

        synchronized (this) {
            wait(10000);
        }
        mBus.unregister(listener);

        assertTrue(listener.acceptTermsEvent);
        assertTrue(listener.verificationRequestedEvent);
    }

    public class RequestVerificationTestListener {
        public boolean acceptTermsEvent;
        public boolean verificationRequestedEvent;

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
            this.verificationRequestedEvent = true;
            synchronized (RegistrationServiceTest.this) {
                RegistrationServiceTest.this.notify();
            }
        }
    }

}

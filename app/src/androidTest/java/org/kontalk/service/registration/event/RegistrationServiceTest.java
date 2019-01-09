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


@RunWith(AndroidJUnit4.class)
@LargeTest
public class RegistrationServiceTest extends TestServerTest {

    @Before
    public void setUp() {
        super.setUp();
        InstrumentationRegistry.getTargetContext()
            .startService(new Intent(InstrumentationRegistry.getTargetContext(),
                RegistrationService.class));
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getTargetContext()
            .stopService(new Intent(InstrumentationRegistry.getTargetContext(),
                RegistrationService.class));
    }

    @Test
    public void requestVerificationTest() {
        // TODO VerificationRequest reception and reply
    }

}

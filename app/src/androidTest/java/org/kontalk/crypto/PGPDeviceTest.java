/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.crypto;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;

import static org.junit.Assert.assertNotNull;


@RunWith(AndroidJUnit4.class)
@FlakyTest
public class PGPDeviceTest {

    @Before
    public void setUp() {
        PGP.registerProvider();
    }

    /**
     * Some devices have problem running crypto stuff. So this test should be
     * run on as much devices as possible.
     */
    @Test
    public void testCreate() throws Exception {
        PGP.PGPDecryptedKeyPairRing ring = PGP.create(new Date());
        assertNotNull(ring);
        assertNotNull(ring.authKey);
        assertNotNull(ring.encryptKey);
        assertNotNull(ring.signKey);
    }

}

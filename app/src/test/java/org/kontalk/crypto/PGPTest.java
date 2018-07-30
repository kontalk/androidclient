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

package org.kontalk.crypto;

import org.junit.Test;

import static org.junit.Assert.*;


public class PGPTest {

    @Test
    public void testFormatFingerprint() throws Exception {
        String fpr = PGP.formatFingerprint("F28947756EB27311E86F6309274BE2A3BD56E37A");
        assertEquals("F289 4775 6EB2 7311 E86F  6309 274B E2A3 BD56 E37A", fpr);
    }
}

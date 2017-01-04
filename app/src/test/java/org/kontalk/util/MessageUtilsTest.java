/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.util;

import org.junit.Test;

import static org.junit.Assert.*;


public class MessageUtilsTest {

    @Test
    public void testBytesToHex() throws Exception {
        byte[] input = new byte[]{0, 2, 39, 81, 21, 100, 127, 102, 100, 111, 8};
        String out = MessageUtils.bytesToHex(input);
        assertEquals("0002275115647f66646f08", out);
    }
}

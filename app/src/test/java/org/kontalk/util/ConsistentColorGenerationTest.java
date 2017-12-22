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


public class ConsistentColorGenerationTest {

    @Test
    public void testGetRGB() throws Exception {
        double[] rgb;

        rgb = ConsistentColorGeneration.getRGB("juliet@capulet.lit",
            ConsistentColorGeneration.CORRECTION_NONE);
        assertEquals(rgb[0], 0.337, 0.01);
        assertEquals(rgb[1], 1.000, 0.01);
        assertEquals(rgb[2], 0.000, 0.01);

        rgb = ConsistentColorGeneration.getRGB("Romeo",
            ConsistentColorGeneration.CORRECTION_NONE);
        assertEquals(rgb[0], 0.281, 0.01);
        assertEquals(rgb[1], 0.790, 0.01);
        assertEquals(rgb[2], 1.000, 0.01);
    }

}

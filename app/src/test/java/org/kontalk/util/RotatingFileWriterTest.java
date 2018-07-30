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

package org.kontalk.util;

import java.io.File;

import org.jivesoftware.smack.util.StringUtils;
import org.junit.Test;

import static org.junit.Assert.*;


public class RotatingFileWriterTest {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testRotate() throws Exception {
        int lineSepLen = System.getProperty("line.separator").length();
        File f = File.createTempFile("log", null);
        RotatingFileWriter w = new RotatingFileWriter(f, 3000, 2);
        w.println(StringUtils.randomString(512));
        w.println(StringUtils.randomString(512));
        w.println(StringUtils.randomString(512));
        w.println(StringUtils.randomString(512));
        w.flush();
        assertEquals((512*4)+(lineSepLen*4), f.length());
        w.println(StringUtils.randomString(512));
        w.flush();
        assertEquals((512*5)+(lineSepLen*5), f.length());
        w.println(StringUtils.randomString(512));
        // rotation threshold
        w.flush();
        assertEquals((512*4)+(lineSepLen*4), f.length());
        w.println(StringUtils.randomString(512));
        w.flush();
        assertEquals((512*5)+(lineSepLen*5), f.length());
        w.println(StringUtils.randomString(512));
        // rotation threshold
        w.flush();
        assertEquals((512*4)+(lineSepLen*4), f.length());
        w.close();
        f.delete();
    }

}

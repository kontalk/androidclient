/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.*;

import android.content.Context;
import android.os.Environment;


public class MediaStorage {
    public static final File MEDIA_ROOT = new File(Environment.getExternalStorageDirectory(), "Kontalk");

    /* instantiation not allowed */
    private MediaStorage() {}

    public static File writeInternalMedia(Context context, String filename, byte[] contents) throws IOException {
        File f = new File(context.getCacheDir(), filename);
        FileOutputStream fout = new FileOutputStream(f);
        fout.write(contents);
        fout.close();
        return f;
    }

    public static File writeMedia(String filename, InputStream source) throws IOException {
        MEDIA_ROOT.mkdirs();
        File f = new File(MEDIA_ROOT, filename);
        FileOutputStream fout = new FileOutputStream(f);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = source.read(buffer)) != -1)
            fout.write(buffer, 0, len);
        fout.close();
        return f;
    }

}

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

package org.kontalk.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Properties;

import android.content.res.AssetFileDescriptor;
import android.util.SparseBooleanArray;


/**
 * Put all utility methods related to data manipulation here.
 */
public class DataUtils {

    /**
     * Provides clone functionality for the {@link SparseBooleanArray}.
     * See https://code.google.com/p/android/issues/detail?id=39242
     */
    public static SparseBooleanArray cloneSparseBooleanArray(SparseBooleanArray array) {
        final SparseBooleanArray clone = new SparseBooleanArray();

        synchronized (array) {
            final int size = array.size();
            for (int i = 0; i < size; i++) {
                int key = array.keyAt(i);
                clone.put(key, array.get(key));
            }
        }

        return clone;
    }

    public static <T> T[] concatenate (T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen+bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    public static <T> T[] concatenate (T[] a, T b) {
        int aLen = a.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + 1);
        System.arraycopy(a, 0, c, 0, aLen);
        c[aLen] = b;

        return c;
    }

    public static <T> boolean contains(final T[] array, final T v) {
        for (final T e : array)
            if (e == v || v != null && v.equals(e))
                return true;

        return false;
    }

    /** Mainly for converting arrays of Jids to arrays of String :) */
    public static <T> String[] toString(final T[] array) {
        String[] out = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            out[i] = array[i] != null ? array[i].toString() : null;
        }
        return out;
    }

    /** Instead of importing the whole commons-io :) */
    public static long copy(final InputStream input, final OutputStream output) throws IOException {
        byte[] buffer = new byte[4096];
        long count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /** Closes the given closeable object, ignoring any errors. */
    public static void close(Closeable object) {
        if (object != null) {
            try {
                object.close();
            }
            catch (Exception ignored) {
            }
        }
    }

    public static String serializeProperties(Properties properties) {
        try {
            StringWriter writer = new StringWriter();
            properties.store(writer, null);
            return writer.toString();
        }
        catch (IOException e) {
            throw new AssertionError("this can't happen");
        }
    }

    public static Properties unserializeProperties(String data) {
        try {
            StringReader reader = new StringReader(data);
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        }
        catch (IOException e) {
            throw new AssertionError("this can't happen");
        }
    }

    /**
     * Closes the given stream, ignoring any errors.
     * This method can be safely deleted once we'll have min SDK set to 19,
     * because AssetFileDescriptor will implement Closeable.
     */
    public static void closeStream(AssetFileDescriptor stream) {
        if (stream != null) {
            try {
                stream.close();
            }
            catch (Exception ignored) {
            }
        }
    }
}

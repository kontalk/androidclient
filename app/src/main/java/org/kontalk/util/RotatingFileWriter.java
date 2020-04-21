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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Flushable;
import java.io.IOException;


/**
 * A simplified FileWriter capable of rotating a file after a given
 * amount of bytes have been written to the underlying file.
 * @author Daniele Ricci
 */
public class RotatingFileWriter implements Flushable, Closeable {

    private static final String LINE_SEPARATOR = System.getProperty("line.separator");

    /** Rotate at this amount of bytes. */
    private static final long ROTATE_AT = 1048576;
    /** How many old lines to delete when rotating. */
    private static final int DELETE_OLD = 100;

    private final long mRotateAt;
    private final long mDeleteOld;
    private final File mLogFile;
    private FileWriter mWriter;
    /** Caches the amount of bytes written in the current file. */
    private long mSize;

    public RotatingFileWriter(File logFile) throws IOException {
        this(logFile, ROTATE_AT, DELETE_OLD);
    }

    public RotatingFileWriter(File logFile, long rotateAt, int deleteOld) throws IOException {
        super();
        mLogFile = logFile;
        mWriter = new FileWriter(logFile, true);
        mSize = mLogFile.length();
        mRotateAt = rotateAt;
        mDeleteOld = deleteOld;
    }

    @Override
    public synchronized void flush() throws IOException {
        mWriter.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        mWriter.close();
        mWriter = null;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public synchronized void abort() throws IOException {
        close();
        mLogFile.delete();
    }

    private void write(String str) throws IOException {
        mWriter.write(str);
        mSize += str.length();
    }

    private void newLine() throws IOException {
        write(LINE_SEPARATOR);
    }

    private void print(String s) throws IOException {
        if (s == null) {
            s = "null";
        }
        write(s);
    }

    public synchronized void println(String x) throws IOException {
        print(x);
        newLine();
        checkRotate();
    }

    private void checkRotate() throws IOException {
        if (mSize >= mRotateAt) {
            mWriter.close();
            if (!rotate())
                throw new IOException("Unable to rotate log file");
            mWriter = new FileWriter(mLogFile, true);
        }
    }

    private boolean rotate() throws IOException {
        // rename the current file
        File oldFile = new File(mLogFile.toString() + ".old");
        File newFile = new File(mLogFile.toString());
        if (newFile.renameTo(oldFile)) {
            BufferedReader oldLog = null;
            FileWriter rotatedLog = null;
            try {
                // open the old file and start writing lines
                oldLog = new BufferedReader(new FileReader(oldFile));
                rotatedLog = new FileWriter(mLogFile.toString());
                long lineCount = 0;
                String line;
                while ((line = oldLog.readLine()) != null) {
                    lineCount++;
                    // skip up to DELETE_OLD lines, then start writing lines
                    if (lineCount > mDeleteOld) {
                        rotatedLog.write(line);
                        rotatedLog.write(LINE_SEPARATOR);
                    }
                }
            }
            finally {
                DataUtils.close(oldLog);
                DataUtils.close(rotatedLog);
                oldFile.delete();
            }

            mSize = mLogFile.length();
            return true;
        }
        else {
            // we couldn't rename the old log file
            // we just delete it to make space for a new one
            return mLogFile.delete();
        }
    }

}

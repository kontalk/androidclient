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

package org.kontalk;

import java.io.File;
import java.io.IOException;

import android.content.Context;

import org.kontalk.util.Preferences;
import org.kontalk.util.RotatingFileWriter;
import org.kontalk.util.SystemUtils;


/**
 * isLoggable-aware wrapper around {@link android.util.Log} plus some minor
 * enhancements such as formatted strings and dump to file.
 * @author Daniele Ricci
 */
public final class Log {

    private static final String LOG_FILENAME = "kontalk-android.log";

    private static RotatingFileWriter sLogFileWriter;
    private static File sLogFile;

    public static void init(Context context) {
        try {
            if (Preferences.isDebugLogEnabled(context)) {
                sLogFile = new File(context.getExternalCacheDir(), LOG_FILENAME);
                sLogFileWriter = new RotatingFileWriter(sLogFile);
            }
            else {
                if (sLogFileWriter != null) {
                    sLogFileWriter.abort();
                    sLogFileWriter = null;
                }
            }
        }
        catch (IOException e) {
            // TODO notify to user via Toast?
        }
    }

    public static File getLogFile() {
        return sLogFile;
    }

    public static boolean isDebug() {
        return BuildConfig.DEBUG || sLogFileWriter != null;
    }

    private static String buildLog(String tag, int level, String msg) {
        String strLevel;
        switch (level) {
            case android.util.Log.VERBOSE:
                strLevel = "V";
                break;
            case android.util.Log.DEBUG:
                strLevel = "D";
                break;
            case android.util.Log.INFO:
                strLevel = "I";
                break;
            case android.util.Log.WARN:
                strLevel = "W";
                break;
            case android.util.Log.ERROR:
                strLevel = "E";
                break;
            default:
                strLevel = "?";
                break;
        }
        return strLevel + "/" + tag + ": " + msg;
    }

    private static void log(String tag, int level, Throwable tr) {
        if (sLogFileWriter != null && tr != null) {
            log(tag, level, android.util.Log.getStackTraceString(tr));
        }
    }

    private static void log(String tag, int level, String msg) {
        if (sLogFileWriter != null) {
            try {
                sLogFileWriter.println(buildLog(tag, level, msg));
                sLogFileWriter.flush();
            }
            catch (IOException e) {
                // disable logging but keep the file
                SystemUtils.closeStream(sLogFileWriter);
                sLogFileWriter = null;
            }
        }
    }

    private static String makeTag(String tag) {
        // does not work even on Android > 7.0
        // return (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N && tag.length() > 23) ? tag.substring(0, 23) : tag;
        return tag.length() > 23 ? tag.substring(0, 23) : tag;
    }

    /**
     * Send a {@link android.util.Log#VERBOSE} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int v(String tag, String msg) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.VERBOSE)) {
            log(tag, android.util.Log.VERBOSE, msg);
            return android.util.Log.v(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#VERBOSE} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int v(String tag, String msg, Throwable tr) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.VERBOSE)) {
            log(tag, android.util.Log.VERBOSE, msg);
            log(tag, android.util.Log.VERBOSE, tr);
            return android.util.Log.v(_tag, msg, tr);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#VERBOSE} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param fmt The message you would like logged.
     * @param args Arguments for formatting the message.
     */
    public static int v(String tag, String fmt, Object... args) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.VERBOSE)) {
            String msg = String.format(fmt, args);
            log(tag, android.util.Log.VERBOSE, msg);
            return android.util.Log.v(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#DEBUG} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int d(String tag, String msg) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.DEBUG)) {
            log(tag, android.util.Log.DEBUG, msg);
            return android.util.Log.d(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#DEBUG} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int d(String tag, String msg, Throwable tr) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.DEBUG)) {
            log(tag, android.util.Log.DEBUG, msg);
            log(tag, android.util.Log.DEBUG, tr);
            return android.util.Log.d(_tag, msg, tr);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#DEBUG} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param fmt The message you would like logged.
     * @param args Arguments for formatting the message.
     */
    public static int d(String tag, String fmt, Object... args) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.DEBUG)) {
            String msg = String.format(fmt, args);
            log(tag, android.util.Log.DEBUG, msg);
            return android.util.Log.d(_tag, msg);
        }
        return 0;
    }

    /**
     * Send an {@link android.util.Log#INFO} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int i(String tag, String msg) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.INFO)) {
            log(tag, android.util.Log.INFO, msg);
            return android.util.Log.i(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#INFO} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int i(String tag, String msg, Throwable tr) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.INFO)) {
            log(tag, android.util.Log.INFO, msg);
            log(tag, android.util.Log.INFO, tr);
            return android.util.Log.i(_tag, msg, tr);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#INFO} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param fmt The message you would like logged.
     * @param args Arguments for formatting the message.
     */
    public static int i(String tag, String fmt, Object... args) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.INFO)) {
            String msg = String.format(fmt, args);
            log(tag, android.util.Log.INFO, msg);
            return android.util.Log.i(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#WARN} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int w(String tag, String msg) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.WARN)) {
            log(tag, android.util.Log.WARN, msg);
            return android.util.Log.w(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int w(String tag, String msg, Throwable tr) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.WARN)) {
            log(tag, android.util.Log.WARN, msg);
            log(tag, android.util.Log.WARN, tr);
            return android.util.Log.w(_tag, msg, tr);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#WARN} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param fmt The message you would like logged.
     * @param args Arguments for formatting the message.
     */
    public static int w(String tag, String fmt, Object... args) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.WARN)) {
            String msg = String.format(fmt, args);
            log(tag, android.util.Log.WARN, msg);
            return android.util.Log.w(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#WARN} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param tr An exception to log
     */
    public static int w(String tag, Throwable tr) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.WARN)) {
            log(tag, android.util.Log.WARN, tr);
            return android.util.Log.w(_tag, tr);
        }
        return 0;
    }

    /**
     * Send an {@link android.util.Log#ERROR} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static int e(String tag, String msg) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.ERROR)) {
            log(tag, android.util.Log.ERROR, msg);
            return android.util.Log.e(_tag, msg);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#ERROR} log message and log the exception.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr An exception to log
     */
    public static int e(String tag, String msg, Throwable tr) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.ERROR)) {
            log(tag, android.util.Log.ERROR, msg);
            log(tag, android.util.Log.ERROR, tr);
            return android.util.Log.e(_tag, msg, tr);
        }
        return 0;
    }

    /**
     * Send a {@link android.util.Log#ERROR} log message.
     * @param tag Used to identify the source of a log message.  It usually identifies
     *        the class or activity where the log call occurs.
     * @param fmt The message you would like logged.
     * @param args Arguments for formatting the message.
     */
    public static int e(String tag, String fmt, Object... args) {
        String _tag = makeTag(tag);
        if (isDebug() || android.util.Log.isLoggable(_tag, android.util.Log.ERROR)) {
            String msg = String.format(fmt, args);
            log(tag, android.util.Log.ERROR, msg);
            return android.util.Log.e(_tag, msg);
        }
        return 0;
    }

}

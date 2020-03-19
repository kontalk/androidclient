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

package org.kontalk.reporting;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.crashlytics.android.answers.SignUpEvent;

import android.content.Context;

import io.fabric.sdk.android.Fabric;

import org.kontalk.BuildConfig;


/**
 * Reporting manager for Crashlytics.
 * @author Daniele Ricci
 */
public class ReportingManager {

    private static boolean sEnabled;

    private ReportingManager() {
    }

    public static void register(Context context) {
        if (!BuildConfig.DEBUG && !sEnabled) {
            Fabric.with(context, new Crashlytics());
            sEnabled = true;
        }
    }

    public static void unregister(Context context) {
        if (sEnabled) {
            // HACK to unregister Fabric crash handler
            Thread.setDefaultUncaughtExceptionHandler(null);
            sEnabled = false;
        }
    }

    public static void logException(Throwable exception) {
        if (!BuildConfig.DEBUG && sEnabled)
            Crashlytics.logException(exception);
    }

    /**
     * Logs a registration attempt.
     * @param method the challenge being used
     */
    public static void logRegister(String method) {
        if (!BuildConfig.DEBUG && sEnabled)
            Answers.getInstance().logCustom(new CustomEvent("Register")
                .putCustomAttribute("Method", method));
    }

    public static void logSignUp(String method) {
        if (!BuildConfig.DEBUG && sEnabled)
            Answers.getInstance().logSignUp(new SignUpEvent()
                .putMethod(method)
                .putSuccess(true));
    }

}

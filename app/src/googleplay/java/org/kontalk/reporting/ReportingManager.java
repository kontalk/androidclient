/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.Context;

import io.fabric.sdk.android.Fabric;


/**
 * Reporting manager for Crashlytics.
 * @author Daniele Ricci
 */
public class ReportingManager {

    private ReportingManager() {
    }

    public static void register(Context context) {
        Fabric.with(context, new Crashlytics());
    }

    public static void logException(Throwable exception) {
        Crashlytics.logException(exception);
    }

}

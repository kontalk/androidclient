/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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


import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/**
 * System-related utilities.
 * @author Daniele Ricci
 */
public final class SystemUtils {

    private SystemUtils() {
    }

    public static PackageInfo getPackageInfo(Context context) throws PackageManager.NameNotFoundException {
        return context.getPackageManager()
            .getPackageInfo(context.getPackageName(), 0);
    }

    public static String getVersionName(Context context) {
        try {
            PackageInfo pInfo = SystemUtils.getPackageInfo(context);
            return pInfo.versionName;
        }
        catch (PackageManager.NameNotFoundException e) {
            // shouldn't happen
            return null;
        }
    }

    public static int getVersionCode(Context context) {
        try {
            PackageInfo pInfo = SystemUtils.getPackageInfo(context);
            return pInfo.versionCode;
        }
        catch (PackageManager.NameNotFoundException e) {
            // shouldn't happen
            return 0;
        }
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.Display;
import android.view.WindowManager;

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

    public static Point getDisplaySize(Context context) {
        Point displaySize = null;
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        if (display != null) {
            displaySize = new Point();
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB_MR2) {
                displaySize.set(display.getWidth(), display.getHeight());
            }
            else {
                display.getSize(displaySize);
            }
        }

        return displaySize;
    }

    public static int getDisplayRotation(Context context) {
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        return manager.getDefaultDisplay().getRotation();
    }

    /** Returns the type name of the current network, or null. */
    public static String getCurrentNetworkName(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info = connMgr.getActiveNetworkInfo();
        return info != null ? info.getTypeName() : null;
    }

    public static int getCurrentNetworkType(Context context) {
        ConnectivityManager connMgr = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo info = connMgr.getActiveNetworkInfo();
        return info != null ? info.getType() : -1;
    }

    public static boolean isOnWifi(Context context) {
        return getCurrentNetworkType(context) == ConnectivityManager.TYPE_WIFI;
    }

}

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.kontalk.R;


/**
 * System-related utilities.
 * @author Daniele Ricci
 */
public final class SystemUtils {

    private static final Pattern VERSION_CODE_MATCH = Pattern
        .compile("\\(([0-9]+)\\)$");

    private SystemUtils() {
    }

    public static boolean isOlderVersion(Context context, String version) {
        Matcher m = VERSION_CODE_MATCH.matcher(version);
        if (m.find() && m.groupCount() > 0) {
            try {
                int versionCode = Integer.parseInt(m.group(1));
                int currentVersion = getVersionCode(context);
                return versionCode < currentVersion;
            }
            catch (Exception ignored) {
            }

        }

        // no version code found at the end - assume older version
        return true;
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

    public static String getVersionFullName(Context context) {
        try {
            PackageInfo pInfo = SystemUtils.getPackageInfo(context);
            return context.getString(R.string.about_version,
                pInfo.versionName, pInfo.versionCode);
        }
        catch (PackageManager.NameNotFoundException e) {
            // shouldn't happen
            return null;
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

    /**
     * Returns the correct screen orientation based on the supposedly preferred
     * position of the device.
     * http://stackoverflow.com/a/16585072/1045199
     */
    public static int getScreenOrientation(Activity activity) {
        WindowManager windowManager =  (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        Configuration configuration = activity.getResources().getConfiguration();
        int rotation = windowManager.getDefaultDisplay().getRotation();

        // Search for the natural position of the device
        if(configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
            (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) ||
            configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270))
        {
            // Natural position is Landscape
            switch (rotation)
            {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                case Surface.ROTATION_180:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                case Surface.ROTATION_270:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            }
        }
        else {
            // Natural position is Portrait
            switch (rotation)
            {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_180:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                case Surface.ROTATION_270:
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            }
        }

        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
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

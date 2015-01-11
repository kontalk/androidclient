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

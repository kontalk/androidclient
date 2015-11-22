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

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;


/**
 * System-related utilities.
 * @author Daniele Ricci
 */
public final class SystemUtils {

    private static final Pattern VERSION_CODE_MATCH = Pattern
        .compile("\\(([0-9]+)\\)$");

    private static Uri sProfileUri;

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

    public static int getVersionCode(Context context) {
        return BuildConfig.VERSION_CODE;
    }

    public static String getVersionFullName(Context context) {
        return context.getString(R.string.about_version,
            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
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

    public static void acquireScreenOn(Activity activity) {
        activity.getWindow().addFlags(WindowManager
            .LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public static void releaseScreenOn(Activity activity) {
        activity.getWindow().clearFlags(WindowManager
            .LayoutParams.FLAG_KEEP_SCREEN_ON);
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

    /** Checks for network availability. */
    public static boolean isNetworkConnectionAvailable(Context context) {
        final ConnectivityManager cm = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm.getBackgroundDataSetting()) {
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.getState() == NetworkInfo.State.CONNECTED)
                return true;
        }

        return false;
    }

    public static Bitmap getProfilePhoto(Context context) {
        // profile photo is available only since API level 14
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            ContentResolver cr = context.getContentResolver();
            InputStream input = ContactsContract.Contacts
                .openContactPhotoInputStream(cr, ContactsContract.Profile.CONTENT_URI);
            if (input != null) {
                try {
                    return BitmapFactory.decodeStream(input);
                }
                finally {
                    try {
                        input.close();
                    }
                    catch (IOException ignore) {
                    }
                }
            }
        }

        return null;
    }

    public static Uri getProfileUri(Context context) {
        if (sProfileUri == null) {
            // profile contact is available only since API level 14
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                sProfileUri = ContactsContract.Profile.CONTENT_URI;
            }
            else {
                // try with the phone number
                String phoneNumber = Authenticator.getDefaultAccountName(context);
                sProfileUri = lookupPhoneNumber(context, phoneNumber);
            }
        }

        return sProfileUri;
    }

    public static Uri lookupPhoneNumber(Context context, String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber));
        Cursor cur = context.getContentResolver().query(uri,
            new String[] { ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.LOOKUP_KEY },
            null, null, null);
        if (cur != null) {
            try {
                if (cur.moveToNext()) {
                    long id = cur.getLong(0);
                    String lookupKey = cur.getString(1);
                    return ContactsContract.Contacts.getLookupUri(id, lookupKey);
                }
            }
            finally {
                cur.close();
            }
        }

        return null;
    }

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

}

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

package org.kontalk.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.support.v4.app.Fragment;

import pub.devrel.easypermissions.EasyPermissions;


/**
 * Permission utilities.
 * @author Daniele Ricci
 */
public class Permissions {

    public static final int RC_CALL_PHONE = 1001;
    public static final int RC_CONTACTS = 1002;
    public static final int RC_READ_EXT_STORAGE = 1003;
    public static final int RC_CAMERA = 1004;
    public static final int RC_RECORD_AUDIO = 1005;
    public static final int RC_LOCATION = 1006;

    public static boolean canCallPhone(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.CALL_PHONE);
    }

    public static void requestCallPhone(Fragment fragment, String rationale) {
        EasyPermissions.requestPermissions(fragment, rationale, RC_CALL_PHONE,
            Manifest.permission.CALL_PHONE);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean canWriteContacts(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS);
    }

    public static boolean canReadContacts(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.READ_CONTACTS);
    }

    public static void requestContacts(Activity activity, String rationale) {
        EasyPermissions.requestPermissions(activity, rationale, RC_CONTACTS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS);
    }

    @SuppressLint("InlinedApi")
    public static boolean canReadExternalStorage(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @SuppressLint("InlinedApi")
    public static void requestReadExternalStorage(Activity activity, String rationale) {
        EasyPermissions.requestPermissions(activity, rationale, RC_READ_EXT_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    @SuppressLint("InlinedApi")
    public static boolean canWriteExternalStorage(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    @SuppressLint("InlinedApi")
    public static void requestWriteExternalStorage(Activity activity, String rationale) {
        EasyPermissions.requestPermissions(activity, rationale, RC_READ_EXT_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean canUseCamera(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.CAMERA);
    }

    public static void requestCamera(Activity activity, String rationale) {
        EasyPermissions.requestPermissions(activity, rationale, RC_CAMERA,
            Manifest.permission.CAMERA);
    }

    public static void requestCamera(Fragment fragment, String rationale) {
        EasyPermissions.requestPermissions(fragment, rationale, RC_CAMERA,
            Manifest.permission.CAMERA);
    }

    @SuppressLint("InlinedApi")
    public static boolean canRecordAudio(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean canRecordAudioOnly(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.RECORD_AUDIO);
    }

    @SuppressLint("InlinedApi")
    public static void requestRecordAudio(Activity activity, String rationale) {
        EasyPermissions.requestPermissions(activity, rationale, RC_RECORD_AUDIO,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean canAccessLocation(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public static boolean canAccessSomeLocation(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.ACCESS_COARSE_LOCATION) ||
            EasyPermissions.hasPermissions(context,
                Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public static boolean canAccessFineLocation(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.ACCESS_FINE_LOCATION);
    }

    public static void requestLocation(Fragment fragment, String rationale) {
        EasyPermissions.requestPermissions(fragment, rationale, RC_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION);
    }

}

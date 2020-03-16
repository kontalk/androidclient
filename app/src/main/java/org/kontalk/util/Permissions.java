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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import androidx.fragment.app.Fragment;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;


/**
 * Permission utilities.
 * @author Daniele Ricci
 */
public class Permissions {

    public static final int RC_CALL_PHONE = 201;
    public static final int RC_CONTACTS = 202;
    public static final int RC_READ_EXT_STORAGE = 203;
    public static final int RC_WRITE_EXT_STORAGE = 204;
    public static final int RC_CAMERA = 205;
    public static final int RC_RECORD_AUDIO = 206;
    public static final int RC_LOCATION = 207;
    public static final int RC_PHONE_STATE = 208;

    public static boolean canCallPhone(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.CALL_PHONE);
    }

    public static void requestCallPhone(Fragment fragment) {
        EasyPermissions.requestPermissions(fragment, null, RC_CALL_PHONE,
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

    public static void requestReadExternalStorage(Activity activity, String rationale) {
        requestReadExternalStorage(activity, rationale, 0);
    }

    @SuppressLint("InlinedApi")
    public static void requestReadExternalStorage(Activity activity, String rationale, int index) {
        EasyPermissions.requestPermissions(activity, rationale, RC_READ_EXT_STORAGE + (index * 100),
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
        EasyPermissions.requestPermissions(activity, rationale, RC_WRITE_EXT_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    @SuppressLint("InlinedApi")
    public static void requestWriteExternalStorage(Fragment fragment, String rationale) {
        EasyPermissions.requestPermissions(fragment, rationale, RC_WRITE_EXT_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean canUseCamera(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.CAMERA);
    }

    public static void requestCamera(Activity activity, String rationale) {
        if (EasyPermissions.permissionPermanentlyDenied(activity, Manifest.permission.CAMERA)) {
            new AppSettingsDialog.Builder(activity)
                .setRationale(rationale)
                .build()
                .show();
        }
        else {
            EasyPermissions.requestPermissions(activity, rationale, RC_CAMERA,
                Manifest.permission.CAMERA);
        }
    }

    public static void requestCamera(Fragment fragment, String rationale) {
        if (EasyPermissions.permissionPermanentlyDenied(fragment, Manifest.permission.CAMERA)) {
            new AppSettingsDialog.Builder(fragment)
                .setRationale(rationale)
                .build()
                .show();
        }
        else {
            EasyPermissions.requestPermissions(fragment, rationale, RC_CAMERA,
                Manifest.permission.CAMERA);
        }
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
        if (EasyPermissions.permissionPermanentlyDenied(activity, Manifest.permission.RECORD_AUDIO) ||
                EasyPermissions.permissionPermanentlyDenied(activity, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                EasyPermissions.permissionPermanentlyDenied(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            new AppSettingsDialog.Builder(activity)
                .setRationale(rationale)
                .build()
                .show();
        }
        else {
            EasyPermissions.requestPermissions(activity, rationale, RC_RECORD_AUDIO,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
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

    public static void requestPhoneState(Activity activity, String rationale) {
        EasyPermissions.requestPermissions(activity, rationale, RC_PHONE_STATE,
            Manifest.permission.READ_PHONE_STATE);
    }

}

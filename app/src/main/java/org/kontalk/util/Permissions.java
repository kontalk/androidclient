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
import android.content.Context;
import android.support.v4.app.Fragment;

import pub.devrel.easypermissions.EasyPermissions;


/**
 * Permission utilities.
 * @author Daniele Ricci
 */
public class Permissions {

    public static final int RC_CALL_PHONE = 501;

    @SuppressLint("InlinedApi")
    public static boolean canWriteExternalStorage(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    public static boolean canCallPhone(Context context) {
        return EasyPermissions.hasPermissions(context,
            Manifest.permission.CALL_PHONE);
    }

    public static void requestCallPhone(Fragment fragment, String rationale) {
        EasyPermissions.requestPermissions(fragment, rationale, RC_CALL_PHONE,
            Manifest.permission.CALL_PHONE);
    }

}

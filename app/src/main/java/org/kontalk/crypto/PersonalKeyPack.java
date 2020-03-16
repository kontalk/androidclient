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

package org.kontalk.crypto;

import java.io.File;

import android.os.Environment;


public interface PersonalKeyPack {

    public static final String PUBLIC_KEY_FILENAME = "kontalk-public.asc";
    public static final String PRIVATE_KEY_FILENAME = "kontalk-private.asc";
    public static final String BRIDGE_CERT_FILENAME = "kontalk-login.crt";
    public static final String BRIDGE_KEY_FILENAME = "kontalk-login.key";
    public static final String BRIDGE_CERTPACK_FILENAME = "kontalk-login.p12";
    public static final String TRUSTED_KEYS_FILENAME = "trusted.properties";
    public static final String ACCOUNT_INFO_FILENAME = "account-info.properties";

    public static final String KEYPACK_FILENAME = "kontalk-keys.zip";
    public static final String KEYPACK_MIME = "application/zip";

    public static final File DEFAULT_KEYPACK = new File(Environment
        .getExternalStorageDirectory(), KEYPACK_FILENAME);

}

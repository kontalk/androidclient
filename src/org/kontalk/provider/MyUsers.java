/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.provider;

import android.net.Uri;
import android.provider.BaseColumns;


public class MyUsers {
    private MyUsers() {}

    public static final class Users implements BaseColumns {
        private Users() {}

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + UsersProvider.AUTHORITY + "/users");

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.kontalk.user";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.kontalk.user";

        public static final String HASH = "hash";
        public static final String NUMBER = "number";
        public static final String LOOKUP_KEY = "lookup_key";

        // uri parameter for update: triggers a complete resync
        public static final String RESYNC = "resync";
        // uri parameter for update: used with resync, triggers a complete sync
        // only if the database is newly created
        public static final String BOOTSTRAP = "bootstrap";
    }
}

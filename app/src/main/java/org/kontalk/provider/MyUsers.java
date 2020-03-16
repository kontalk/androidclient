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

package org.kontalk.provider;

import android.net.Uri;
import android.provider.BaseColumns;

import org.kontalk.BuildConfig;


public class MyUsers {
    private MyUsers() {}

    public interface CommonColumns extends BaseColumns {
        String JID = "jid";
    }

    public static final class Users implements CommonColumns {
        private Users() {}

        public static final Uri CONTENT_URI = Uri.parse("content://"
            + UsersProvider.AUTHORITY + "/users");
        public static final Uri CONTENT_URI_OFFLINE =  Users.CONTENT_URI.buildUpon()
            .appendQueryParameter(Users.OFFLINE, "true").build();

        private static final String ITEM_TYPE = BuildConfig.APPLICATION_ID + ".user";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/" + ITEM_TYPE;
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/" + ITEM_TYPE;

        public static final String NUMBER = "number";
        public static final String DISPLAY_NAME = "display_name";
        public static final String LOOKUP_KEY = "lookup_key";
        public static final String CONTACT_ID = "contact_id";
        public static final String REGISTERED = "registered";
        public static final String STATUS = "status";
        public static final String LAST_SEEN = "last_seen";
        public static final String BLOCKED = "blocked";

        // uri parameter for indexed cursor
        public static final String EXTRA_INDEX = "org.kontalk.provider.extra.INDEX";
        // uri parameter for update: triggers a complete resync
        public static final String RESYNC = "resync";
        // uri parameter for update: used with resync, triggers a complete sync
        // only if the database is newly created
        public static final String BOOTSTRAP = "bootstrap";
        // uri parameter for update: used with resync, swap backup users table
        // with the real one
        public static final String COMMIT = "commit";
        // uri parameter for select: use offline table
        public static final String OFFLINE = "offline";
        // uri parameter for insert: discard name and number when updating
        // (e.g. update a subscription entry from an existing contact)
        public static final String DISCARD_NAME = "discardName";

        // results by EXTRA_INDEX
        public static final String EXTRA_INDEX_COUNTS = "org.kontalk.provider.extra.INDEX_COUNTS";
        public static final String EXTRA_INDEX_TITLES = "org.kontalk.provider.extra.INDEX_TITLES";
    }

    public static final class Keys implements CommonColumns {
        private Keys() {}

        public static final String PUBLIC_KEY = "public_key";
        public static final String FINGERPRINT = "fingerprint";
        public static final String TIMESTAMP = "timestamp";
        public static final String TRUST_LEVEL = "trust_level";
        public static final String MANUAL_TRUST = "manual_trust";

        public static final Uri CONTENT_URI = Uri.parse("content://"
            + UsersProvider.AUTHORITY + "/keys");

        public static Uri getUri(String jid) {
            return CONTENT_URI.buildUpon()
                .appendPath(jid)
                .build();
        }

        public static Uri getUri(String jid, String fingerprint) {
            return CONTENT_URI.buildUpon()
                .appendPath(jid)
                .appendPath(fingerprint).build();
        }

        public static final int TRUST_UNKNOWN = 0;
        public static final int TRUST_IGNORED = 1;
        public static final int TRUST_VERIFIED = 2;

        public static final String INSERT_ONLY = "insertOnly";
    }
}

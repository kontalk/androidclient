package org.nuntius.provider;


import android.net.Uri;
import android.provider.BaseColumns;

public class MyUsers {
    private MyUsers() {}

    public static final class Users implements BaseColumns {
        private Users() {}

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + UsersProvider.AUTHORITY + "/users");

        /**
         * Builds a user {@link Uri}.
         * @param userId the user id hash
         * @return a new user {@link Uri}
         */
        public static Uri getUri(String userId) {
            return Uri.parse("content://"
                    + UsersProvider.AUTHORITY + "/users/" + Uri.encode(userId));
        }

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.nuntius.user";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.nuntius.user";

        public static final String HASH = "hash";
        public static final String NUMBER = "number";
    }
}

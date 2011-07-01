package org.kontalk.provider;

import android.provider.BaseColumns;


public class MyUsers {
    private MyUsers() {}

    public static final class Users implements BaseColumns {
        private Users() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.kontalk.user";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.kontalk.user";

        public static final String HASH = "hash";
        public static final String NUMBER = "number";
    }
}

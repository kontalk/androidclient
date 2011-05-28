package org.nuntius.provider;

import org.nuntius.MessagesProvider;

import android.net.Uri;
import android.provider.BaseColumns;

public class Messages {

    public static final Uri CONTENT_URI = Uri.parse("content://"
            + MessagesProvider.AUTHORITY + "/messages");

    public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.nuntius.Message";

    public static final class Message implements BaseColumns {
        private Message() {
        }

        public static final String MESSAGE_ID = _ID;
        public static final String PEER = "peer";
        public static final String MIME = "mime";
        public static final String CONTENT = "content";
    }

}


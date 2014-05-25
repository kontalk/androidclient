/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

public final class MyMessages {
    private MyMessages() {}

    public interface CommonColumns extends BaseColumns {
        public static final String PEER = "peer";
        public static final String DIRECTION = "direction";
        public static final String TIMESTAMP = "timestamp";
        public static final String UNREAD = "unread";
        public static final String STATUS_CHANGED = "status_changed";
        public static final String STATUS = "status";
        public static final String MESSAGE_ID = "msg_id";
        public static final String ENCRYPTED = "encrypted";
    }

    public static final class Messages implements CommonColumns {
        private Messages() {}

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + MessagesProvider.AUTHORITY + "/messages");

        public static final int DIRECTION_IN = 0;
        public static final int DIRECTION_OUT = 1;

        public static final int STATUS_INCOMING = 0;
        public static final int STATUS_SENDING = 1;
        public static final int STATUS_ERROR = 2;
        public static final int STATUS_NOTACCEPTED = 3;
        public static final int STATUS_SENT = 4;
        public static final int STATUS_RECEIVED = 5;
        public static final int STATUS_CONFIRMED = 6;
        public static final int STATUS_NOTDELIVERED = 7;
        /** Pending user review (e.g. unencryptable message). */
        public static final int STATUS_PENDING = 8;

        /**
         * Builds a message {@link Uri}.
         * @param msgId the message id
         * @return a new message {@link Uri}
         */
        public static Uri getUri(String msgId) {
            return Uri.parse("content://"
                    + MessagesProvider.AUTHORITY + "/messages/" + Uri.encode(msgId));
        }

        public static final class Fulltext implements BaseColumns {
            public static final Uri CONTENT_URI = Uri.parse("content://"
                    + MessagesProvider.AUTHORITY + "/fulltext");

            public static final String _ID = "rowid";
            public static final String THREAD_ID = "thread_id";
            public static final String CONTENT = "content";
        }

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.kontalk.message";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.kontalk.message";

        public static final String THREAD_ID = "thread_id";
        public static final String SERVER_TIMESTAMP = "server_timestamp";

        public static final String BODY_MIME = "body_mime";
        public static final String BODY_CONTENT = "body_content";
        public static final String BODY_LENGTH = "body_length";

        public static final String ATTACHMENT_MIME = "att_mime";
        public static final String ATTACHMENT_PREVIEW_PATH = "att_preview_path";
        public static final String ATTACHMENT_FETCH_URL = "att_fetch_url";
        public static final String ATTACHMENT_LOCAL_URI = "att_local_uri";
        public static final String ATTACHMENT_LENGTH = "att_length";
        public static final String ATTACHMENT_ENCRYPTED = "att_encrypted";
        public static final String ATTACHMENT_SECURITY_FLAGS = "att_security_flags";

        public static final String SECURITY_FLAGS = "security_flags";

        // not DESC here because the listview is reverse-stacked
        public static final String DEFAULT_SORT_ORDER = _ID;
        public static final String INVERTED_SORT_ORDER = _ID + " DESC";
    }

    /** Threads are just for conversations metadata. */
    public static final class Threads implements CommonColumns {
        private Threads() {}

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + MessagesProvider.AUTHORITY + "/threads");

        /** No subscription request whatsoever. */
        public static final int REQUEST_NONE = 0;
        /** An incoming subscription request is waiting to be approved. */
        public static final int REQUEST_WAITING = 1;
        /** Positive subscription response is waiting to be sent. */
        public static final int REQUEST_REPLY_PENDING_ACCEPT = 2;
        /** Block request is waiting to be sent. */
        public static final int REQUEST_REPLY_PENDING_BLOCK = 3;
        /** Unblock request is waiting to be sent. */
        public static final int REQUEST_REPLY_PENDING_UNBLOCK = 4;

        /** Conversation represents a message group for a given thread. */
        public static final class Conversations implements BaseColumns {
            public static final Uri CONTENT_URI = Uri
                .parse("content://" + MessagesProvider.AUTHORITY + "/conversations");
        }

        /** Request represents a presence subscription request. */
        public static final class Requests implements BaseColumns {
            public static final Uri CONTENT_URI = Uri
            	.parse("content://" + MessagesProvider.AUTHORITY + "/requests");
        }

        /**
         * Builds a thread {@link Uri}.
         * @param peer the peer of the thread
         * @return a new thread {@link Uri}
         */
        public static Uri getUri(String peer) {
            return Uri.parse("content://"
                    + MessagesProvider.AUTHORITY + "/threads/" + Uri.encode(peer));
        }

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/org.kontalk.thread";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.kontalk.thread";

        public static final String MIME = "mime";
        public static final String CONTENT = "content";
        public static final String COUNT = "count";
        public static final String DRAFT = "draft";
        public static final String REQUEST_STATUS = "request_status";

        public static final String DEFAULT_SORT_ORDER = "timestamp DESC";
        public static final String INVERTED_SORT_ORDER = "timestamp";
    }
}

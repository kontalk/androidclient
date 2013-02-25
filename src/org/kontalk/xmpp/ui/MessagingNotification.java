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

package org.kontalk.xmpp.ui;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.data.Contact;
import org.kontalk.xmpp.provider.MyMessages.Messages;
import org.kontalk.xmpp.provider.MyMessages.Threads;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;


/**
 * Various utility methods for managing system notifications.
 * @author Daniele Ricci
 */
public class MessagingNotification {
    public static final int NOTIFICATION_ID_MESSAGES        = 101;
    public static final int NOTIFICATION_ID_UPLOADING       = 102;
    public static final int NOTIFICATION_ID_UPLOAD_ERROR    = 103;
    public static final int NOTIFICATION_ID_DOWNLOADING     = 104;
    public static final int NOTIFICATION_ID_DOWNLOAD_OK     = 105;
    public static final int NOTIFICATION_ID_DOWNLOAD_ERROR  = 106;
    public static final int NOTIFICATION_ID_QUICK_REPLY     = 107;

    private static final String[] THREADS_UNREAD_PROJECTION =
    {
        Threads._ID,
        Threads.PEER,
        Threads.CONTENT,
        Threads.UNREAD,
        Threads.TIMESTAMP
    };

    private static final String THREADS_UNREAD_SELECTION =
        Threads.UNREAD + " <> 0 AND " +
        Threads.DIRECTION + " = " + Messages.DIRECTION_IN;

    /** Pending delayed notification update flag. */
    private static volatile boolean mPending;

    /** Peer to NOT be notified for new messages. */
    private static volatile String mPaused;

    /** This class is not instanciable. */
    private MessagingNotification() {}

    public static void setPaused(String peer) {
        mPaused = peer;
    }

    public static String getPaused() {
        return mPaused;
    }

    /** Starts messages notification updates in another thread. */
    public static void delayedUpdateMessagesNotification(final Context context, final boolean isNew) {
        // notifications are disabled
        if (!MessagingPreferences.getNotificationsEnabled(context))
            return;

        if (!mPending) {
            mPending = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    updateMessagesNotification(context, isNew);
                    mPending = false;
                }
            }).start();
        }
    }

    /**
     * Updates system notification for unread messages.
     * @param context
     * @param isNew if true a new message has come (starts notification alerts)
     */
    public static void updateMessagesNotification(Context context, boolean isNew) {
        // notifications are disabled
        if (!MessagingPreferences.getNotificationsEnabled(context))
            return;

        // if notifying new messages, wait a little bit
        // to let all incoming messages come through
        /*
        FIXME this is useless because message are slow to arrive anyway
        (time to receive packs, store messages in db, etc. wastes too much time
        if (isNew) {
            try {
                Thread.sleep(500);
            }
            catch (InterruptedException e) {
                // ignored
            }
        }
        */

        ContentResolver res = context.getContentResolver();
        NotificationManager nm = (NotificationManager) context
            .getSystemService(Context.NOTIFICATION_SERVICE);

        // query for unread threads
        String query = THREADS_UNREAD_SELECTION;
        String[] args = null;
        if (mPaused != null) {
            query += " AND " + Threads.PEER + " <> ?";
            args = new String[] { mPaused };
        }
        Cursor c = res.query(Threads.CONTENT_URI,
                THREADS_UNREAD_PROJECTION, query, args,
                Threads.INVERTED_SORT_ORDER);

        // no unread messages - delete notification
        if (c.getCount() == 0) {
            c.close();
            nm.cancel(NOTIFICATION_ID_MESSAGES);
            return;
        }

        // loop all threads and accumulate them
        MessageAccumulator accumulator = new MessageAccumulator(context);
        while (c.moveToNext()) {
            accumulator.accumulate(
                c.getLong(0),
                c.getString(1),
                c.getString(2),
                c.getInt(3),
                c.getLong(4)
            );
        }
        c.close();

        //Notification no = new Notification(R.drawable.icon_stat, accumulator.getTicker(), accumulator.getTimestamp());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext());
        builder.setTicker(accumulator.getTicker());
        builder.setNumber(accumulator.unreadCount);
        builder.setSmallIcon(R.drawable.icon_stat);
        builder.setContentTitle(accumulator.getTitle());
        builder.setContentText(accumulator.getText());
        builder.setContentIntent(accumulator.getPendingIntent());

        if (isNew) {
            int defaults = Notification.DEFAULT_LIGHTS;

            String ringtone = MessagingPreferences.getNotificationRingtone(context);
            if (ringtone != null && ringtone.length() > 0)
                builder.setSound(Uri.parse(ringtone));

            String vibrate = MessagingPreferences.getNotificationVibrate(context);
            if ("always".equals(vibrate) || ("silent_only".equals(vibrate) &&
                    ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE))
                        .getRingerMode() != AudioManager.RINGER_MODE_NORMAL))
                defaults |= Notification.DEFAULT_VIBRATE;

            builder.setDefaults(defaults);
        }

        nm.notify(NOTIFICATION_ID_MESSAGES, builder.build());

        // TODO take this from configuration
        boolean quickReply = false;
        if (isNew && quickReply) {
            Intent i = new Intent(context.getApplicationContext(), QuickReplyActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            i.putExtra("org.kontalk.quickreply.FROM", accumulator.getLastMessagePeer());
            i.putExtra("org.kontalk.quickreply.MESSAGE", accumulator.getLastMessageText());
            i.putExtra("org.kontalk.quickreply.OPEN_INTENT", accumulator.getLastMessagePendingIntent());
            context.startActivity(i);
        }
    }

    /**
     * This class accumulates all incoming unread threads and returns
     * well-formed data to be used in a {@link Notification}.
     * @author Daniele Ricci
     */
    private static final class MessageAccumulator {
        private final class ConversationStub {
            public long id;
            public String peer;
            public String content;
            public long timestamp;
        }

        private ConversationStub conversation;
        private int convCount;
        private int unreadCount;
        private Context mContext;
        private Contact mContact;

        public MessageAccumulator(Context context) {
            mContext = context;
        }

        /** Adds a conversation thread to the accumulator. */
        public void accumulate(long id, String peer, String content, int unread, long timestamp) {
            // check old accumulated conversation
            if (conversation != null) {
                if (!conversation.peer.equalsIgnoreCase(peer))
                    convCount++;
            }
            // no previous conversation - start counting
            else {
                convCount = 1;
                conversation = new ConversationStub();
            }

            conversation.id = id;
            conversation.peer = peer;
            conversation.content = content;
            conversation.timestamp = timestamp;

            unreadCount += unread;
        }

        private void cacheContact() {
            mContact = Contact.findByUserId(mContext, conversation.peer);
        }

        /** Returns the text that should be used as a ticker in the notification. */
        public CharSequence getTicker() {
            cacheContact();
            String peer = (mContact != null) ? mContact.getName() :
                mContext.getString(R.string.peer_unknown);
                // debug mode -- conversation.peer;

            SpannableStringBuilder buf = new SpannableStringBuilder();
            buf.append(peer).append(':').append(' ');
            buf.setSpan(new StyleSpan(Typeface.BOLD), 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            buf.append(conversation.content);

            return buf;
        }

        /** Returns the text that should be used as the notification title. */
        public String getTitle() {
            if (convCount > 1) {
                return mContext.getString(R.string.new_messages);
            }
            else {
                cacheContact();
                return (mContact != null) ? mContact.getName() :
                    mContext.getString(R.string.peer_unknown);
                    // debug mode -- conversation.peer;
            }
        }

        /** Returns the text that should be used as the notification text. */
        public String getText() {
            return (unreadCount > 1) ?
                    mContext.getString(R.string.unread_messages, unreadCount)
                    : conversation.content;
        }

        /** Returns the timestamp to be used in the notification. */
        public long getTimestamp() {
            return conversation.timestamp;
        }

        /** Builds a {@link PendingIntent} to be used in the notification. */
        public PendingIntent getPendingIntent() {
            Intent ni;
            // more than one unread conversation - open ConversationList
            if (convCount > 1) {
                ni = new Intent(mContext, ConversationList.class);
            }
            // one unread conversation - open ComposeMessage on that peer
            else {
                ni = ComposeMessage.fromConversation(mContext, conversation.id);
            }
            return PendingIntent.getActivity(mContext, NOTIFICATION_ID_MESSAGES,
                    ni, 0);
        }

        public String getLastMessageText() {
            return conversation.content;
        }

        public String getLastMessagePeer() {
            return conversation.peer;
        }

        public PendingIntent getLastMessagePendingIntent() {
            // one unread conversation - open ComposeMessage on that peer
            Intent ni = ComposeMessage.fromConversation(mContext, conversation.id);
            return PendingIntent.getActivity(mContext, NOTIFICATION_ID_QUICK_REPLY,
                    ni, 0);
        }
    }
}

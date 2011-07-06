package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyMessages.Threads;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;


/**
 * Various utility methods for managing system notifications.
 * @author Daniele Ricci
 * @version 1.0
 * TODO:
 * - we should keep track of already notified threads, and notify only new
 * threads or changed ones
 */
public class MessagingNotification {
    private static final int MESSAGES_NOTIFICATION_ID = 12;

    private static final String[] THREADS_UNREAD_PROJECTION =
    {
        Threads._ID,
        Threads.PEER,
        Threads.CONTENT,
        Threads.UNREAD,
        Threads.TIMESTAMP
    };

    private static final String THREADS_UNREAD_SELECTION =
        Threads.UNREAD + " > 0";

    /** This class is not instanciable. */
    private MessagingNotification() {}

    /** Delays by 3 seconds any messages notification updates. */
    public static void delayedUpdateMessagesNotification(final Context context, final boolean isNew) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {}
                updateMessagesNotification(context, isNew);
            }
        }).start();
    }

    /**
     * Updates system notification for unread messages.
     * @param context
     * @param isNew if true a new message has come (starts notification alerts)
     */
    public static void updateMessagesNotification(Context context, boolean isNew) {
        ContentResolver res = context.getContentResolver();
        NotificationManager nm = (NotificationManager) context
            .getSystemService(Context.NOTIFICATION_SERVICE);

        // query for unread threads
        Cursor c = res.query(Threads.CONTENT_URI,
                THREADS_UNREAD_PROJECTION, THREADS_UNREAD_SELECTION, null,
                Threads.INVERTED_SORT_ORDER);

        // no unread messages - delete notification
        if (c.getCount() == 0) {
            c.close();
            nm.cancel(MESSAGES_NOTIFICATION_ID);
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

        Notification no = new Notification(R.drawable.icon, accumulator.getTicker(), accumulator.getTimestamp());
        if (isNew) {
            no.defaults |= Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS;
            no.flags |= Notification.FLAG_SHOW_LIGHTS;
        }

        no.setLatestEventInfo(context.getApplicationContext(),
                accumulator.getTitle(), accumulator.getText(), accumulator.getPendingIntent());
        nm.notify(MESSAGES_NOTIFICATION_ID, no);
    }

    /**
     * This class accumulates all incoming unread threads and returns
     * well-formed data to be used in a {@link Notification}.
     * @author Daniele Ricci
     * @version 1.0
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
            mContact = Contact.findbyUserId(mContext, conversation.peer);
        }

        /** Returns the text that should be used as a ticker in the notification. */
        public CharSequence getTicker() {
            cacheContact();
            String peer = (mContact != null) ? mContact.getName() : conversation.peer;

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
                // FIXME use a contact cache
                cacheContact();
                return (mContact != null) ? mContact.getName() : conversation.peer;
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
                ni = new Intent(mContext, ComposeMessage.class);
                ni.setAction(ComposeMessage.ACTION_VIEW_CONVERSATION);
                ni.putExtra(ComposeMessage.MESSAGE_THREAD_ID, conversation.id);
            }
            return PendingIntent.getActivity(mContext, MESSAGES_NOTIFICATION_ID, ni, Intent.FLAG_ACTIVITY_NEW_TASK);
        }
    }
}

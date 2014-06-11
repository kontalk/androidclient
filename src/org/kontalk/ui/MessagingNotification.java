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

package org.kontalk.ui;

import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.util.Preferences;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigTextStyle;
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.support.v4.app.NotificationCompat.Style;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
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
    public static final int NOTIFICATION_ID_KEYPAIR_GEN     = 108;
    public static final int NOTIFICATION_ID_INVITATION      = 109;

    private static final String[] MESSAGES_UNREAD_PROJECTION =
    {
        Messages.THREAD_ID,
        CommonColumns.PEER,
        Messages.BODY_CONTENT,
        Messages.ATTACHMENT_MIME,
        CommonColumns.ENCRYPTED,
    };

    private static final String[] THREADS_UNREAD_PROJECTION =
    {
        CommonColumns._ID,
        CommonColumns.PEER,
        Threads.MIME,
        Threads.CONTENT,
        CommonColumns.ENCRYPTED,
        CommonColumns.UNREAD,
    };

    private static final String MESSAGES_UNREAD_SELECTION =
        CommonColumns.UNREAD + " <> 0 AND " +
        CommonColumns.DIRECTION + " = " + Messages.DIRECTION_IN;

    /** Pending delayed notification update flag. */
    private static volatile boolean mPending;

    /** Peer to NOT be notified for new messages. */
    private static volatile String mPaused;

    /** Peer of last notified chat invitation. */
    private static volatile String mLastInvitation;

    /** This class is not instanciable. */
    private MessagingNotification() {}

    public static void setPaused(String jid) {
        mPaused = jid;
    }

    public static String getPaused() {
        return mPaused;
    }

    private static boolean supportsBigNotifications() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN;
    }

    /** Starts messages notification updates in another thread. */
    public static void delayedUpdateMessagesNotification(final Context context, final boolean isNew) {
        // notifications are disabled
        if (!Preferences.getNotificationsEnabled(context))
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
        if (!Preferences.getNotificationsEnabled(context))
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

        String query = MESSAGES_UNREAD_SELECTION;
        String[] args = null;
        String[] proj;
        String order;
        Uri uri;
        if (supportsBigNotifications()) {
            uri = Messages.CONTENT_URI;
            proj = MESSAGES_UNREAD_PROJECTION;
            order = Messages.DEFAULT_SORT_ORDER;
        }
        else {
            uri = Threads.CONTENT_URI;
            proj = THREADS_UNREAD_PROJECTION;
            order = Threads.INVERTED_SORT_ORDER;
        }

        // is there a peer to not notify for?
        if (mPaused != null) {
            query += " AND " + CommonColumns.PEER + " <> ?";
            args = new String[] { StringUtils.parseName(mPaused) };
        }

        Cursor c = res.query(uri, proj, query, args, order);

        // this shouldn't happen, but who knows...
        if (c == null) {
            nm.cancel(NOTIFICATION_ID_MESSAGES);
            return;
        }

        // no unread messages - delete notification
        int unread = c.getCount();
        if (unread == 0) {
            c.close();
            nm.cancel(NOTIFICATION_ID_MESSAGES);
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext());

        if (supportsBigNotifications()) {
            Map<String, CharSequence[]> convs = new HashMap<String, CharSequence[]>();

            String peer = null;
            long id = 0;
            while (c.moveToNext()) {
                // thread_id for PendingIntent
                id = c.getLong(0);
                peer = c.getString(1);
                byte[] content = c.getBlob(2);
                String attMime = c.getString(3);

                CharSequence[] b = convs.get(peer);
                if (b == null) {
                    b = new CharSequence[2];
                    b[0] = new StringBuilder();
                    b[1] = null;
                    convs.put(peer, b);
                }
                else {
                    ((StringBuilder) b[0]).append('\n');
                }

                String textContent;

                boolean encrypted = c.getInt(4) != 0;
                if (encrypted) {
                	textContent = context.getString(R.string.text_encrypted);
                }
                else if (content == null && attMime != null) {
                	textContent = CompositeMessage.getSampleTextContent(attMime);
                }
                else {
                	textContent = content != null ? new String(content) : "";
                }

                ((StringBuilder) b[0]).append(textContent);
                b[1] = textContent;
            }
            c.close();

            // TODO we are not ready for this -- builder.addAction(android.R.drawable.ic_menu_revert, "Reply", accumulator.getPendingIntent());

            /* -- FIXME FIXME VERY UGLY CODE FIXME FIXME -- */

            Style style;
            CharSequence title, text, ticker;

            // more than one conversation - use InboxStyle
            if (convs.size() > 1) {
                style = new InboxStyle();

                // ticker: "X unread messages"
                ticker = context.getString(R.string.unread_messages, unread);

                // title
                title = ticker;

                // text: comma separated names (TODO RTL?)
                StringBuilder btext = new StringBuilder();
                int count = 0;
                for (String user : convs.keySet()) {
                    count++;

                    Contact contact = Contact.findByUserId(context, user);
                    String name = (contact != null) ? contact.getName() :
                        context.getString(R.string.peer_unknown);

                    if (contact != null) {
                        if (btext.length() > 0)
                            btext.append(", ");
                        btext.append(name);
                    }

                    // inbox line
                    if (count < 5) {
                        SpannableStringBuilder buf = new SpannableStringBuilder();
                        buf.append(name).append(' ');
                        buf.setSpan(new ForegroundColorSpan(Color.WHITE), 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        // take just the last message
                        buf.append(convs.get(user)[1]);

                        ((InboxStyle) style).addLine(buf);
                    }
                }

                if (btext.length() > 0)
                    text = btext.toString();
                else
                    // TODO i18n
                    text = "(unknown users)";

                String summary;
                if (count > 5)
                    // TODO i18n
                    summary = "+" + (convs.size() - count) + " more";
                else
                    summary = Authenticator.getDefaultAccount(context).name;

                ((InboxStyle) style).setSummaryText(summary);
            }
            // one conversation, use BigTextStyle
            else {
                String content = convs.get(peer)[0].toString();
                CharSequence last = convs.get(peer)[1];

                // big text content
                style = new BigTextStyle();
                ((BigTextStyle) style).bigText(content);
                ((BigTextStyle) style).setSummaryText(Authenticator.getDefaultAccount(context).name);

                // ticker
                Contact contact = Contact.findByUserId(context, peer);
                String name = (contact != null) ? contact.getName() :
                    context.getString(R.string.peer_unknown);
                    // debug mode -- conversation.peer;

                SpannableStringBuilder buf = new SpannableStringBuilder();
                buf.append(name).append(':').append(' ');
                buf.setSpan(new StyleSpan(Typeface.BOLD), 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                buf.append(last);

                ticker = buf;

                // title
                title = name;

                // text
                text = (unread > 1) ?
                    context.getString(R.string.unread_messages, unread)
                    : content;

                // avatar
                if (contact != null) {
                    BitmapDrawable avatar = (BitmapDrawable) contact.getAvatar(context, null);
                    if (avatar != null)
                        builder.setLargeIcon(avatar.getBitmap());
                }
            }

            builder.setNumber(unread);
            builder.setSmallIcon(R.drawable.stat_notify);

            builder.setTicker(ticker);
            builder.setContentTitle(title);
            builder.setContentText(text);
            builder.setStyle(style);

            Intent ni;
            // more than one unread conversation - open ConversationList
            if (convs.size() > 1) {
                ni = new Intent(context, ConversationList.class);
            }
            // one unread conversation - open ComposeMessage on that peer
            else {
                ni = ComposeMessage.fromConversation(context, id);
            }
            PendingIntent pi = PendingIntent.getActivity(context, NOTIFICATION_ID_MESSAGES,
                    ni, 0);

            builder.setContentIntent(pi);
        }

        else {

            // loop all threads and accumulate them
            MessageAccumulator accumulator = new MessageAccumulator(context);
            while (c.moveToNext()) {
                String content = c.getString(3);
                boolean encrypted = c.getInt(4) != 0;

                if (encrypted)
                	content = context.getString(R.string.text_encrypted);
                else if (content == null)
                	content = CompositeMessage.getSampleTextContent(c.getString(2));

                accumulator.accumulate(
                    c.getLong(0),
                    c.getString(1),
                    content,
                    c.getInt(5)
                );
            }
            c.close();

            builder.setTicker(accumulator.getTicker());
            Contact contact = accumulator.getContact();
            if (contact != null) {
                BitmapDrawable avatar = (BitmapDrawable) contact.getAvatar(context, null);
                if (avatar != null)
                    builder.setLargeIcon(avatar.getBitmap());
            }
            builder.setNumber(accumulator.unreadCount);
            builder.setSmallIcon(R.drawable.stat_notify);
            builder.setContentTitle(accumulator.getTitle());
            builder.setContentText(accumulator.getText());
            builder.setContentIntent(accumulator.getPendingIntent());
        }

        if (isNew) {
        	setDefaults(context, builder);
        }

        nm.notify(NOTIFICATION_ID_MESSAGES, builder.build());

        /* TODO take this from configuration
        boolean quickReply = false;
        if (isNew && quickReply) {
            Intent i = new Intent(context.getApplicationContext(), QuickReplyActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            i.putExtra("org.kontalk.quickreply.FROM", accumulator.getLastMessagePeer());
            i.putExtra("org.kontalk.quickreply.MESSAGE", accumulator.getLastMessageText());
            i.putExtra("org.kontalk.quickreply.OPEN_INTENT", accumulator.getLastMessagePendingIntent());
            context.startActivity(i);
        }
        */
    }

    private static void setDefaults(Context context, NotificationCompat.Builder builder) {
        int defaults = Notification.DEFAULT_LIGHTS;

        String ringtone = Preferences.getNotificationRingtone(context);
        if (ringtone != null && ringtone.length() > 0)
            builder.setSound(Uri.parse(ringtone));

        String vibrate = Preferences.getNotificationVibrate(context);
        if ("always".equals(vibrate) || ("silent_only".equals(vibrate) &&
                ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE))
                    .getRingerMode() != AudioManager.RINGER_MODE_NORMAL))
            defaults |= Notification.DEFAULT_VIBRATE;


        builder.setDefaults(defaults);
    }

    /** Triggers a notification for a chat invitation. */
    public static void chatInvitation(Context context, String userId) {
    	// open conversation, do not send notification
    	if (userId.equalsIgnoreCase(StringUtils.parseName(mPaused)))
    		return;

        // find the contact for the userId
        Contact contact = Contact.findByUserId(context, userId);

        String title = (contact != null) ? contact.getName() :
            context.getString(R.string.peer_unknown);

        // notification will open the conversation
        Intent ni = ComposeMessage.fromUserId(context, userId);
        PendingIntent pi = PendingIntent.getActivity(context,
        	NOTIFICATION_ID_INVITATION, ni, 0);

        // build the notification
        NotificationCompat.Builder builder = new NotificationCompat
            .Builder(context.getApplicationContext())
        	.setAutoCancel(true)
            .setSmallIcon(R.drawable.stat_notify)
            .setTicker(context.getString(R.string.title_invitation))
            .setContentTitle(title)
            // TODO i18n
            .setContentText("is inviting you to chat")
            .setContentIntent(pi);

        // include an avatar if any
        if (contact != null) {
            BitmapDrawable avatar = (BitmapDrawable) contact.getAvatar(context, null);
            if (avatar != null)
                builder.setLargeIcon(avatar.getBitmap());
        }

        // defaults (sound, vibration, lights)
        setDefaults(context, builder);

        // fire it up!
        NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(NOTIFICATION_ID_INVITATION, builder.build());

        // this is for clearChatInvitation()
        mLastInvitation = userId;
    }

    /** Cancel a chat invitation notification. */
    public static void clearChatInvitation(Context context, String userId) {
    	if (userId.equalsIgnoreCase(mLastInvitation)) {
            NotificationManager nm = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);

            nm.cancel(NOTIFICATION_ID_INVITATION);
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
        public void accumulate(long id, String peer, String content, int unread) {
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

            unreadCount += unread;
        }

        private void cacheContact() {
            mContact = Contact.findByUserId(mContext, conversation.peer);
        }

        public Contact getContact() {
            return mContact;
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

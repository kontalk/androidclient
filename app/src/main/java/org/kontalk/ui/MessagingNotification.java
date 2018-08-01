/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jxmpp.jid.Jid;
import org.jxmpp.util.XmppStringUtils;

import android.accounts.Account;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.BigPictureStyle;
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.support.v4.app.NotificationCompat.MessagingStyle;
import android.support.v4.app.NotificationCompat.Style;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.LocationComponent;
import org.kontalk.provider.MessagesProviderClient.GroupThreadContent;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.NotificationActionReceiver;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;

import me.leolin.shortcutbadger.ShortcutBadger;


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
    public static final int NOTIFICATION_ID_AUTH_ERROR      = 110;
    public static final int NOTIFICATION_ID_FOREGROUND      = 111;

    private static final String[] MESSAGES_UNREAD_PROJECTION =
    {
        Messages.THREAD_ID,
        Messages.PEER,
        Messages.BODY_MIME,
        Messages.BODY_CONTENT,
        Messages.ATTACHMENT_MIME,
        Messages.ATTACHMENT_PREVIEW_PATH,
        Messages.GEO_LATITUDE,
        Messages.ENCRYPTED,
        Groups.GROUP_JID,
        Groups.SUBJECT,
        Messages.TIMESTAMP,
    };

    // mapped to MESSAGES_UNREAD_PROJECTION
    private static final int COLUMN_MESSAGES_THREAD_ID = 0;
    private static final int COLUMN_MESSAGES_PEER = 1;
    private static final int COLUMN_MESSAGES_BODY_MIME = 2;
    private static final int COLUMN_MESSAGES_BODY_CONTENT = 3;
    private static final int COLUMN_MESSAGES_ATTACHMENT_MIME = 4;
    private static final int COLUMN_MESSAGES_ATTACHMENT_PREVIEW_PATH = 5;
    private static final int COLUMN_MESSAGES_GEO_LATITUDE = 6;
    private static final int COLUMN_MESSAGES_ENCRYPTED = 7;
    private static final int COLUMN_MESSAGES_GROUP_JID = 8;
    private static final int COLUMN_MESSAGES_GROUP_SUBJECT = 9;
    private static final int COLUMN_MESSAGES_TIMESTAMP = 10;

    private static final String[] THREADS_UNREAD_PROJECTION =
    {
        Threads._ID,
        Threads.PEER,
        Threads.MIME,
        Threads.CONTENT,
        Threads.ENCRYPTED,
        Threads.UNREAD,
        Groups.GROUP_JID,
        Groups.SUBJECT,
        Threads.TIMESTAMP,
    };

    // mapped to THREADS_UNREAD_PROJECTION
    private static final int COLUMN_THREADS_ID = 0;
    private static final int COLUMN_THREADS_PEER = 1;
    private static final int COLUMN_THREADS_MIME = 2;
    private static final int COLUMN_THREADS_CONTENT = 3;
    private static final int COLUMN_THREADS_ENCRYPTED = 4;
    private static final int COLUMN_THREADS_UNREAD = 5;
    private static final int COLUMN_THREADS_GROUP_JID = 6;
    private static final int COLUMN_THREADS_GROUP_SUBJECT = 7;
    private static final int COLUMN_THREADS_TIMESTAMP = 8;

    private static final String MESSAGES_UNREAD_SELECTION =
        Messages.NEW + " <> 0 AND " +
        Messages.DIRECTION + " = " + Messages.DIRECTION_IN;

    /** Pending delayed notification update flag. */
    @SuppressWarnings("WeakerAccess")
    static volatile boolean sPending;

    /** Temporary disable all notifications flag */
    private static volatile boolean sDisabled;

    /** Peer to NOT be notified for new messages. */
    private static volatile String sPaused;

    /** Peer of last notified chat invitation. */
    private static volatile String sLastInvitation;

    /** Notification action intents stuff. */
    public static final String ACTION_NOTIFICATION_DELETED = "org.kontalk.ACTION_NOTIFICATION_DELETED";
    public static final String ACTION_NOTIFICATION_REPLY = "org.kontalk.ACTION_NOTIFICATION_REPLY";
    public static final String ACTION_NOTIFICATION_MARK_READ = "org.kontalk.ACTION_NOTIFICATION_MARK_READ";

    /** This class is not instanciable. */
    private MessagingNotification() {}

    public static void init(Context context) {
    }

    public static void setPaused(String jid) {
        sPaused = jid;
    }

    public static boolean isPaused(Jid jid) {
        return sPaused != null && sPaused.equalsIgnoreCase(jid.asBareJid().toString());
    }

    public static boolean isPaused(String jid) {
        return sPaused != null && sPaused.equalsIgnoreCase(XmppStringUtils.parseBareJid(jid));
    }

    /** Enables all notifications. */
    public static void enable() {
        sDisabled = false;
    }

    /** Temporarly disable all notifications. */
    public static void disable() {
        sDisabled = true;
    }

    private static boolean supportsBigNotifications() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN;
    }

    static boolean supportsDirectReply() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
    }

    /** Starts messages notification updates in another thread. */
    public static void delayedUpdateMessagesNotification(final Context context, final boolean isNew) {
        if (!sPending) {
            sPending = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    updateMessagesNotification(context, isNew);
                    sPending = false;
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
        // no default account. WTF?!?
        Account account = Authenticator.getDefaultAccount(context);
        if (account == null)
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
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);

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
        final String paused = sPaused;
        if (paused != null) {
            query += " AND " + Messages.PEER + " <> ? AND " +
                "(" + Groups.GROUP_JID + " IS NULL OR " + Groups.GROUP_JID + " <> ?)";
            args = new String[] { paused, paused };
        }

        Cursor c = res.query(uri, proj, query, args, order);

        // this shouldn't happen, but who knows...
        if (c == null) {
            clearMessageNotification(context);
            return;
        }

        // no unread messages - delete notification
        int unread = c.getCount();
        if (unread == 0) {
            c.close();
            clearMessageNotification(context);
            return;
        }

        // notifications are disabled
        if (!Preferences.getNotificationsEnabled(context) || sDisabled) {
            c.close();
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext());
        Set<Uri> conversationIds = new HashSet<>(unread);
        long latestTimestamp = 0;

        if (supportsBigNotifications()) {
            NotificationGenerator ngen = new NotificationGenerator(context, builder);

            long id = 0;
            while (c.moveToNext()) {
                // thread_id for PendingIntent
                id = c.getLong(COLUMN_MESSAGES_THREAD_ID);
                String peer = c.getString(COLUMN_MESSAGES_PEER);
                String mime = c.getString(COLUMN_MESSAGES_BODY_MIME);
                byte[] content = c.getBlob(COLUMN_MESSAGES_BODY_CONTENT);
                String attMime = c.getString(COLUMN_MESSAGES_ATTACHMENT_MIME);
                String attPreviewPath = c.getString(COLUMN_MESSAGES_ATTACHMENT_PREVIEW_PATH);
                boolean encrypted = c.getInt(COLUMN_MESSAGES_ENCRYPTED) != 0;
                String groupJid = c.getString(COLUMN_MESSAGES_GROUP_JID);
                String groupSubject = c.getString(COLUMN_MESSAGES_GROUP_SUBJECT);
                long timestamp = c.getLong(COLUMN_MESSAGES_TIMESTAMP);

                if (!c.isNull(COLUMN_MESSAGES_GEO_LATITUDE)) {
                    content = context.getString(R.string.notification_location).getBytes();
                }

                // store conversation id for intents
                conversationIds.add(ContentUris.withAppendedId(Threads.CONTENT_URI, id));

                Uri attPreview = attPreviewPath != null ? Uri.fromFile(new File(attPreviewPath)) : null;
                ngen.addMessage(peer, mime, content, attMime, attPreview, encrypted, timestamp, groupJid, groupSubject);
                latestTimestamp = Math.max(latestTimestamp, timestamp);
            }
            c.close();

            int convCount = ngen.build(account, unread, conversationIds.iterator().next());

            builder.setSmallIcon(R.drawable.ic_stat_notify);
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);

            Intent ni;
            // more than one unread conversation - open conversations list
            if (convCount > 1) {
                ni = new Intent(context, ConversationsActivity.class);
                ni.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            // one unread conversation - open compose message on that thread
            else {
                ni = ComposeMessage.fromConversation(context, id);
            }
            PendingIntent pi = createPendingIntent(context, ni);

            builder.setContentIntent(pi);
        }

        else {
            // loop all threads and accumulate them
            MessageAccumulator accumulator = new MessageAccumulator(context);
            while (c.moveToNext()) {
                long threadId = c.getLong(COLUMN_THREADS_ID);
                String peer = c.getString(COLUMN_THREADS_PEER);
                String mime = c.getString(COLUMN_THREADS_MIME);
                String content = c.getString(COLUMN_THREADS_CONTENT);
                boolean encrypted = c.getInt(COLUMN_THREADS_ENCRYPTED) != 0;
                int unreadCount = c.getInt(COLUMN_THREADS_UNREAD);
                String groupJid = c.getString(COLUMN_THREADS_GROUP_JID);
                String groupSubject = c.getString(COLUMN_THREADS_GROUP_SUBJECT);
                long timestamp = c.getLong(COLUMN_THREADS_TIMESTAMP);

                if (encrypted) {
                    content = context.getString(R.string.text_encrypted);
                }
                else if (GroupCommandComponent.supportsMimeType(mime)) {
                    // content is in a special format
                    GroupThreadContent parsed = GroupThreadContent.parseIncoming(content);
                    try {
                        peer = parsed.sender;
                        content = GroupCommandComponent.getTextContent(context, parsed.command, true);
                    }
                    catch (UnsupportedOperationException e) {
                        // TODO using another string
                        content = context.getString(R.string.peer_unknown);
                    }
                }
                else if (LocationComponent.supportsMimeType(mime)) {
                    content = context.getString(R.string.notification_location);
                }
                else if (content == null) {
                    content = CompositeMessage.getSampleTextContent(mime);
                }

                accumulator.accumulate(
                    threadId,
                    peer,
                    content,
                    unreadCount,
                    // group data
                    groupJid,
                    groupSubject
                );
                // actually we don't need to check for max since conversations were selected
                // in timestamp order, but whatever...
                latestTimestamp = Math.max(latestTimestamp, timestamp);
                conversationIds.add(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId));
            }
            c.close();

            builder.setTicker(accumulator.getTicker());
            Contact contact = accumulator.getContact();
            if (contact != null) {
                Bitmap avatar = contact.getAvatarBitmap(context, true);
                builder.setLargeIcon(avatar);
            }
            builder.setNumber(accumulator.unreadCount);
            builder.setSmallIcon(R.drawable.ic_stat_notify);
            builder.setContentTitle(accumulator.getTitle());
            builder.setContentText(accumulator.getText());
            builder.setContentIntent(accumulator.getPendingIntent());

            // overwrite thread count with message count (used for badges later)
            unread = accumulator.unreadCount;
        }

        // shouldn't happen, but let's check it anyway
        if (latestTimestamp > 0)
            builder.setWhen(latestTimestamp);

        // build on delete intent for conversations
        Intent notificationDeleteIntent = new Intent(context, NotificationActionReceiver.class);
        notificationDeleteIntent.setAction(ACTION_NOTIFICATION_DELETED);
        notificationDeleteIntent.putExtra("org.kontalk.datalist", conversationIds.toArray(new Uri[conversationIds.size()]));
        builder.setDeleteIntent(PendingIntent.getBroadcast(context, 0,
            notificationDeleteIntent, PendingIntent.FLAG_UPDATE_CURRENT));

        if (isNew) {
            setAlerts(context, builder);
        }

        // features (priority, category)
        setFeatures(context, builder);

        Notification notification = builder.build();

        // set badge counter
        ShortcutBadger.applyNotification(context, notification, unread);
        ShortcutBadger.applyCount(context, unread);

        nm.notify(NOTIFICATION_ID_MESSAGES, notification);

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

    /** Adds proper alerts to the notification. */
    private static void setAlerts(Context context, NotificationCompat.Builder builder) {
        int defaults = 0;

        if (Preferences.getNotificationLED(context)) {
            int ledColor = Preferences.getNotificationLEDColor(context);
            builder.setLights(ledColor, 1000, 1000);
        }
        else {
            // this will disable the LED completely
            builder.setLights(0, 0, 0);
        }

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

    /** High priority message with Kontalk colors :-) */
    private static void setFeatures(Context context, NotificationCompat.Builder builder) {
        builder
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setColor(ContextCompat.getColor(context, R.color.app_accent));
    }

    private static void clearMessageNotification(Context context) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancel(NOTIFICATION_ID_MESSAGES);
        ShortcutBadger.removeCount(context);
    }

    private static final class NotificationConversation {
        static final class ConversationMessage {
            final String peer;
            final CharSequence content;
            final long timestamp;
            final String mime;
            final Uri media;

            ConversationMessage(String peer, CharSequence content, long timestamp, String mime, Uri media) {
                this.peer = peer;
                this.content = content;
                this.timestamp = timestamp;
                this.mime = mime;
                this.media = media;
            }

            @Override
            public String toString() {
                return content.toString();
            }
        }

        final List<ConversationMessage> content;
        final String groupJid;
        final String groupSubject;

        final Set<String> allPeers = new HashSet<>();
        CharSequence lastContent;

        NotificationConversation(List<ConversationMessage> content, CharSequence lastContent, String groupJid, String groupSubject) {
            this.content = content;
            this.lastContent = lastContent;
            this.groupJid = groupJid;
            this.groupSubject = groupSubject;
        }

        public void addContent(ConversationMessage message) {
            this.content.add(message);
            this.allPeers.add(message.peer);
        }
    }

    /** Triggers a notification for a chat invitation. */
    public static void chatInvitation(Context context, String jid) {
        // open conversation, do not send notification
        if (jid.equalsIgnoreCase(sPaused))
            return;

        // find the contact for the userId
        Contact contact = Contact.findByUserId(context, jid);

        String title = contact.getDisplayName();

        // notification will open the conversation
        Intent ni = ComposeMessage.fromUserId(context, jid);
        ni.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_SINGLE_TOP
            | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context,
            NOTIFICATION_ID_INVITATION, ni, 0);

        // build the notification
        NotificationCompat.Builder builder = new NotificationCompat
            .Builder(context.getApplicationContext());
        builder
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setTicker(context.getString(R.string.title_invitation))
            .setContentTitle(title)
            .setContentText(context.getString(R.string.invite_notification))
            .setContentIntent(pi);

        // include an avatar if any
        Bitmap avatar = contact.getAvatarBitmap(context, true);
        builder.setLargeIcon(avatar);

        // alerts (sound, vibration, lights)
        setAlerts(context, builder);
        // features (priority, category)
        setFeatures(context, builder);

        // fire it up!
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(NOTIFICATION_ID_INVITATION, builder.build());

        // this is for clearChatInvitation()
        sLastInvitation = jid;
    }

    /** Cancel a chat invitation notification. */
    public static void clearChatInvitation(Context context, String userId) {
        if (userId.equalsIgnoreCase(sLastInvitation)) {
            NotificationManagerCompat nm = NotificationManagerCompat.from(context);
            nm.cancel(NOTIFICATION_ID_INVITATION);
        }
    }

    /** Fires an authentication error notification. */
    public static void authenticationError(Context context) {
        // notification will open the conversation
        Intent ni = ConversationsActivity.authenticationErrorWarning(context);
        PendingIntent pi = PendingIntent.getActivity(context,
            NOTIFICATION_ID_AUTH_ERROR, ni, PendingIntent.FLAG_UPDATE_CURRENT);

        // build the notification
        NotificationCompat.Builder builder = new NotificationCompat
            .Builder(context.getApplicationContext());
        builder
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setTicker(context.getString(R.string.title_auth_error))
            .setContentTitle(context.getString(R.string.title_auth_error))
            .setContentText(context.getString(R.string.notification_text_more))
            .setContentIntent(pi);

        // defaults (sound, vibration, lights)
        setAlerts(context, builder);

        // fire it up!
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(NOTIFICATION_ID_AUTH_ERROR, builder.build());
    }

    public static void clearAuthenticationError(Context context) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancel(NOTIFICATION_ID_AUTH_ERROR);
    }

    public static Notification buildForegroundNotification(Context context) {
        Intent ni = new Intent(context.getApplicationContext(), ConversationsActivity.class);
        PendingIntent pi = PendingIntent.getActivity(context,
            NOTIFICATION_ID_FOREGROUND, ni, PendingIntent.FLAG_UPDATE_CURRENT);

        // build the notification
        return new NotificationCompat.Builder(context.getApplicationContext())
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setTicker(context.getString(R.string.notification_ticker_service_running))
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_text_service_running))
            .setContentIntent(pi)
            .build();
    }

    /**
     * Takes messages to be notified and fills a notification builder.
     * Used only for big notifications (JB+).
     */
    private static final class NotificationGenerator {
        private final Context mContext;
        private final NotificationCompat.Builder mBuilder;
        private final Map<String, NotificationConversation> mConversations;

        NotificationGenerator(Context context, NotificationCompat.Builder builder) {
            mContext = context;
            mBuilder = builder;
            mConversations = new LinkedHashMap<>();
        }

        void addMessage(String peer, String mime, byte[] content, String attMime, Uri attPreview, boolean encrypted, long timestamp,
                String groupJid, String groupSubject) {
            String key = conversationKey(peer, groupJid);
            NotificationConversation conv = mConversations.get(key);
            if (conv == null) {
                conv = new NotificationConversation(new LinkedList<NotificationConversation.ConversationMessage>(),
                    null, groupJid, groupSubject);
                mConversations.put(key, conv);
            }

            String textContent;

            if (encrypted) {
                textContent = mContext.getString(R.string.text_encrypted);
            }
            else if (content == null && attMime != null) {
                textContent = CompositeMessage.getSampleTextContent(attMime);
            }
            else {
                textContent = content != null ? new String(content) : "";
                if (GroupCommandComponent.supportsMimeType(mime)) {
                    try {
                        textContent = GroupCommandComponent.getTextContent(mContext, textContent, true);
                    }
                    catch (UnsupportedOperationException e) {
                        // TODO using another string
                        textContent = mContext.getString(R.string.peer_unknown);
                    }
                }
            }

            conv.addContent(new NotificationConversation.ConversationMessage(peer, textContent, timestamp, attMime, attPreview));
            conv.lastContent = textContent;
        }

        private String conversationKey(String peer, String groupJid) {
            return groupJid != null ? groupJid : peer;
        }

        /**
         * Call this when all messages are added. We will fill the builder with
         * relevant information.
         * @param account account information
         * @param unread total number of unread messages
         * @param firstThreadUri Uri of first conversation, used for reply intent
         * @return the number of conversations (i.e. threads) involved
         */
        int build(Account account, int unread, Uri firstThreadUri) {
            int convCount = mConversations.size();
            Style style = null;
            CharSequence title, text, ticker;

            // more than one conversation - use InboxStyle
            if (convCount > 1) {
                InboxStyle inboxStyle = new InboxStyle();

                // ticker: "X unread messages"
                ticker = mContext.getResources().getQuantityString(R.plurals.unread_messages, unread, unread);

                // title
                title = ticker;

                // text: comma separated names (TODO RTL?)
                StringBuilder btext = new StringBuilder();
                int count = 0;
                for (String convId : mConversations.keySet()) {
                    NotificationConversation conv = mConversations.get(convId);
                    count++;

                    // we'll take the last message and user who sent it
                    String lastPeer = ((LinkedList<NotificationConversation.ConversationMessage>) conv.content)
                        .getLast().peer;
                    Contact contact = Contact.findByUserId(mContext, lastPeer);
                    String name = contact.getDisplayName();

                    if (conv.groupJid != null) {
                        name = mContext.getResources().getString(R.string.notification_group_title,
                            name, (TextUtils.isEmpty(conv.groupSubject) ?
                                mContext.getString(R.string.group_untitled) : conv.groupSubject));
                    }

                    // add persons to notification
                    for (String peer : conv.allPeers) {
                        Contact lContact = Contact.findByUserId(mContext, peer);
                        Uri personUri = lContact.getUri();
                        if (personUri == null && lContact.getNumber() != null) {
                            // no contact uri available, try phone number lookup
                            try {
                                personUri = Uri.parse("tel:" + lContact.getNumber());
                            }
                            catch (Exception ignored) {
                            }
                        }
                        if (personUri != null)
                            mBuilder.addPerson(personUri.toString());
                    }

                    if (btext.length() > 0)
                        btext.append(", ");
                    btext.append(name);

                    // inbox line
                    if (count < 5) {
                        SpannableStringBuilder buf = new SpannableStringBuilder();
                        buf.append(name).append(' ');
                        buf.setSpan(new ForegroundColorSpan(ContextCompat
                            .getColor(mContext, R.color.notification_name_color)),
                            0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        // take just the last message
                        buf.append(conv.lastContent);

                        inboxStyle.addLine(buf);
                    }
                }

                if (btext.length() > 0)
                    text = btext.toString();
                else
                    // TODO i18n
                    text = "(unknown users)";

                String summary;
                int moreCount = mConversations.size() - count;
                if (moreCount > 0) {
                    summary = mContext.getResources()
                        .getQuantityString(R.plurals.notification_more, moreCount, moreCount);
                }
                else {
                    summary = account.name;
                }

                inboxStyle.setSummaryText(summary);

                style = inboxStyle;
            }
            // one conversation/one group, use MessagingStyle
            else {
                NotificationConversation conv = mConversations.values().iterator().next();
                List<NotificationConversation.ConversationMessage> content = conv.content;
                CharSequence last = conv.lastContent;

                // needed only for custom direct reply
                SpannableStringBuilder allContent = null;
                if (!supportsDirectReply())
                    allContent = new SpannableStringBuilder();

                // single image media, use big picture style
                NotificationConversation.ConversationMessage soloMessage = content.get(0);
                if (unread == 1 && soloMessage.media != null && ImageComponent.supportsMimeType(soloMessage.mime)) {
                    InputStream in = null;
                    try {
                        in = mContext.getContentResolver().openInputStream(soloMessage.media);
                        Bitmap bitmap = MediaStorage.loadBitmapSimple(in);

                        style = new BigPictureStyle()
                            .bigPicture(bitmap);
                    }
                    catch (Exception e) {
                        // unable to load big picture
                        // will go on with normal notification
                    }
                    finally {
                        SystemUtils.closeStream(in);
                    }
                }

                String name = null;
                if (style == null) {
                    MessagingStyle msgStyle = new MessagingStyle(mContext.getString(R.string.person_me));
                    int start = 0;
                    for (NotificationConversation.ConversationMessage message : content) {
                        Contact contact = Contact.findByUserId(mContext, message.peer);
                        name = contact.getDisplayName();

                        msgStyle.addMessage(message.content, message.timestamp, name);

                        if (allContent != null) {
                            if (allContent.length() > 0)
                                allContent.append("\n");

                            start = allContent.length();
                            allContent.append(name).append(':').append(' ');
                            allContent.setSpan(new StyleSpan(Typeface.BOLD), start, start + name.length() + 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            allContent.append(message.toString());
                        }
                    }

                    // group ticker
                    if (conv.groupJid != null) {
                        name = mContext.getResources().getString(R.string.notification_group_title,
                            name, (TextUtils.isEmpty(conv.groupSubject) ?
                                mContext.getString(R.string.group_untitled) : conv.groupSubject));
                        msgStyle.setConversationTitle((TextUtils.isEmpty(conv.groupSubject) ?
                            mContext.getString(R.string.group_untitled) : conv.groupSubject));
                    }

                    style = msgStyle;
                }
                else {
                    NotificationConversation.ConversationMessage message = content.get(content.size()-1);
                    Contact contact = Contact.findByUserId(mContext, message.peer);
                    name = contact.getDisplayName();
                }

                // name now contains data from the latest message

                // add people data
                for (String peer : conv.allPeers) {
                    Contact contact = Contact.findByUserId(mContext, peer);
                    Uri personUri = contact.getUri();
                    if (personUri == null && contact.getNumber() != null) {
                        // no contact uri available, try phone number lookup
                        try {
                            personUri = Uri.parse("tel:" + contact.getNumber());
                        }
                        catch (Exception ignored) {
                        }
                    }
                    if (personUri != null)
                        mBuilder.addPerson(personUri.toString());

                    // avatar (non-group)
                    if (conv.groupJid == null) {
                        Bitmap avatar = contact.getAvatarBitmap(mContext, true);
                        mBuilder.setLargeIcon(avatar);
                    }
                }

                // group avatar and ticker
                if (conv.groupJid != null) {
                    mBuilder.setLargeIcon(MessageUtils
                        .drawableToBitmap(ContextCompat
                            .getDrawable(mContext, R.drawable.ic_default_group)));
                }

                SpannableStringBuilder buf = new SpannableStringBuilder();
                buf.append(name).append(':').append(' ');
                buf.setSpan(new StyleSpan(Typeface.BOLD), 0, buf.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                buf.append(last);

                ticker = buf;

                // title
                title = name;

                // text
                text = (unread > 1) ?
                    mContext.getResources().getQuantityString(R.plurals.unread_messages, unread, unread)
                    : last;

                // mark as read pending intent
                // TODO this should also be used for messages from a single group
                Intent markReadIntent = new Intent(ACTION_NOTIFICATION_MARK_READ,
                    firstThreadUri, mContext, NotificationActionReceiver.class);
                PendingIntent readPendingIntent = PendingIntent.getBroadcast(mContext, 0,
                    markReadIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                if (supportsDirectReply()) {
                    // pending intent will start a broadcast receiver
                    Intent replyIntent = new Intent(ACTION_NOTIFICATION_REPLY, firstThreadUri,
                        mContext, NotificationActionReceiver.class);
                    PendingIntent replyPendingIntent = PendingIntent
                        .getBroadcast(mContext, 0, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                    // reply action
                    mBuilder.addAction(new android.support.v4.app.NotificationCompat
                        .Action.Builder(R.drawable.ic_menu_reply, mContext.getString(R.string.reply), replyPendingIntent)
                        .addRemoteInput(NotificationActionReceiver.buildReplyInput(mContext))
                        .build()
                    );
                }
                else {
                    // pending intent will start a the quick reply dialog
                    long firstThreadId = ContentUris.parseId(firstThreadUri);
                    Intent replyIntent = QuickReplyActivity.getStartIntent(mContext, firstThreadId, allContent);
                    PendingIntent replyPendingIntent = PendingIntent
                        .getActivity(mContext, 0, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                    // reply action
                    mBuilder.addAction(R.drawable.ic_menu_reply, mContext.getString(R.string.reply), replyPendingIntent);
                }

                mBuilder.addAction(R.drawable.ic_menu_check, mContext.getString(R.string.mark_read), readPendingIntent);
            }

            mBuilder.setTicker(ticker);
            mBuilder.setContentTitle(title);
            mBuilder.setContentText(text);
            mBuilder.setStyle(style);
            mBuilder.setNumber(unread);

            return convCount;
        }
    }

    static PendingIntent createPendingIntent(Context context, Intent intent) {
        return TaskStackBuilder.create(context)
            // add all of DetailsActivity's parents to the stack,
            // followed by DetailsActivity itself
            .addNextIntentWithParentStack(intent)
            .getPendingIntent(NOTIFICATION_ID_MESSAGES,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * This class accumulates all incoming unread threads and returns
     * well-formed data to be used in a {@link Notification}.
     * Used only for legacy notifications (pre-JB).
     */
    private static final class MessageAccumulator {
        final class ConversationStub {
            public long id;
            public String peer;
            public String content;
            public String groupJid;
            public String groupSubject;
        }

        private ConversationStub conversation;
        private int convCount;
        int unreadCount;
        private Context mContext;
        private Contact mContact;

        public MessageAccumulator(Context context) {
            mContext = context;
        }

        /** Adds a conversation thread to the accumulator. */
        public void accumulate(long id, String peer, String content, int unread, String groupJid, String groupSubject) {
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
            conversation.groupJid = groupJid;
            conversation.groupSubject = groupSubject;

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
            String peer = (mContact != null) ? mContact.getDisplayName() :
                mContext.getString(R.string.peer_unknown);
                // debug mode -- conversation.peer;

            // append group subject to contact name if any
            if (conversation.groupJid != null) {
                peer = mContext.getResources().getString(R.string.notification_group_title,
                    peer, (TextUtils.isEmpty(conversation.groupSubject) ?
                        mContext.getString(R.string.group_untitled) : conversation.groupSubject));
            }

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
                String peer = (mContact != null) ? mContact.getDisplayName() :
                    mContext.getString(R.string.peer_unknown);
                    // debug mode -- conversation.peer;

                // append group subject to contact name if any
                if (conversation.groupJid != null) {
                    peer = mContext.getResources().getString(R.string.notification_group_title,
                        peer, (TextUtils.isEmpty(conversation.groupSubject) ?
                            mContext.getString(R.string.group_untitled) : conversation.groupSubject));
                }

                return peer;
            }
        }

        /** Returns the text that should be used as the notification text. */
        public String getText() {
            return (unreadCount > 1) ?
                    mContext.getResources().getQuantityString(R.plurals.unread_messages, unreadCount, unreadCount)
                    : conversation.content;
        }

        /** Builds a {@link PendingIntent} to be used in the notification. */
        public PendingIntent getPendingIntent() {
            Intent ni;
            // more than one unread conversation - open ConversationList
            if (convCount > 1) {
                ni = new Intent(mContext, ConversationsActivity.class);
                ni.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            }
            // one unread conversation - open ComposeMessage on that peer
            else {
                ni = ComposeMessage.fromConversation(mContext, conversation.id);
            }
            return createPendingIntent(mContext, ni);
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

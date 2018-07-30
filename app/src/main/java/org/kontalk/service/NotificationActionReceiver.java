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

package org.kontalk.service;

import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.RemoteInput;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.ui.MessagingNotification;


/**
 * Broadcast receiver for notification actions.
 * @author Daniele Ricci
 */
public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String TAG = Kontalk.TAG;

    private static final String KEY_TEXT_REPLY = "key_text_reply";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (MessagingNotification.ACTION_NOTIFICATION_DELETED.equals(action)) {
            // mark threads as old
            Parcelable[] threads = intent.getParcelableArrayExtra("org.kontalk.datalist");
            if (threads == null) {
                Log.w(TAG, "Notification delete action could not be completed because of a firmware bug");
            }
            else {
                for (Parcelable uri : threads)
                    MessagesProviderClient.markThreadAsOld(context, ContentUris.parseId((Uri) uri));
                MessagingNotification.delayedUpdateMessagesNotification(context, false);
            }
        }
        else if (MessagingNotification.ACTION_NOTIFICATION_REPLY.equals(action)) {
            // mark threads as read
            long threadId = ContentUris.parseId(intent.getData());
            MessagesProviderClient.markThreadAsRead(context, threadId);

            // send reply
            Bundle result = RemoteInput.getResultsFromIntent(intent);
            if (result != null) {
                Conversation conv = Conversation.loadFromId(context, threadId);
                CharSequence text = result.getCharSequence(KEY_TEXT_REPLY);
                if (text != null) {
                    Kontalk.get().getMessagesController()
                        .sendTextMessage(conv, text.toString(), 0);
                }
                else {
                    // it shouldn't happen, but you know, Android...
                    Log.w(Kontalk.TAG, "Unable to use direct reply content");
                    ReportingManager.logException(new UnsupportedOperationException
                        ("direct reply content is null"));
                }
            }

            // TODO show notification with the reply for a short time
            // https://developer.android.com/guide/topics/ui/notifiers/notifications.html#direct

            MessagingNotification.delayedUpdateMessagesNotification(context, false);
        }
        else if (MessagingNotification.ACTION_NOTIFICATION_MARK_READ.equals(action)) {
            // mark threads as read
            MessagesProviderClient.markThreadAsRead(context, ContentUris.parseId(intent.getData()));
            MessagingNotification.delayedUpdateMessagesNotification(context, false);
        }
    }

    public static RemoteInput buildReplyInput(Context context) {
        String replyLabel = context.getString(R.string.reply);
        return new RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(replyLabel)
            .build();
    }

}

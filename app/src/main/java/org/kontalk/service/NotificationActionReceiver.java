/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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
import android.os.Parcelable;
import android.util.Log;

import org.kontalk.provider.MessagesProvider;
import org.kontalk.ui.MessagingNotification;


/**
 * Broadcast receiver for notification actions.
 * @author Daniele Ricci
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.v("Kontalk", "got action " + action);

        if (MessagingNotification.ACTION_NOTIFICATION_DELETED.equals(action)) {
            // mark threads as old
            Parcelable[] threads = intent.getParcelableArrayExtra("org.kontalk.datalist");
            for (Parcelable uri : threads)
                MessagesProvider.markThreadAsOld(context, ContentUris.parseId((Uri) uri));
            MessagingNotification.delayedUpdateMessagesNotification(context, false);
        }
        else if (MessagingNotification.ACTION_NOTIFICATION_MARK_READ.equals(action)) {
            // mark threads as read
            MessagesProvider.markThreadAsRead(context, ContentUris.parseId(intent.getData()));
            MessagingNotification.delayedUpdateMessagesNotification(context, false);
        }
    }

}

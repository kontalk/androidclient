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

package org.kontalk.service.gcm;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.Preferences;


/**
 * Intent service for GCM broadcasts.
 * @author Daniele Ricci
 */
public class GcmListenerService extends FirebaseMessagingService {
    private static final String TAG = Kontalk.TAG;

    /** GCM message received from server. */
    private static final String ACTION_CHECK_MESSAGES = "org.kontalk.CHECK_MESSAGES";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String dataAction = remoteMessage.getData().get("action");
        Log.v(TAG, "cloud message received: " + dataAction);

        // new messages - start message center
        if (ACTION_CHECK_MESSAGES.equals(dataAction)) {
            // remember we just received a push notifications
            // this means that there are really messages waiting for us
            Preferences.setLastPushNotification(
                System.currentTimeMillis());

            // test message center connection
            MessageCenterService.test(getApplicationContext());
        }
    }

    @Override
    public void onNewToken(String token) {
        // not really used. Or is it?
    }

}

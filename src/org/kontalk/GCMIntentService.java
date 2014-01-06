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

package org.kontalk;

import org.kontalk.service.MessageCenterService;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;


/**
 * Google Cloud Messaging service.
 * Handles tasks for GCM support.
 * @author Daniele Ricci
 */
public class GCMIntentService extends GCMBaseIntentService {
    private static final String TAG = GCMIntentService.class.getSimpleName();
    private static final String ACTION_CHECK_MESSAGES = "org.kontalk.CHECK_MESSAGES";

    @Override
    protected String[] getSenderIds(Context context) {
        return new String[] { MessageCenterService.getPushSenderId() };
    }

    @Override
    protected void onRegistered(Context context, String registrationId) {
        Log.i(TAG, "registered to GCM - " + registrationId);
        MessageCenterService.registerPushNotifications(context, registrationId);
    }

    @Override
    protected void onUnregistered(Context context, String registrationId) {
        Log.i(TAG, "unregistered from GCM");
        MessageCenterService.registerPushNotifications(context, null);
    }

    @Override
    protected void onError(Context context, String errorId) {
        Log.e(TAG, "error registering to GCM service: " + errorId);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
        String dataAction = intent.getStringExtra("action");
        Log.i(TAG, "cloud message received: " + dataAction);

        // new messages - start message center
        if (ACTION_CHECK_MESSAGES.equals(dataAction))
            MessageCenterService.startMessageCenter(context.getApplicationContext());
    }

}

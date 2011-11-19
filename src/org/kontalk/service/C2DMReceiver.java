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

package org.kontalk.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


/**
 * Cloud-to-device messaging receiver.
 * @author Daniele Ricci
 */
public class C2DMReceiver extends BroadcastReceiver {
    private static final String TAG = C2DMReceiver.class.getSimpleName();

    private static final String ACTION_CHECK_MESSAGES = "org.kontalk.CHECK_MESSAGES";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.google.android.c2dm.intent.REGISTRATION".equals(action)) {
            // notify c2dm registration to message center
            String registration = intent.getStringExtra("registration_id");
            if (registration != null) {
                Log.i(TAG, "registered to C2DM - " + registration);
            }
            else if (intent.getStringExtra("unregistered") != null) {
                Log.i(TAG, "unregistered to C2DM");
            }
            else {
                String err = intent.getStringExtra("error");
                Log.e(TAG, "error registering to C2DM service: " + err);
            }

            MessageCenterService.registerPushNotifications(context, registration);
        }
        else if ("com.google.android.c2dm.intent.RECEIVE".equals(action)) {
            // process push message
            Log.i("TAG", "cloud message received! " + intent);

            String dataAction = intent.getStringExtra("action");
            Log.i("TAG", "cloud message action: " + dataAction);

            if (ACTION_CHECK_MESSAGES.equals(dataAction)) {
                MessageCenterService.startMessageCenter(context.getApplicationContext());
            }
        }
    }

}

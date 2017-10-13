/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import org.kontalk.Kontalk;
import org.kontalk.service.msgcenter.IPushListener;
import org.kontalk.service.msgcenter.MessageCenterService;

import android.content.Context;
import org.kontalk.Log;


/**
 * GCM listener for the Message Center.
 * @author Daniele Ricci
 */
public class DefaultGcmListener implements IPushListener {
    private static final String TAG = Kontalk.TAG;

    @Override
    public void onRegistered(Context context, String registrationId) {
        Log.d(TAG, "registered to GCM - " + registrationId);
        MessageCenterService.registerPushNotifications(context, registrationId);
    }

    @Override
    public void onUnregistered(Context context) {
        Log.d(TAG, "unregistered from GCM");
        MessageCenterService.registerPushNotifications(context, null);
    }

    @Override
    public void onError(Context context, String errorId) {
        Log.w(TAG, "error registering to GCM service: " + errorId);
    }

    @Override
    public String getSenderId(Context context) {
        return MessageCenterService.getPushSenderId();
    }

}

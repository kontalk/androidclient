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

package org.kontalk;

import org.kontalk.provider.MyUsers.Users;
import org.kontalk.service.MessageCenterService;
import org.kontalk.ui.MessagingNotification;

import android.app.Application;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;


/**
 * The Application.
 * @author Daniele Ricci

 */
public class Kontalk extends Application {
    private static final String TAG = Kontalk.class.getSimpleName();

    /** Supported client protocol revision. */
    public static final int CLIENT_PROTOCOL = 3;

    @Override
    public void onCreate() {
        super.onCreate();

        MessageCenterService.startMessageCenter(this);
        MessagingNotification.updateMessagesNotification(this, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if ("pref_network_uri".equals(key)) {
                    // just restart the message center for now
                    Log.w(TAG, "network address changed");
                    MessageCenterService.restartMessageCenter(Kontalk.this);
                }
            }
        });

        // TODO listen for changes to phone numbers

        /*
         * should we request a manual sync the first time?
         * Technically, a sync would be possible only *after* account creation,
         * so let's just do a UsersProvider.resync here - with bootstrap param
         */
        new Thread(new Runnable() {
            @Override
            public void run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                Uri uri = Users.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Users.RESYNC, "true")
                    .appendQueryParameter(Users.BOOTSTRAP, "true")
                    .build();
                getContentResolver().update(uri, new ContentValues(), null, null);
            }
        }).start();
    }
}

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

import org.kontalk.service.MessageCenterServiceLegacy;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.MessagingNotification;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


/**
 * The Application.
 * @author Daniele Ricci

 */
public class Kontalk extends Application {
    private static final String TAG = Kontalk.class.getSimpleName();

    /** Supported client protocol revision. */
    public static final int CLIENT_PROTOCOL = 4;

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefChangedListener;

    @Override
    public void onCreate() {
        super.onCreate();

        // update notifications from locally unread messages
        MessagingNotification.updateMessagesNotification(this, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                // manual server address
                if ("pref_network_uri".equals(key)) {
                    // just restart the message center for now
                    android.util.Log.w(TAG, "network address changed");
                    MessageCenterServiceLegacy.restartMessageCenter(Kontalk.this);
                }

                // hide presence flag / encrypt user data flag
                else if ("pref_hide_presence".equals(key) || "pref_encrypt_userdata".equals(key)) {
                    MessageCenterServiceLegacy.updateStatus(Kontalk.this);
                }

                // changing remove prefix
                else if ("pref_remove_prefix".equals(key)) {
                    SyncAdapter.requestSync(getApplicationContext(), true);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(mPrefChangedListener);

        // TODO listen for changes to phone numbers
    }
}

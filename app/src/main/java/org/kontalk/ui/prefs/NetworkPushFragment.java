/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui.prefs;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import org.kontalk.R;
import org.kontalk.service.msgcenter.IPushService;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.PushServiceManager;


/**
 * Push notifications settings fragment.
 */
public class NetworkPushFragment extends RootPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences_network_push);

        // push notifications checkbox
        final Preference pushNotifications = findPreference("pref_push_notifications");
        pushNotifications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context ctx = getActivity();
                CheckBoxPreference pref = (CheckBoxPreference) preference;
                if (pref.isChecked())
                    MessageCenterService.enablePushNotifications(ctx.getApplicationContext());
                else
                    MessageCenterService.disablePushNotifications(ctx.getApplicationContext());

                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((PreferencesActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_push_notifications);
    }

    @Override
    protected void setupPreferences() {
        final Preference description = findPreference("pref_push_notifications_title");

        IPushService service = PushServiceManager.getInstance(getContext());
        if (service == null) {
            description.setTitle(R.string.pref_push_notifications_title_disabled);
        }
        else if (!service.isServiceAvailable()) {
            description.setTitle(R.string.pref_push_notifications_title_unavailable);
        }
    }

}

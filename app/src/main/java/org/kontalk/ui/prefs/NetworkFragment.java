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

import com.afollestad.materialdialogs.MaterialDialog;

import android.os.Bundle;

import androidx.preference.Preference;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;


/**
 * Network settings fragment.
 */
public class NetworkFragment extends RootPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_network);

        // manual server address is handled in Application context
        // we just handle validation here
        final Preference manualServer = findPreference("pref_network_uri");
        manualServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String value = newValue.toString().trim();
                if (value.length() > 0 && !EndpointServer.validate(value)) {
                    new MaterialDialog.Builder(getActivity())
                        .title(R.string.pref_network_uri)
                        .content(R.string.err_server_invalid_format)
                        .positiveText(android.R.string.ok)
                        .show();
                    return false;
                }
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((PreferencesActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_network_settings);

        final Preference pushNotifications = findPreference("pref_push_notifications_parent");
        PushNotificationsPreference.setState(pushNotifications);
    }

    @Override
    protected void setupPreferences() {
        setupPreferences("pref_push_notifications_parent", R.xml.preferences_network_push);
    }

    private void setupPreferences(String pref, final int xml) {
        final Preference preference = findPreference(pref);
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                invokeCallback(xml);
                return true;
            }
        });
    }

}

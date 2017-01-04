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

package org.kontalk.ui.prefs;

import android.os.Bundle;
import android.preference.Preference;

import org.kontalk.R;

/**
 * Messaging settings fragment.
 */
public class MessagingFragment extends RootPreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_messaging);
    }

    @Override
    protected void setupPreferences() {
        // privacy section
        final Preference privacy = findPreference("pref_privacy_settings");
        privacy.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                invokeCallback(R.xml.privacy_preferences);
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        ((PreferencesActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_messaging_settings);
    }
}

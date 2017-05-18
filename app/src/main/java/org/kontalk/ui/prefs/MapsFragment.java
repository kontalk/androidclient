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
import android.preference.ListPreference;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.position.PositionManager;

/**
 * @author andreacappelli
 */

public class MapsFragment extends RootPreferenceFragment {
    static final String TAG = Kontalk.TAG;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_maps);

        final ListPreference serviceProvider = (ListPreference) findPreference("pref_maps_service");

        if (serviceProvider.getValue() == null)
            serviceProvider.setValue(PositionManager.getDefaultMapsProvider(getActivity()));
    }

    @Override
    public void onResume() {
        super.onResume();

        ((PreferencesActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_maps_settings);
    }
}

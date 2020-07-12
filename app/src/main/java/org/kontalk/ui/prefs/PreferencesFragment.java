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

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;
import org.kontalk.R;


/**
 * Preferences overview fragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public final class PreferencesFragment extends RootPreferenceFragment {
    static final String TAG = Kontalk.TAG;

    @SuppressLint("ApplySharedPref")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // upgrade from old version: pref_text_enter becomes string
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        try {
            prefs.getString("pref_text_enter", null);
        }
        catch (ClassCastException e) {
            // legacy mode
            prefs.edit()
                .putString("pref_text_enter",
                    prefs.getBoolean("pref_text_enter", false) ?
                        "newline" : "default")
                .commit();
        }

        addPreferencesFromResource(R.xml.preferences);

        // remove reporting preference in debug builds
        if (BuildConfig.DEBUG) {
            Preference prefReporting = findPreference("pref_reporting");
            if (prefReporting != null)
                getPreferenceScreen().removePreference(prefReporting);
        }

        if (Kontalk.get().getDefaultAccount() == null) {
            // no account, hide some stuff
            Preference prefAccount = findPreference("pref_account_settings");
            getPreferenceScreen().removePreference(prefAccount);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ((PreferencesActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.menu_settings);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // don't use up navigation here
                getActivity().finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void setupPreferences() {
        setupPreferences("pref_account_settings", R.xml.preferences_account);
        setupPreferences("pref_network_settings", R.xml.preferences_network);
        setupPreferences("pref_messaging_settings", R.xml.preferences_messaging);
        setupPreferences("pref_appearance_settings", R.xml.preferences_appearance);
        setupPreferences("pref_media_settings", R.xml.preferences_media);
        setupPreferences("pref_location_settings", R.xml.preferences_location);
        setupPreferences("pref_notification_settings", R.xml.preferences_notification);
        setupPreferences("pref_maintenance_settings", R.xml.preferences_maintenance);
    }

    private void setupPreferences(String pref, final int xml) {
        final Preference preference = findPreference(pref);
        if (preference != null) {
            // null preference means it's been removed
            preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    invokeCallback(xml);
                    return true;
                }
            });
        }
    }

}

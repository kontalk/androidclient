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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import com.afollestad.materialdialogs.color.ColorChooserDialog;

import org.kontalk.R;
import org.kontalk.ui.ToolbarActivity;
import org.kontalk.util.Preferences;


/**
 * Notification settings fragment
 */
public class NotificationFragment extends RootPreferenceFragment {

    private static final int REQUEST_PICK_RINGTONE = Activity.RESULT_FIRST_USER + 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_notification);

        // set ringtone
        final Preference setRingtone = findPreference("pref_ringtone");
        setRingtone.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext());

                String _currentRingtone = prefs.getString(preference.getKey(),
                        getString(R.string.pref_default_ringtone));
                Uri currentRingtone = !TextUtils.isEmpty(_currentRingtone) ? Uri.parse(_currentRingtone) : null;

                final Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtone);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, preference.getTitle());

                startActivityForResult(i, REQUEST_PICK_RINGTONE);
                return true;
            }
        });

        // notification LED color
        final Preference notificationLed = findPreference("pref_notification_led_color");
        notificationLed.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context context = getContext();
                int[] ledColors = new int[]{
                    ContextCompat.getColor(context, android.R.color.white),
                    ContextCompat.getColor(context, R.color.blue_light),
                    ContextCompat.getColor(context, R.color.purple_light),
                    ContextCompat.getColor(context, R.color.green_light),
                    ContextCompat.getColor(context, R.color.yellow_light),
                    ContextCompat.getColor(context, R.color.red_light),
                };

                try {
                    new ColorChooserDialog.Builder((BasePreferencesActivity) getActivity(),
                        R.string.pref_notification_led_color)
                        .customColors(ledColors, null)
                        .preselect(Preferences.getNotificationLEDColor(getContext()))
                        .allowUserColorInput(false)
                        .dynamicButtonColor(false)
                        .show(getFragmentManager());
                }
                catch (IllegalStateException e) {
                    // fragment is being destroyed - ignore
                }
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        ((ToolbarActivity) getActivity())
            .getSupportActionBar()
            .setTitle(R.string.pref_notification_settings);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_RINGTONE) {
            if (resultCode == Activity.RESULT_OK) {
                Context ctx = getActivity();
                if (ctx != null) {
                    Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    Preferences.setRingtone(uri != null ? uri.toString() : "");
                }
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

}

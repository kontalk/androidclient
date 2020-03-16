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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import android.util.AttributeSet;
import android.view.View;

import org.kontalk.R;
import org.kontalk.service.msgcenter.IPushService;
import org.kontalk.service.msgcenter.PushServiceManager;
import org.kontalk.util.Preferences;


/**
 * Preference for push notifications.
 * @author Daniele Ricci
 */
public class PushNotificationsPreference extends CheckBoxPreference implements View.OnClickListener {

    public PushNotificationsPreference(Context context) {
        super(context);
        init();
    }

    public PushNotificationsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PushNotificationsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public PushNotificationsPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.preference_switch_layout);

        // disable and uncheck preference
        IPushService service = PushServiceManager.getInstance(getContext());
        if (service == null || !service.isServiceAvailable()) {
            setEnabled(false);
            setChecked(false);
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View checkbox = holder.findViewById(android.R.id.checkbox);
        checkbox.setOnClickListener(this);
    }

    public void onClick(@SuppressWarnings("UnusedParameters") View view) {
        super.onClick();
        OnPreferenceClickListener listener = super.getOnPreferenceClickListener();
        if (listener != null)
            listener.onPreferenceClick(this);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        setTitle(isChecked() ? R.string.pref_on : R.string.pref_off);
    }

    public static void setState(Preference parent) {
        boolean enabled = Preferences.getPushNotificationsEnabled(parent.getContext());
        IPushService service = PushServiceManager.getInstance(parent.getContext());
        if (service == null) {
            parent.setSummary(R.string.pref_push_notifications_disabled);
        }
        else if (service.isServiceAvailable()) {
            parent.setSummary(enabled ? R.string.pref_on : R.string.pref_off);
        }
    }

}

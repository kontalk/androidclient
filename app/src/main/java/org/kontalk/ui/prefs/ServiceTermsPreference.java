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
import androidx.preference.Preference;
import android.util.AttributeSet;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.util.SystemUtils;


/**
 * Preference for the service terms link.
 * @author Daniele Ricci
 */
public class ServiceTermsPreference extends Preference {

    private String mServiceTermsUrl;

    public ServiceTermsPreference(Context context) {
        super(context);
        init();
    }

    public ServiceTermsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ServiceTermsPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ServiceTermsPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        mServiceTermsUrl = Authenticator.getDefaultServiceTermsURL(getContext());
        setEnabled(mServiceTermsUrl != null);

        if (mServiceTermsUrl != null) {
            setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    openServiceTerms(getContext());
                    return true;
                }
            });
        }
    }

    private void openServiceTerms(Context context) {
        SystemUtils.openURL(context, mServiceTermsUrl);
    }

}

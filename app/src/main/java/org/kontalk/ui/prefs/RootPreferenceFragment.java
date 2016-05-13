/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import com.github.machinarius.preferencefragment.PreferenceFragment;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.view.MenuItem;

import org.kontalk.R;
import org.kontalk.util.SystemUtils;


/**
 * Base class for root preference fragments.
 * @author Daniele Ricci
 */
public abstract class RootPreferenceFragment extends PreferenceFragment {

    private Callback mCallback;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setupPreferences();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Callback) {
            mCallback = (Callback) context;
        }
        else {
            throw new IllegalStateException("Owner must implement Callback interface");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().onBackPressed();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void invokeCallback(int key) {
        if (mCallback != null)
            mCallback.onNestedPreferenceSelected(key);
    }

    protected void setupPreferences() {
    }

    // FIXME this is used only for root preference fragments
    protected void setupInternalPreferences() {
        final Preference help = findPreference("pref_help");
        help.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SystemUtils.openURL(getContext(), getString(R.string.help_url));
                return true;
            }
        });

    }

    public interface Callback {
        void onNestedPreferenceSelected(int key);
    }

}

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
import android.view.MenuItem;

import androidx.preference.PreferenceFragmentCompat;


/**
 * Base class for root preference fragments.
 * @author Daniele Ricci
 */
public abstract class RootPreferenceFragment extends PreferenceFragmentCompat {

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
        // with the exception of the notification and account preferences activities...
        else if (!(context instanceof NotificationPreferencesActivity) &&
            !(context instanceof AccountPreferencesActivity)) {
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

    public interface Callback {
        void onNestedPreferenceSelected(int key);
    }

}

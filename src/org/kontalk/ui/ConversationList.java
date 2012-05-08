/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Window;
import android.widget.ListAdapter;


/**
 * The conversations list activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ConversationList extends FragmentActivity {
    //private static final String TAG = ConversationList.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.conversation_list_screen);
    }

    /** Called when a new intent is sent to the activity (if already started). */
    @Override
    protected void onNewIntent(Intent intent) {
        ConversationListFragment fragment = (ConversationListFragment)
            getSupportFragmentManager().
            findFragmentById(R.id.fragment_conversation_list);
        fragment.startQuery();
    }

    @Override
    public boolean onSearchRequested() {
        ConversationListFragment fragment = (ConversationListFragment)
            getSupportFragmentManager().
            findFragmentById(R.id.fragment_conversation_list);

        ListAdapter list = fragment.getListAdapter();
        // no data found
        if (list == null || list.getCount() == 0)
            return false;

        startSearch(null, false, null, false);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.startValidation(this);
            finish();
        }
    }

    public ConversationListFragment getListFragment() {
        return (ConversationListFragment)
                getSupportFragmentManager().
                findFragmentById(R.id.fragment_conversation_list);
    }

}

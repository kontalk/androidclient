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

package org.kontalk.xmpp.ui;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.authenticator.Authenticator;
import org.kontalk.xmpp.data.Contact;
import org.kontalk.xmpp.sync.SyncAdapter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ListAdapter;


/**
 * The conversations list activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ConversationList extends ActionBarActivity implements ContactsSyncActivity {
    //private static final String TAG = ConversationList.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_list_screen);

        checkBigUpgrade1();
    }

    public void titleComposeMessage(View view) {
        getListFragment().chooseContact();
    }

    public void titleSearch(View view) {
        onSearchRequested();
    }

    /**
     * Checks for the first big database upgrade - manually triggering a sync
     * if necessary.
     */
    private void checkBigUpgrade1() {
        Boolean oldSync = (Boolean) getLastCustomNonConfigurationInstance();
        if (!MessagingPreferences.getBigUpgrade1(this) || (oldSync != null && oldSync.booleanValue())) {
            SyncAdapter.requestSync(getApplicationContext(), true);
            // TODO we need to requery the list when sync has finished
        }
    }

    /** Called when a new intent is sent to the activity (if already started). */
    @Override
    protected void onNewIntent(Intent intent) {
        ConversationListFragment fragment = getListFragment();
        fragment.startQuery();
    }

    @Override
    public boolean onSearchRequested() {
        ConversationListFragment fragment = getListFragment();

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

    public boolean isDualPane() {
        return findViewById(R.id.fragment_compose_message) != null;
    }

    /** Called when a contact has been selected from a {@link ContactsListFragment}. */
    protected void onNewContactSelected(Fragment fragment, Contact cl) {
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.remove(fragment);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        ft.commit();

        // TODO handle pick
        /*
        Intent i = new Intent(Intent.ACTION_PICK, Threads.getUri(cl.getContact().getHash()));
        setResult(RESULT_OK, i);
        finish();
        */
    }

    @Override
    public void setSyncing(boolean syncing) {
        // TODO
    }

}

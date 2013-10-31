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
import org.kontalk.xmpp.data.Conversation;
import org.kontalk.xmpp.provider.MyMessages.Threads;
import org.kontalk.xmpp.sync.SyncAdapter;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.ListAdapter;
import android.widget.Toast;


/**
 * The conversations list activity.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ConversationList extends ActionBarActivity
        implements ContactsSyncActivity, ContactPickerListener {
    //private static final String TAG = ConversationList.class.getSimpleName();

    ConversationListFragment mFragment;

    private static final int REQUEST_CONTACT_PICKER = 7720;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversation_list_screen);

        mFragment = (ConversationListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_conversation_list);

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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // contact chooser
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null)
                    openConversation(uri);
            }
        }
    }

    public ConversationListFragment getListFragment() {
        return mFragment;
    }

    public boolean isDualPane() {
        return findViewById(R.id.fragment_compose_message) != null;
    }

    /** Called when a contact has been selected from a {@link ContactsListFragment}. */
    @Override
    public void onContactSelected(ContactsListFragment fragment, Contact contact) {
        // remove contact picker fragment
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.remove(fragment);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        ft.commit();

        // open by user hash
        openConversation(Threads.getUri(contact.getHash()));
    }

    public void showContactPicker() {
        if (isDualPane()) {
            // TODO fragment
        }
        else {
            // TODO one day it will be like this
            // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
            Intent i = new Intent(this, ContactsListActivity.class);
            startActivityForResult(i, REQUEST_CONTACT_PICKER);
        }
    }

    @Override
    public void setSyncing(boolean syncing) {
        // TODO
    }

    public void openConversation(Conversation conv, int position) {
        if (isDualPane()) {
            mFragment.getListView().setItemChecked(position, true);

            // get the old fragment
            ComposeMessageFragment f = (ComposeMessageFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_compose_message);

            // check if we are replacing the same fragment
            if (f == null || !f.getConversation().getRecipient().equals(conv.getRecipient())) {
                f = ComposeMessageFragment.fromConversation(this, conv);
                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.addToBackStack(null);
                ft.commit();
            }
        }
        else {
            Intent i = ComposeMessage.fromConversation(this, conv);
            startActivity(i);
        }
    }

    private void openConversation(Uri threadUri) {
        if (isDualPane()) {
            // TODO position
            //getListView().setItemChecked(position, true);

            // load conversation
            String userId = threadUri.getLastPathSegment();
            Conversation conv = Conversation.loadFromUserId(this, userId);

            // get the old fragment
            ComposeMessageFragment f = (ComposeMessageFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_compose_message);

            // check if we are replacing the same fragment
            if (f == null || conv == null || !f.getConversation().getRecipient().equals(conv.getRecipient())) {
                if (conv == null)
                    f = ComposeMessageFragment.fromUserId(this, userId);
                else
                    f = ComposeMessageFragment.fromConversation(this, conv);

                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                ft.addToBackStack(null);
                ft.commitAllowingStateLoss();
            }
        }
        else {
            Intent i = ComposeMessage.fromUserId(this, threadUri.getLastPathSegment());
            if (i != null)
                startActivity(i);
            else
                Toast.makeText(this, R.string.contact_not_registered, Toast.LENGTH_LONG)
                    .show();
        }
    }

}

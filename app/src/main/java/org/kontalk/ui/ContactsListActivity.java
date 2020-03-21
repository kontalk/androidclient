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

package org.kontalk.ui;

import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.view.ContactPickerListener;
import org.kontalk.util.Permissions;
import org.kontalk.util.Preferences;


public class ContactsListActivity extends ToolbarActivity
        implements ContactPickerListener {

    public static final String TAG = ContactsListActivity.class.getSimpleName();

    public static final String MODE_MULTI_SELECT = "org.kontalk.contacts.MULTI_SELECT";
    public static final String MODE_ADD_USERS = "org.kontalk.contacts.ADD_USERS";
    public static final String MODE_RECENTS = "org.kontalk.contacts.RECENTS";

    private static final int VIEW_MODE_CONTACTS = 0;
    private static final int VIEW_MODE_RECENTS = 1;

    private ContactsListFragment mFragment;

    private MenuItem mSwitchContacts;
    private MenuItem mSwitchRecents;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.contacts_list_screen);

        setupToolbar(true, true);

        boolean multiselect = getIntent().getBooleanExtra(MODE_MULTI_SELECT, false);
        if (multiselect) {
            boolean addUsers = getIntent().getBooleanExtra(MODE_ADD_USERS, false);
            // FIXME using another string
            setTitle(addUsers ? R.string.menu_invite_group : R.string.action_compose_group);
        }

        boolean recents = getIntent().getBooleanExtra(MODE_RECENTS, false);
        if (recents) {
            setTitle(R.string.contacts_list_recents_title);
        }
        else {
            setTitle(R.string.contacts_list_title);
        }

        if (savedInstanceState == null) {
            mFragment = ContactsListFragment.newInstance(multiselect, recents);
            getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_contacts_list, mFragment)
                .commitAllowingStateLoss();
        }
        else {
            mFragment = (ContactsListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_contacts_list);
        }

        if (!getIntent().getBooleanExtra("picker", false))
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (!Preferences.getContactsListVisited())
            Toast.makeText(this, R.string.msg_do_refresh,
                    Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isRecentsMode()) {
            getMenuInflater().inflate(R.menu.contacts_list_parent_menu, menu);
            mSwitchContacts = menu.findItem(R.id.menu_switch_contacts);
            mSwitchRecents = menu.findItem(R.id.menu_switch_recents);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isRecentsMode()) {
            int viewMode = getCurrentViewMode();
            mSwitchContacts.setVisible(viewMode == VIEW_MODE_RECENTS);
            mSwitchRecents.setVisible(viewMode == VIEW_MODE_CONTACTS);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_switch_contacts:
                switchToContacts();
                return true;

            case R.id.menu_switch_recents:
                switchToRecents();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void switchToContacts() {
        setTitle(R.string.contacts_list_title);
        replaceView(false);
    }

    private void switchToRecents() {
        setTitle(R.string.contacts_list_recents_title);
        replaceView(true);
    }

    private void replaceView(boolean recents) {
        boolean multiselect = getIntent().getBooleanExtra(MODE_MULTI_SELECT, false);
        mFragment = ContactsListFragment.newInstance(multiselect, recents);
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_contacts_list, mFragment)
            .commitAllowingStateLoss();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!Permissions.canReadContacts(this)) {
            Toast.makeText(this, R.string.warn_contacts_denied, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release message center
        MessageCenterService.release(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.start(this);
            finish();
            return;
        }

        // hold message center
        MessageCenterService.hold(this, true);

        mFragment.startQuery();
    }

    private boolean isRecentsMode() {
        return getIntent().getBooleanExtra(MODE_RECENTS, false);
    }

    private int getCurrentViewMode() {
        return mFragment.isRecentsMode() ? VIEW_MODE_RECENTS : VIEW_MODE_CONTACTS;
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return false;
    }

    /** Called when a contact has been selected from a {@link ContactsListFragment}. */
    @Override
    public void onContactSelected(ContactsListFragment fragment, Contact contact) {
        Intent i = new Intent(Intent.ACTION_PICK, Threads.getUri(contact.getJID()));
        setResult(RESULT_OK, i);
        finish();
    }

    @Override
    public void onContactsSelected(ContactsListFragment fragment, List<Contact> contacts) {
        Intent i = new Intent(Intent.ACTION_PICK);
        ArrayList<Uri> uris = new ArrayList<>(contacts.size());
        for (Contact contact : contacts)
            uris.add(Threads.getUri(contact.getJID()));
        i.putParcelableArrayListExtra("org.kontalk.contacts", uris);
        setResult(RESULT_OK, i);
        finish();
    }

}

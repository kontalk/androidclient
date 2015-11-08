/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.view.ContactPickerListener;
import org.kontalk.util.Preferences;


public class ContactsListActivity extends ToolbarActivity
        implements ContactPickerListener {

    public static final String TAG = ContactsListActivity.class.getSimpleName();

    private ContactsListFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.contacts_list_screen);

        setupToolbar(true);

        mFragment = (ContactsListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_contacts_list);

        if (!getIntent().getBooleanExtra("picker", false))
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (!Preferences.getContactsListVisited(this))
            Toast.makeText(this, R.string.msg_do_refresh,
                    Toast.LENGTH_LONG).show();
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
        MessageCenterService.hold(this);

        mFragment.startQuery();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                startActivity(new Intent(this, ConversationsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Called when a contact has been selected from a {@link ContactsListFragment}. */
    @Override
    public void onContactSelected(ContactsListFragment fragment, Contact contact) {
        Intent i = new Intent(Intent.ACTION_PICK, Threads.getUri(contact.getJID()));
        setResult(RESULT_OK, i);
        finish();
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import org.kontalk.data.Contact;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.MessageCenterService;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.util.Preferences;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;


public class ContactsListActivity extends ActionBarActivity
        implements ContactsSyncActivity, ContactPickerListener {

    private MenuItem mSyncButton;

    private ContactsListFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.contacts_list_screen);

        //setSupportProgressBarIndeterminate(true);
        // HACK this is for crappy honeycomb :)
        setSupportProgressBarIndeterminateVisibility(false);

        mFragment = (ContactsListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_contacts_list);

        if (!getIntent().getBooleanExtra("picker", false))
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (!Preferences.getContactsListVisited(this))
            Toast.makeText(this, R.string.msg_do_refresh,
                    Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // hold message center
        MessageCenterService.hold(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // release message center
        MessageCenterService.release(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.startValidation(this);
            finish();
            return;
        }

        mFragment.startQuery();
    }

    @Override
    public synchronized boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contacts_list_menu, menu);
        mSyncButton = menu.findItem(R.id.menu_refresh);
        MenuItemCompat.setShowAsAction(mSyncButton, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        mSyncButton.setVisible(!SyncAdapter.isActive(this));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                startActivity(new Intent(this, ConversationList.class));
                return true;

            case R.id.menu_refresh:
                startSync(true);
                return true;

            case R.id.menu_invite:
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(TextComponent.MIME_TYPE);
                i.putExtra(Intent.EXTRA_TEXT, getString(R.string.text_invite_message));
                startActivity(i);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /** Called when a contact has been selected from a {@link ContactsListFragment}. */
    @Override
    public void onContactSelected(ContactsListFragment fragment, Contact contact) {
        Intent i = new Intent(Intent.ACTION_PICK, Threads.getUri(contact.getHash()));
        setResult(RESULT_OK, i);
        finish();
    }

    private void startSync(boolean errorWarning) {
        if (MessageCenterService.isNetworkConnectionAvailable(this)) {
            if (SyncAdapter.requestSync(this, true))
                setSyncing(true);
        }
        else if (errorWarning) {
            Toast.makeText(this, R.string.err_sync_nonetwork, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void setSyncing(boolean syncing) {
        if (mSyncButton != null)
            mSyncButton.setVisible(!syncing);
        setSupportProgressBarIndeterminateVisibility(syncing);
    }

}

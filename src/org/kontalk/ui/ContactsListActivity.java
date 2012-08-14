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
import org.kontalk.data.Contact;
import org.kontalk.message.PlainTextMessage;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.MessageCenterService;
import org.kontalk.util.SyncerUI;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;


public class ContactsListActivity extends SherlockListActivity
        implements ContactsListAdapter.OnContentChangedListener {

    private Cursor mCursor;
    private ContactsListAdapter mListAdapter;
    private MenuItem mSyncButton;

    private final Runnable mPostSyncAction = new Runnable() {
        public void run() {
            startQuery();
            setSyncing(false);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.contacts_list);

        setSupportProgressBarIndeterminate(true);
        // HACK this is for crappy honeycomb :)
        setSupportProgressBarIndeterminateVisibility(false);

        TextView text = (TextView) findViewById(android.R.id.empty);
        text.setText(Html.fromHtml(getString(R.string.text_contacts_empty)));

        mListAdapter = new ContactsListAdapter(this, getListView());
        mListAdapter.setOnContentChangedListener(this);
        setListAdapter(mListAdapter);

        if (!getIntent().getBooleanExtra("picker", false))
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (!MessagingPreferences.getContactsListVisited(this))
            Toast.makeText(this, R.string.msg_do_refresh,
                    Toast.LENGTH_LONG).show();

        // retain current sync state to hide the refresh button and start indeterminate progress
        if (SyncerUI.retainIfRunning(this, mPostSyncAction, false))
            setSyncing(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // hold message center
        MessageCenterService.holdMessageCenter(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mListAdapter.changeCursor(null);
        try {
            // make sure the cursor is really closed
            mCursor.close();
        }
        catch (Exception e) {
            // ignored
        }

        // cancel any ongoing sync
        SyncerUI.cancel(true);
        // release message center
        MessageCenterService.releaseMessageCenter(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.startValidation(this);
            finish();
            return;
        }

        startQuery();
    }

    @Override
    public synchronized boolean onCreateOptionsMenu(Menu menu) {
        getSupportMenuInflater().inflate(R.menu.contacts_list_menu, menu);
        mSyncButton = menu.findItem(R.id.menu_refresh);
        mSyncButton.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        mSyncButton.setVisible(!SyncerUI.isRunning());
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
                i.setType(PlainTextMessage.MIME_TYPE);
                i.putExtra(Intent.EXTRA_TEXT, getString(R.string.text_invite_message));
                startActivity(i);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ContactsListItem cl = (ContactsListItem) v;
        Intent i = new Intent(Intent.ACTION_PICK, Threads.getUri(cl.getContact().getHash()));
        setResult(RESULT_OK, i);
        finish();
    }

    private void startSync(boolean errorWarning) {
        if (MessageCenterService.isNetworkConnectionAvailable(this)) {
            setSyncing(true);
            SyncerUI.execute(this, mPostSyncAction, false);
        }
        else if (errorWarning) {
            Toast.makeText(this, R.string.err_sync_nonetwork, Toast.LENGTH_LONG).show();
        }
    }

    private void setSyncing(boolean syncing) {
        if (mSyncButton != null)
            mSyncButton.setVisible(!syncing);
        setSupportProgressBarIndeterminateVisibility(syncing);
    }

    private void startQuery() {
        mCursor = Contact.queryContacts(this);
        mListAdapter.changeCursor(mCursor);
    }

    @Override
    public void onContentChanged(ContactsListAdapter adapter) {
        startQuery();
    }

}

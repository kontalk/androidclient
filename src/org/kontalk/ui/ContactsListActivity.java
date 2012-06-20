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

import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class ContactsListActivity extends ListActivity
        implements ContactsListAdapter.OnContentChangedListener {

    private Cursor mCursor;
    private ContactsListAdapter mListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contacts_list);
        TextView text = (TextView) findViewById(android.R.id.empty);
        text.setText(Html.fromHtml(getString(R.string.text_contacts_empty)));

        mListAdapter = new ContactsListAdapter(this);
        mListAdapter.setOnContentChangedListener(this);
        setListAdapter(mListAdapter);

        if (!MessagingPreferences.getContactsListVisited(this))
            Toast.makeText(this, R.string.msg_do_refresh,
                    Toast.LENGTH_LONG).show();
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contacts_list_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                startSync();
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

    private void startSync() {
        Runnable action = new Runnable() {
            public void run() {
                startQuery();
            }
        };

        SyncerUI.execute(this, action, true);
    }

    private void startQuery() {
        // destroy previous cursor
        if (mCursor != null) {
            stopManagingCursor(mCursor);
            mCursor.close();
        }

        mCursor = Contact.queryContacts(this);
        startManagingCursor(mCursor);
        mListAdapter.changeCursor(mCursor);
    }

    @Override
    public void onContentChanged(ContactsListAdapter adapter) {
        startQuery();
    }

}

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
import org.kontalk.sync.SyncAdapter;

import android.accounts.Account;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.view.View;
import android.widget.ListView;


public class ContactsListActivity extends ListActivity {

    private Cursor mCursor;
    private ContactsListAdapter mListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.contacts_list);

        Account account = Authenticator.getDefaultAccount(this);
        Uri uri = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            .build();
        mCursor = getContentResolver().query(uri, null, null, null, SyncAdapter.RAW_COLUMN_DISPLAY_NAME);
        startManagingCursor(mCursor);

        mListAdapter = new ContactsListAdapter(this, mCursor);
        // TODO mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ContactsListItem cl = (ContactsListItem) v;
        Intent i = new Intent(Intent.ACTION_PICK, cl.getContact().getRawContactUri());
        setResult(RESULT_OK, i);
        finish();
    }

}

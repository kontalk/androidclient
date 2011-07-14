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
            /*getContentResolver().query(ContactsContract.Data.CONTENT_URI, null,
                Contacts.Data.MIMETYPE + " = ? AND " + SyncAdapter.DATA_COLUMN_ACCOUNT_NAME + " = ?",
                new String[] { Users.CONTENT_ITEM_TYPE, account.name }, null);*/
        startManagingCursor(mCursor);

        mListAdapter = new ContactsListAdapter(this, mCursor);
        //mListAdapter.setOnContentChangedListener(mContentChangedListener);
        setListAdapter(mListAdapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        ContactsListItem cl = (ContactsListItem) v;
        Intent i = new Intent(Intent.ACTION_PICK, cl.getContact().getUri());
        setResult(RESULT_OK, i);
        finish();
    }

}

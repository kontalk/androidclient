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
import org.kontalk.message.PlainTextMessage;
import org.kontalk.sync.Syncer;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentProviderClient;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class ContactsListActivity extends ListActivity
        implements ContactsListAdapter.OnContentChangedListener {

    private static final String TAG = "SyncTask";

    private Cursor mCursor;
    private ContactsListAdapter mListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contacts_list);
        TextView text = (TextView) findViewById(android.R.id.empty);
        text.setText(Html.fromHtml(getString(R.string.text_contacts_empty)));

        startQuery();
        mListAdapter = new ContactsListAdapter(this, mCursor);
        mListAdapter.setOnContentChangedListener(this);
        setListAdapter(mListAdapter);

        if (!MessagingPreferences.getContactsListVisited(this))
            Toast.makeText(this, R.string.msg_do_refresh,
                    Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.startValidation(this);
            finish();
        }
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
        Intent i = new Intent(Intent.ACTION_PICK, cl.getContact().getRawContactUri());
        setResult(RESULT_OK, i);
        finish();
    }

    private void startSync() {
        if (Syncer.getInstance() == null) {
            // start monitored sync
            Log.v(TAG, "starting monitored sync");
            new SyncTask().execute(Syncer.getInstance(this));
        }
        else {
            // monitor existing instance
            Log.v(TAG, "sync already in progress, monitoring it");
            new SyncMonitorTask().execute();
        }
    }

    /** Executes a sync asynchronously, monitoring its status. */
    private final class SyncTask extends AsyncTask<Syncer, Integer, Boolean> {
        private Syncer syncer;
        private Dialog dialog;

        @Override
        protected Boolean doInBackground(Syncer... params) {
            if (isCancelled())
                return false;

            String authority = ContactsContract.AUTHORITY;
            Account account = Authenticator.getDefaultAccount(ContactsListActivity.this);
            ContentProviderClient provider = getContentResolver()
                    .acquireContentProviderClient(authority);

            try {
                syncer = params[0];
                syncer.performSync(ContactsListActivity.this, account,
                    authority, provider, new SyncResult());
            }
            catch (OperationCanceledException e) {
                // ignored - normal cancelation
            }
            catch (Exception e) {
                // TODO error string where??
                return false;
            }
            finally {
                provider.release();
                Syncer.release();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Runnable action = null;
            if (!result) {
                action = new Runnable() {
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog
                                .Builder(ContactsListActivity.this);
                        builder
                            // TODO i18n
                            .setTitle("Error")
                            .setMessage("Unable to refresh contacts list. Please retry later.")
                            .setPositiveButton(android.R.string.ok, null)
                            .create().show();
                    }
                };
            }

            finish(action);
        }

        @Override
        protected void onPreExecute() {
            ProgressDialog dg = new ProgressDialog(ContactsListActivity.this);
            dg.setMessage(getString(R.string.msg_sync_progress));
            dg.setIndeterminate(true);
            dg.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });

            dialog = dg;
            dg.show();
        }

        @Override
        protected void onCancelled(Boolean result) {
            if (syncer != null)
                syncer.onSyncCanceled();
            finish(null);
        }

        private void finish(Runnable action) {
            // dismiss status dialog
            if (dialog != null)
                dialog.dismiss();

            if (action != null)
                action.run();

            onContentChanged();
        }
    }

    /** Monitors an already running {@link Syncer}. */
    private final class SyncMonitorTask extends AsyncTask<Syncer, Integer, Boolean> {
        private Dialog dialog;

        @Override
        protected Boolean doInBackground(Syncer... params) {
            if (isCancelled())
                return false;

            while (Syncer.getInstance() != null) {
                try {
                    Thread.sleep(1000);
                }
                catch (InterruptedException e)  {
                    // interrupted :)
                }
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            finish();
        }

        @Override
        protected void onPreExecute() {
            ProgressDialog dg = new ProgressDialog(ContactsListActivity.this);
            dg.setMessage(getString(R.string.msg_sync_progress));
            dg.setIndeterminate(true);
            dg.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cancel(true);
                }
            });

            dialog = dg;
            dg.show();
        }

        @Override
        protected void onCancelled(Boolean result) {
            finish();
        }

        private void finish() {
            // dismiss status dialog
            if (dialog != null)
                dialog.dismiss();
            onContentChanged();
        }
    }

    private void startQuery() {
        Account account = Authenticator.getDefaultAccount(this);
        Uri uri = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            .build();
        mCursor = getContentResolver().query(uri, null, null, null, Syncer.RAW_COLUMN_DISPLAY_NAME);
        startManagingCursor(mCursor);
    }

    @Override
    public void onContentChanged(ContactsListAdapter adapter) {
        startQuery();
    }

}

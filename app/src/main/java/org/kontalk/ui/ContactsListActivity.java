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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.data.Contact;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.util.Preferences;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.Toast;


public class ContactsListActivity extends ActionBarActivity
        implements ContactsSyncActivity, ContactPickerListener {

    static final String TAG = ContactsListActivity.class.getSimpleName();

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
    protected void onPause() {
        super.onPause();
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

        // hold message center
        MessageCenterService.hold(this);

        mFragment.startQuery();
    }

    @Override
    public synchronized boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contacts_list_menu, menu);
        mSyncButton = menu.findItem(R.id.menu_refresh);
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
                startInvite();
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

    private void startInvite() {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(TextComponent.MIME_TYPE);
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.text_invite_message));

        List<ResolveInfo> resInfo = getPackageManager().queryIntentActivities(shareIntent, 0);
        // having size=1 means that we are the only handlers
        if (resInfo != null && resInfo.size() > 1) {
            List<Intent> targets = new ArrayList<Intent>();

            for (ResolveInfo resolveInfo : resInfo) {
                String packageName = resolveInfo.activityInfo.packageName;

                if (!getPackageName().equals(packageName)) {
                    // copy intent and add resolved info
                    Intent targetShareIntent = new Intent(shareIntent);
                    targetShareIntent
                        .setPackage(packageName)
                        .setComponent(new ComponentName(
                            packageName, resolveInfo.activityInfo.name))
                        .putExtra("org.kontalk.invite.label", resolveInfo.loadLabel(getPackageManager()));

                    targets.add(targetShareIntent);
                }
            }

            // initial intents are added before of the main intent, so we remove the last one here
            Intent chooser = Intent.createChooser(targets.remove(targets.size() - 1), getString(R.string.menu_invite));
            Collections.sort(targets, new DisplayNameComparator());
            // remove custom extras
            for (Intent intent : targets)
                intent.removeExtra("org.kontalk.invite.label");

            Parcelable[] extraIntents = new Parcelable[targets.size()];
            targets.toArray(extraIntents);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

            startActivity(chooser);
        }

        else {
            // no activity to handle invitation
            Toast.makeText(this, R.string.warn_invite_no_app,
                    Toast.LENGTH_SHORT).show();
        }
    }

    public static class DisplayNameComparator implements
            Comparator<Intent> {
        public DisplayNameComparator() {
            mCollator.setStrength(Collator.PRIMARY);
        }

        public final int compare(Intent a, Intent b) {
            CharSequence sa = a.getCharSequenceExtra("org.kontalk.invite.label");
            if (sa == null)
                sa = a.getComponent().getClassName();
            CharSequence sb = b.getCharSequenceExtra("org.kontalk.invite.label");
            if (sb == null)
                sb = b.getComponent().getClassName();

            return mCollator.compare(sa.toString(), sb.toString());
        }

        private final Collator mCollator = Collator.getInstance();
    }

}

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

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.authenticator.LegacyAuthentication;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.sync.Syncer;
import org.kontalk.ui.view.ContactPickerListener;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.XMPPUtils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListAdapter;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The conversations list activity.
 *
 * Layout is a sliding pane holding the conversation list as primary view and the contact list as
 * browser side view.
 *
 * @author Daniele Ricci
 * @version 1.0
 */
public class ConversationActivity extends ActionBarActivity implements ContactPickerListener {
    public static final String TAG = ConversationActivity.class.getSimpleName();

    private ConversationListFragment mFragment;
    private SlidingPaneLayout mSlidingPanel;

    private LockedProgressDialog mUpgradeProgress;
    private BroadcastReceiver mUpgradeReceiver;

    private static final int REQUEST_CONTACT_PICKER = 7720;

    private static final int DIALOG_AUTH_ERROR_WARNING = 1;

    private static final String ACTION_AUTH_ERROR_WARNING = "org.kontalk.AUTH_ERROR_WARN";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.conversation_screen);

        mFragment = (ConversationListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_conversation_list);
        final Fragment contactsListFragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_contacts_list);

        mSlidingPanel = (SlidingPaneLayout) findViewById(R.id.slider_pane);
        mSlidingPanel.setPanelSlideListener(new SlidingPaneLayout.PanelSlideListener() {
            @Override
            public void onPanelClosed(View panel) {
                getSupportActionBar().setTitle(getString(R.string.app_name));
                //supportInvalidateOptionsMenu();
                mFragment.setHasOptionsMenu(true);
                contactsListFragment.setHasOptionsMenu(false);
                Fragment composeMessageFragment = composeMessageFragmentOrNull();
                if (composeMessageFragment != null) {
                    composeMessageFragment.setHasOptionsMenu(true);
                }
            }

            @Override
            public void onPanelOpened(View panel) {
                getSupportActionBar().setTitle(getString(R.string.contacts_list_title));
                //supportInvalidateOptionsMenu();
                mFragment.setHasOptionsMenu(false);
                contactsListFragment.setHasOptionsMenu(true);
                Fragment composeMessageFragment = composeMessageFragmentOrNull();
                if (composeMessageFragment != null) {
                    composeMessageFragment.setHasOptionsMenu(false);
                }
                showOnFirstVisit();
            }

            @Override
            public void onPanelSlide(View panel, float slideOffset) {
            }
        });
        mSlidingPanel.setParallaxDistance(200);

        // initial menu
        if (!mSlidingPanel.isSlideable()) {
            //mFragment.setHasOptionsMenu(true);
            contactsListFragment.setHasOptionsMenu(false);
        }

        if (!xmppUpgrade())
            handleIntent(getIntent());
    }

    private Fragment composeMessageFragmentOrNull() {
        return getSupportFragmentManager().findFragmentById(R.id.fragment_compose_message);
    }

    /** Big upgrade: asymmetric key encryption (for XMPP). */
    private boolean xmppUpgrade() {
        AccountManager am = (AccountManager) getSystemService(Context.ACCOUNT_SERVICE);
        Account account = Authenticator.getDefaultAccount(am);
        if (account != null) {
            if (!Authenticator.hasPersonalKey(am, account)) {
                // first of all, disable offline mode
                Preferences.setOfflineMode(this, false);

                String name = Authenticator.getDefaultDisplayName(this);
                if (name == null || name.length() == 0) {
                    // ask for user name
                    askForPersonalName();
                }
                else {
                    // proceed to upgrade immediately
                    proceedXmppUpgrade(name);
                }

                return true;
            }
        }

        return false;
    }

    private void askForPersonalName() {
        DialogInterface.OnClickListener okListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // no key pair found, generate a new one
                if (BuildConfig.DEBUG) {
                    Toast.makeText(ConversationActivity.this,
                        R.string.msg_generating_keypair, Toast.LENGTH_LONG).show();
                }

                String name = InputDialog
                        .getInputText((Dialog) dialog)
                        .toString();

                // upgrade account
                proceedXmppUpgrade(name);
            }
        };

        DialogInterface.OnCancelListener cancelListener = new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                new AlertDialog.Builder(ConversationActivity.this)
                    .setTitle(R.string.title_no_personal_key)
                    .setMessage(R.string.msg_no_personal_key)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            }
        };

        new InputDialog.Builder(this,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
            .setTitle(R.string.title_no_name)
            .setMessage(R.string.msg_no_name)
            .setPositiveButton(android.R.string.ok, okListener)
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            })
            .setOnCancelListener(cancelListener)
            .show();
    }

    private void proceedXmppUpgrade(String name) {
        // start progress dialog
        mUpgradeProgress = new LockedProgressDialog(this);
        mUpgradeProgress.setIndeterminate(true);
        mUpgradeProgress.setMessage(getString(R.string.msg_xmpp_upgrading));
        mUpgradeProgress.show();

        // setup operation completed received
        mUpgradeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                LocalBroadcastManager lbm = LocalBroadcastManager
                    .getInstance(getApplicationContext());
                lbm.unregisterReceiver(mUpgradeReceiver);
                mUpgradeReceiver = null;

                // force contact list update
                SyncAdapter.requestSync(ConversationActivity.this, true);

                if (mUpgradeProgress != null) {
                    mUpgradeProgress.dismiss();
                    mUpgradeProgress = null;
                }
            }
        };
        IntentFilter filter = new IntentFilter(MessageCenterService.ACTION_REGENERATE_KEYPAIR);
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(mUpgradeReceiver, filter);

        LegacyAuthentication.doUpgrade(getApplicationContext(), name);
    }

    /** Called when a new intent is sent to the activity (if already started). */
    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);

        ConversationListFragment fragment = getListFragment();
        fragment.startQuery();
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        if (id == DIALOG_AUTH_ERROR_WARNING) {

            return new AlertDialog.Builder(this)
                .setTitle(R.string.title_auth_error)
                .setMessage(R.string.msg_auth_error)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        }

        return super.onCreateDialog(id, args);
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_AUTH_ERROR_WARNING.equals(action)) {
                showDialog(DIALOG_AUTH_ERROR_WARNING);
            }

            // this is for intents coming from the world, forwarded by ComposeMessage
            else {
                boolean actionView = Intent.ACTION_VIEW.equals(action);
                if (actionView || ComposeMessage.ACTION_VIEW_USERID.equals(action)) {
                    Uri uri = null;

                    if (actionView) {
                        Cursor c = getContentResolver().query(intent.getData(),
                            new String[]{Syncer.DATA_COLUMN_PHONE},
                            null, null, null);
                        if (c.moveToFirst()) {
                            String phone = c.getString(0);
                            String userJID = XMPPUtils.createLocalJID(this,
                                MessageUtils.sha1(phone));
                            uri = Threads.getUri(userJID);
                        }
                        c.close();
                    } else {
                        uri = intent.getData();
                    }

                    if (uri != null)
                        openConversation(uri);
                }
            }
        }
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
    public void onBackPressed() {
        if (mSlidingPanel.closePane()) {
            return;
        }

        ComposeMessageFragment f = (ComposeMessageFragment) getSupportFragmentManager()
            .findFragmentById(R.id.fragment_compose_message);
        if (f == null || !f.tryHideEmojiDrawer())
            super.onBackPressed();
    }

    @Override
    public void onResume() {
        super.onResume();

        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // mark all messages as old
                MessagesProvider.markAllThreadsAsOld(context);
                // update notification
                MessagingNotification.updateMessagesNotification(context, false);
            }
        }).start();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.start(this);
            finish();
        }
        else {
            // hold message center
            MessageCenterService.hold(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release message center
        MessageCenterService.release(this);
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
        // open by user hash
        openConversation(Threads.getUri(contact.getJID()));
        mSlidingPanel.closePane();
    }

    public void showContactPicker() {
        mSlidingPanel.openPane();
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
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.addToBackStack(null);
                ft.commit();
            }
        } else {
            Intent i = ComposeMessage.fromConversation(this, conv);
            startActivity(i);
        }
    }

    private void openConversation(Uri threadUri) {
        if (isDualPane()) {
            // TODO position
            //mFragment.getListView().setItemChecked(position, true);

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

    public static Intent authenticationErrorWarning(Context context) {
        Intent i = new Intent(context.getApplicationContext(), ConversationActivity.class);
        i.setAction(ACTION_AUTH_ERROR_WARNING);
        return i;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_invite:
                startInvite();
                return true;
        }

        return super.onOptionsItemSelected(item);
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

    private void showOnFirstVisit() {
        if (!Preferences.getContactsListVisited(this))
            Toast.makeText(this, R.string.msg_do_refresh,
                    Toast.LENGTH_LONG).show();
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

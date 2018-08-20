/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.jivesoftware.smack.util.StringUtils;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.data.Conversation;
import org.kontalk.provider.KontalkGroupCommands;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.sync.Syncer;
import org.kontalk.ui.prefs.HelpPreference;
import org.kontalk.ui.prefs.PreferencesActivity;
import org.kontalk.util.Preferences;
import org.kontalk.util.XMPPUtils;


/**
 * The conversations list activity.
 *
 * Layout is a sliding pane holding the conversation list as primary view and the contact list as
 * browser side view.
 *
 * @author Daniele Ricci
 */
public class ConversationsActivity extends MainActivity
        implements ComposeMessageParent {
    public static final String TAG = ConversationsActivity.class.getSimpleName();

    /** An intent extra for storing an ACTION_SEND intent from {@link ComposeMessage}. */
    public static final String EXTRA_SEND_INTENT = "org.kontalk.SEND_INTENT";

    private ConversationsFragment mFragment;

    /** Search menu item. */
    private MenuItem mSearchMenu;
    private MenuItem mDeleteAllMenu;
    /** Offline mode menu item. */
    private MenuItem mOfflineMenu;

    private RecyclerView.AdapterDataObserver mObserver;

    private static final int REQUEST_CONTACT_PICKER = 7720;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.conversations_screen);

        setupToolbar(false, false);

        mFragment = (ConversationsFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_conversation_list);
        mObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onDatabaseChanged();
                    }
                });
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                onChanged();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                onChanged();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                onChanged();
            }
        };

        if (Authenticator.getDefaultAccount(this) != null && !afterOnCreate())
            handleIntent(getIntent());
    }

    /** Called when a new intent is sent to the activity (if already started). */
    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    protected boolean handleIntent(Intent intent) {
        if (super.handleIntent(intent))
            return true;

        if (intent != null) {
            String action = intent.getAction();

            // this is for intents coming from the world, forwarded by ComposeMessage
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
                            XMPPUtils.createLocalpart(phone));
                        uri = Threads.getUri(userJID);
                    }
                    c.close();
                }
                else {
                    uri = intent.getData();
                }

                if (uri != null)
                    openConversation(uri);
            }
            else if (ComposeMessage.ACTION_VIEW_CONVERSATION.equals(action)) {
                Uri uri = intent.getData();
                if (uri != null) {
                    long threadId = ContentUris.parseId(uri);
                    if (threadId >= 0)
                        openConversation(threadId);
                }
            }
        }

        return true;
    }

    private void processSendIntent(Intent sendIntent) {
        AbstractComposeFragment f = getCurrentConversation();
        SendIntentReceiver.processSendIntent(this, sendIntent, f);
    }

    @Override
    public boolean onSearchRequested() {
        ConversationsFragment fragment = getListFragment();

        // no data found
        if (!fragment.hasListItems())
            return false;

        toggleSearch();
        return false;
    }

    private void toggleSearch() {
        if (mSearchMenu != null) {
            if (mSearchMenu.isActionViewExpanded())
                mSearchMenu.collapseActionView();
            else
                mSearchMenu.expandActionView();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mFragment.getListAdapter().registerAdapterDataObserver(mObserver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mFragment.getListAdapter().unregisterAdapterDataObserver(mObserver);
    }

    @Override
    public void onBackPressed() {
        if (mFragment != null && mFragment.isActionMenuOpen()) {
            mFragment.closeActionMenu();
            return;
        }
        AbstractComposeFragment f = getCurrentConversation();
        if (f == null || !f.tryHideEmojiDrawer()) {
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // set title for offline mode
        setOfflineModeTitle();

        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (Authenticator.getDefaultAccount(context) != null) {
                    // mark all messages as old
                    MessagesProviderClient.markAllThreadsAsOld(context);
                    // update notification
                    MessagingNotification.updateMessagesNotification(context, false);
                }
            }
        }).start();

        if (Authenticator.getDefaultAccount(this) == null) {
            NumberValidation.start(this);
            finish();
        }
        else {
            // hold message center
            MessageCenterService.hold(this, true);
            // since we have the conversation list open, we're going to disable notifications
            // no need to notify the user twice
            MessagingNotification.disable();
        }

        updateOffline();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        Intent intent = getIntent();
        if (intent != null) {
            Intent sendIntent = getIntent().getParcelableExtra(EXTRA_SEND_INTENT);
            if (sendIntent != null) {
                // handle the share intent sent from ComposeMessage
                processSendIntent(sendIntent);
                // clear the intent
                intent.removeExtra(EXTRA_SEND_INTENT);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // release message center
        MessageCenterService.release(this);
        // enable notifications again
        MessagingNotification.enable();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // contact chooser
        if (requestCode == REQUEST_CONTACT_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                ArrayList<Uri> uris;
                Uri uri = data.getData();
                if (uri != null) {
                    openConversation(uri);
                }
                else if ((uris = data.getParcelableArrayListExtra("org.kontalk.contacts")) != null) {
                    startGroupChat(uris);
                }
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private AbstractComposeFragment getCurrentConversation() {
        return (AbstractComposeFragment) getSupportFragmentManager()
            .findFragmentById(R.id.fragment_compose_message);
    }

    private void startGroupChat(List<Uri> users) {
        String selfJid = Authenticator.getSelfJID(this);
        String groupId = StringUtils.randomString(20);
        String groupJid = KontalkGroupCommands.createGroupJid(groupId, selfJid);

        // ensure no duplicates
        Set<String> usersList = new HashSet<>();
        for (Uri uri : users) {
            String member = uri.getLastPathSegment();
            // exclude ourselves
            if (!member.equalsIgnoreCase(selfJid))
                usersList.add(member);
        }

        if (usersList.size() > 0) {
            askGroupSubject(usersList, groupJid);
        }
    }

    private void askGroupSubject(final Set<String> usersList, final String groupJid) {
        new MaterialDialog.Builder(this)
            .title(R.string.title_group_subject)
            .positiveText(android.R.string.ok)
            .negativeText(android.R.string.cancel)
            .input(null, null, true, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                    String title = !TextUtils.isEmpty(input) ? input.toString() : null;
                    Context ctx = ConversationsActivity.this;

                    String[] users = usersList.toArray(new String[usersList.size()]);
                    long groupThreadId = Conversation.initGroupChat(ctx,
                        groupJid, title, users, "");

                    // store create group command to outbox
                    // NOTE: group chats can currently only be created with chat encryption enabled
                    boolean encrypted = Preferences.getEncryptionEnabled(ctx);
                    String msgId = MessageCenterService.messageId();
                    Uri cmdMsg = KontalkGroupCommands.createGroup(ctx,
                        groupThreadId, groupJid, users, msgId, encrypted);
                    // TODO check for null

                    // send create group command now
                    MessageCenterService.createGroup(ConversationsActivity.this, groupJid, title,
                        users, encrypted, ContentUris.parseId(cmdMsg), msgId);

                    // load the new conversation
                    openConversation(Threads.getUri(groupJid), true);
                }
            })
            .inputRange(0, MyMessages.Groups.GROUP_SUBJECT_MAX_LENGTH)
            .show();
    }

    public void setOfflineModeTitle() {
        setTitle(MessageCenterService.isOfflineMode(this));
    }

    public void setTitle(boolean offline) {
        setTitle(offline ? R.string.app_name_offline : R.string.app_name);
    }

    public ConversationsFragment getListFragment() {
        return mFragment;
    }

    public boolean isDualPane() {
        return findViewById(R.id.fragment_compose_message) != null;
    }

    public void showContactPicker(boolean multiselect) {
        // TODO one day it will be like this
        // Intent i = new Intent(Intent.ACTION_PICK, Users.CONTENT_URI);
        Intent i = new Intent(this, ContactsListActivity.class);
        i.putExtra(ContactsListActivity.MODE_MULTI_SELECT, multiselect);
        // FIXME when in single pane mode, onActivityResult will cause us to requery for nothing!!
        // maybe we should let handle the start of ComposeMessage to the contacts list activity
        // use two subclass activities: one for returning a result, one for launching compose message
        startActivityForResult(i, REQUEST_CONTACT_PICKER);
    }

    @Override
    public void setTitle(@Nullable CharSequence title, @Nullable CharSequence subtitle) {
        // nothing
    }

    @Override
    public void setUpdatingSubtitle() {
        // nothing
    }

    /** For tablets. */
    @Override
    public void loadConversation(long threadId, boolean creatingGroup) {
        openConversation(threadId, creatingGroup);
    }

    /** For tablets. */
    @Override
    public void loadConversation(Uri threadUri) {
        openConversation(threadUri, false);
    }

    public void openConversation(Conversation conv, int position) {
        if (isDualPane()) {
            // TODO mFragment.getListView().setItemChecked(position, true);

            // get the old fragment
            AbstractComposeFragment f = getCurrentConversation();

            // check if we are replacing the same fragment
            Conversation oldConv = (f != null ? f.getConversation() : null);
            if (oldConv == null || !oldConv.getRecipient().equals(conv.getRecipient())) {
                f = AbstractComposeFragment.fromConversation(this, conv, false);
                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.commit();
            }
        }
        else {
            Intent i = ComposeMessage.fromConversation(this, conv);
            startActivity(i);
        }
    }

    void openConversation(Uri threadUri) {
        openConversation(threadUri, false);
    }

    void openConversation(Uri threadUri, boolean creatingGroup) {
        if (isDualPane()) {
            // load conversation
            String userId = threadUri.getLastPathSegment();
            Conversation conv = Conversation.loadFromUserId(this, userId);

            // get the old fragment
            AbstractComposeFragment f = getCurrentConversation();

            // check if we are replacing the same fragment
            Conversation oldConv = (f != null ? f.getConversation() : null);
            if (oldConv == null || conv == null || !oldConv.getRecipient().equals(conv.getRecipient())) {
                if (conv == null)
                    f = AbstractComposeFragment.fromUserId(this, userId, creatingGroup);
                else
                    f = AbstractComposeFragment.fromConversation(this, conv, creatingGroup);

                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.commitAllowingStateLoss();
            }
        }
        else {
            Intent i = ComposeMessage.fromUserId(this, threadUri.getLastPathSegment(), creatingGroup);
            if (i != null)
                startActivity(i);
            else
                Toast.makeText(this, R.string.contact_not_registered, Toast.LENGTH_LONG)
                    .show();
        }
    }

    private void openConversation(long threadId) {
        openConversation(threadId, false);
    }

    private void openConversation(long threadId, boolean creatingGroup) {
        if (isDualPane()) {
            // load conversation
            Conversation conv = Conversation.loadFromId(this, threadId);
            if (conv == null)
                return;

            // get the old fragment
            AbstractComposeFragment f = getCurrentConversation();

            // check if we are replacing the same fragment
            Conversation oldConv = (f != null ? f.getConversation() : null);
            if (oldConv == null || !oldConv.getRecipient().equals(conv.getRecipient())) {
                f = AbstractComposeFragment.fromConversation(this, conv, creatingGroup);

                // Execute a transaction, replacing any existing fragment
                // with this one inside the frame.
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.replace(R.id.fragment_compose_message, f);
                ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                ft.commitAllowingStateLoss();
            }
        }
        else {
            startActivity(ComposeMessage.fromConversation(this, threadId, creatingGroup));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation_list_menu, menu);

        // search
        mSearchMenu = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) mSearchMenu.getActionView();
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        // LayoutParams.MATCH_PARENT does not work, use a big value instead
        searchView.setMaxWidth(1000000);

        mDeleteAllMenu = menu.findItem(R.id.menu_delete_all);

        // offline mode
        mOfflineMenu = menu.findItem(R.id.menu_offline);

        // trigger manually
        onDatabaseChanged();
        updateOffline();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                 // TODO @deprecated
                onBackPressed();
                return true;

            case R.id.menu_status:
                StatusActivity.start(this);
                return true;

            case R.id.menu_offline:
                final Context ctx = this;
                final boolean currentMode = Preferences.getOfflineMode();
                if (!currentMode && !Preferences.getOfflineModeUsed()) {
                    // show offline mode warning
                    new MaterialDialog.Builder(ctx)
                        .content(R.string.message_offline_mode_warning)
                        .positiveText(android.R.string.ok)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                Preferences.setOfflineModeUsed();
                                switchOfflineMode();
                            }
                        })
                        .negativeText(android.R.string.cancel)
                        .show();
                }
                else {
                    switchOfflineMode();
                }
                return true;

            case R.id.menu_delete_all:
                deleteAll();
                return true;

            case R.id.menu_mykey:
                launchMyKey();
                return true;

            case R.id.menu_donate:
                launchDonate();
                return true;

            case R.id.menu_help:
                launchHelp();
                return true;

            case R.id.menu_settings: {
                PreferencesActivity.start(this);
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    /** Updates various UI elements after a database change. */
    @UiThread
    void onDatabaseChanged() {
        boolean visible = mFragment.hasListItems();
        if (mSearchMenu != null) {
            mSearchMenu.setEnabled(visible).setVisible(visible);
        }
        // if it's null it hasn't gone through onCreateOptionsMenu() yet
        if (mSearchMenu != null) {
            mSearchMenu.setEnabled(visible).setVisible(visible);
            mDeleteAllMenu.setEnabled(visible).setVisible(visible);
        }

        // for tablet interface
        // select the current conversation item
        AbstractComposeFragment f = getCurrentConversation();
        if (f != null) {
            /* TODO
            int position = ((ConversationListAdapter) mFragment.getListAdapter())
                .getItemPosition(f.getUserId());
            mFragment.getListView().setItemChecked(position, true);
            */
        }
    }

    /** Updates offline mode menu. */
    private void updateOffline() {
        if (mOfflineMenu != null) {
            boolean offlineMode = Preferences.getOfflineMode();
            // set menu
            int icon = (offlineMode) ? R.drawable.ic_menu_online :
                R.drawable.ic_menu_offline;
            int title = (offlineMode) ? R.string.menu_online : R.string.menu_offline;
            mOfflineMenu.setIcon(icon);
            mOfflineMenu.setTitle(title);
            // set window title
            setTitle(offlineMode);
        }
    }

    void switchOfflineMode() {
        boolean currentMode = Preferences.getOfflineMode();
        Preferences.switchOfflineMode(this);
        updateOffline();
        // notify the user about the change
        int text = (currentMode) ? R.string.going_online : R.string.going_offline;
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void deleteAll() {
        new MaterialDialog.Builder(this)
            .content(R.string.confirm_will_delete_all)
            .positiveText(android.R.string.ok)
            .positiveColorRes(R.color.button_danger)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    Conversation.deleteAll(ConversationsActivity.this, dialog.isPromptCheckBoxChecked(), false);
                    MessagingNotification.updateMessagesNotification(getApplicationContext(), false);
                }
            })
            .checkBoxPromptRes(R.string.delete_threads_leave_any_groups, false, null)
            .negativeText(android.R.string.cancel)
            .show();
    }

    private void launchDonate() {
        Intent i = new Intent(this, AboutActivity.class);
        i.setAction(AboutActivity.ACTION_DONATION);
        startActivity(i);
    }

    private void launchMyKey() {
        Intent i = new Intent(this, MyKeyActivity.class);
        startActivity(i);
    }

    private void launchHelp() {
        HelpPreference.openHelp(this);
    }

}

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

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.ui.adapter.ConversationListAdapter;
import org.kontalk.ui.view.ConversationListItem;
import org.kontalk.util.Preferences;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.akalipetis.fragment.MultiChoiceModeListener;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.support.v7.view.ActionMode;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;


public class ConversationListFragment extends com.akalipetis.fragment.ActionModeListFragment
        implements Contact.ContactChangeListener, MultiChoiceModeListener {
    private static final String TAG = ConversationList.TAG;

    private static final int THREAD_LIST_QUERY_TOKEN = 8720;

    /** Context menu group ID for this fragment. */
    private static final int CONTEXT_MENU_GROUP_ID = 1;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private boolean mDualPane;

    /** Search menu item. */
    private MenuItem mSearchMenu;
    private MenuItem mDeleteAllMenu;
    /** Offline mode menu item. */
    private MenuItem mOfflineMenu;

    private int mCheckedItemCount;

    private final ConversationListAdapter.OnContentChangedListener mContentChangedListener =
        new ConversationListAdapter.OnContentChangedListener() {
        public void onContentChanged(ConversationListAdapter adapter) {
            startQuery();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.conversation_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mQueryHandler = new ThreadListQueryHandler(getActivity().getContentResolver());
        mListAdapter = new ConversationListAdapter(getActivity(), null, getListView());
        mListAdapter.setOnContentChangedListener(mContentChangedListener);

        ListView list = getListView();

        // Check to see if we have a frame in which to embed the details
        // fragment directly in the containing UI.
        View detailsFrame = getActivity().findViewById(R.id.fragment_compose_message);
        mDualPane = detailsFrame != null
                && detailsFrame.getVisibility() == View.VISIBLE;

        if (mDualPane) {
            // TODO restore state
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            list.setItemsCanFocus(true);
        }

        setListAdapter(mListAdapter);
        setMultiChoiceModeListener(this);

        getView().findViewById(R.id.action).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseContact();
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO save state
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.conversation_list_menu, menu);

        // compose message
        /*
        MenuItem item = menu.findItem(R.id.menu_compose);
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        */

        // search
        mSearchMenu = menu.findItem(R.id.menu_search);
        //MenuItemCompat.setShowAsAction(mSearchMenu, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

        mDeleteAllMenu = menu.findItem(R.id.menu_delete_all);

        // offline mode
        mOfflineMenu = menu.findItem(R.id.menu_offline);

        // trigger manually
        onDatabaseChanged();
        updateOffline();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_status:
                StatusActivity.start(getActivity());
                return true;

            case R.id.menu_offline:
                final Context ctx = getActivity();
                final boolean currentMode = Preferences.getOfflineMode(ctx);
                if (!currentMode && !Preferences.getOfflineModeUsed(ctx)) {
                    // show offline mode warning
                    new AlertDialogWrapper.Builder(ctx)
                        .setMessage(R.string.message_offline_mode_warning)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                Preferences.setOfflineModeUsed(ctx);
                                switchOfflineMode();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                }
                else {
                    switchOfflineMode();
                }
                return true;

            case R.id.menu_search:
                getActivity().onSearchRequested();
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

            case R.id.menu_settings: {
                PreferencesActivity.start(getActivity());
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        if (checked)
            mCheckedItemCount++;
        else
            mCheckedItemCount--;
        mode.setTitle(getResources()
            .getQuantityString(R.plurals.context_selected,
                mCheckedItemCount, mCheckedItemCount));
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.menu_delete) {
            // using clone because listview returns its original copy
            deleteSelectedThreads(getListView().getCheckedItemPositions().clone());
            mode.finish();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.conversation_list_ctx, menu);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mCheckedItemCount = 0;
        getListView().clearChoices();
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        // TODO what to do here?
        return false;
    }

    private static final int MENU_OPEN_THREAD = 1;
    private static final int MENU_VIEW_CONTACT = 2;
    private static final int MENU_DELETE_THREAD = 3;

    @Override
    public boolean onContextItemSelected(android.view.MenuItem item) {
        // not our context
        if (item.getGroupId() != CONTEXT_MENU_GROUP_ID)
            return false;

        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
        ConversationListItem vitem = (ConversationListItem) info.targetView;
        Conversation conv = vitem.getConversation();

        switch (item.getItemId()) {
            case MENU_OPEN_THREAD:
                ConversationList parent = getParentActivity();
                if (parent != null)
                    parent.openConversation(conv, info.position);
                return true;

            case MENU_VIEW_CONTACT:
                Contact contact = conv.getContact();
                if (contact != null)
                    startActivity(new Intent(Intent.ACTION_VIEW, contact.getUri()));
                return true;

            case MENU_DELETE_THREAD:
                deleteThread(conv.getThreadId());
                return true;
        }

        return super.onContextItemSelected(item);
    }

    private void launchDonate() {
        Intent i = new Intent(getActivity(), AboutActivity.class);
        i.setAction(AboutActivity.ACTION_DONATION);
        startActivity(i);
    }

    private void launchMyKey() {
        Intent i = new Intent(getActivity(), MyKeyActivity.class);
        startActivity(i);
    }

    private void deleteSelectedThreads(final SparseBooleanArray checked) {
        new AlertDialogWrapper
            .Builder(getActivity())
            .setMessage(R.string.confirm_will_delete_threads)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Context ctx = getActivity();
                    for (int i = 0, c = mListAdapter.getCount(); i < c; ++i) {
                        if (checked.get(i))
                            Conversation.deleteFromCursor(ctx, (Cursor) mListAdapter.getItem(i));
                    }
                    mListAdapter.notifyDataSetChanged();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteThread(final long threadId) {
        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
        builder.setMessage(R.string.confirm_will_delete_thread);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                MessagesProvider.deleteThread(getActivity(), threadId);
                MessagingNotification.updateMessagesNotification(getActivity().getApplicationContext(), false);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    public void chooseContact() {
        ConversationList parent = getParentActivity();
        if (parent != null)
            parent.showContactPicker();
    }

    private void deleteAll() {
        AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
        builder.setMessage(R.string.confirm_will_delete_all);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                MessagesProvider.deleteDatabase(getActivity());
                MessagingNotification.updateMessagesNotification(getActivity().getApplicationContext(), false);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    public ConversationList getParentActivity() {
        return (ConversationList) getActivity();
    }

    public void startQuery() {
        Cursor c = null;
        try {
            c = Conversation.startQuery(getActivity());
        }
        catch (SQLiteException e) {
            Log.e(TAG, "query error", e);
        }
        mQueryHandler.onQueryComplete(THREAD_LIST_QUERY_TOKEN, null, c);
    }

    @Override
    public void onStart() {
        super.onStart();
        startQuery();
        Contact.registerContactChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        // update offline mode
        updateOffline();
    }

    @Override
    public void onStop() {
        super.onStop();
        Contact.unregisterContactChangeListener(this);
        mListAdapter.changeCursor(null);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ConversationListItem cv = (ConversationListItem) v;
        Conversation conv = cv.getConversation();

        ConversationList parent = getParentActivity();
        if (parent != null)
            parent.openConversation(conv, position);
    }

    /** Used only in fragment contexts. */
    public void endConversation(ComposeMessageFragment composer) {
        getFragmentManager().beginTransaction().remove(composer).commit();
    }

    public final boolean isFinishing() {
        return (getActivity() == null ||
                (getActivity() != null && getActivity().isFinishing())) ||
                isRemoving();
    }

    /* Updates various UI elements after a database change. */
    private void onDatabaseChanged() {
        boolean visible = (mListAdapter != null && !mListAdapter.isEmpty());
        if (mSearchMenu != null) {
            mSearchMenu.setEnabled(visible).setVisible(visible);
        }
        // if it's null it hasn't gone through onCreateOptionsMenu() yet
        if (mSearchMenu != null) {
            mSearchMenu.setEnabled(visible).setVisible(visible);
            mDeleteAllMenu.setEnabled(visible).setVisible(visible);
        }
    }

    /** Updates offline mode menu. */
    private void updateOffline() {
        Context context = getActivity();
        if (mOfflineMenu != null && context != null) {
            boolean offlineMode = Preferences.getOfflineMode(context);
            int icon = (offlineMode) ? R.drawable.ic_menu_start_conversation :
                android.R.drawable.ic_menu_close_clear_cancel;
            int title = (offlineMode) ? R.string.menu_online : R.string.menu_offline;
            mOfflineMenu.setIcon(icon);
            mOfflineMenu.setTitle(title);
        }
    }

    private void switchOfflineMode() {
        Context ctx = getActivity();
        boolean currentMode = Preferences.getOfflineMode(ctx);
        Preferences.switchOfflineMode(ctx);
        updateOffline();
        // notify the user about the change
        int text = (currentMode) ? R.string.going_online : R.string.going_offline;
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onContactInvalidated(String userId) {
        mQueryHandler.post(new Runnable() {
            @Override
            public void run() {
                // just requery
                startQuery();
            }
        });
    }

    /**
     * The conversation list query handler.
     */
    private final class ThreadListQueryHandler extends AsyncQueryHandler {
        public ThreadListQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (cursor == null || isFinishing()) {
                // close cursor - if any
                if (cursor != null) cursor.close();

                Log.w(TAG, "query aborted or error!");
                mListAdapter.changeCursor(null);
                return;
            }

            switch (token) {
                case THREAD_LIST_QUERY_TOKEN:
                    mListAdapter.changeCursor(cursor);
                    onDatabaseChanged();
                    break;

                default:
                    Log.e(TAG, "onQueryComplete called with unknown token " + token);
            }
        }
    }

    public boolean isDualPane() {
        return mDualPane;
    }

}

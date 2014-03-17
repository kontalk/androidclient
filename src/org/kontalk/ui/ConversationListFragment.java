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
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.service.MessageCenterService;

import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.view.MenuItemCompat;
import android.text.Html;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class ConversationListFragment extends ListFragment {

    private static final String TAG = ConversationListFragment.class.getSimpleName();

    private static final int THREAD_LIST_QUERY_TOKEN = 8720;

    /** Context menu group ID for this fragment. */
    private static final int CONTEXT_MENU_GROUP_ID = 1;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private boolean mDualPane;

    /** Search menu item (might not exist). */
    private MenuItem mSearchMenu;
    /** Search menu action bar item. */
    private MenuItem mSearchMenuAction;
    private MenuItem mDeleteAllMenu;
    /** Offline mode menu item. */
    private MenuItem mOfflineMenu;

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

        // add Compose message entry only if are in dual pane mode
        if (!mDualPane) {
            /*
            LayoutInflater inflater = getLayoutInflater(savedInstanceState);
            ConversationListItem headerView = (ConversationListItem)
                    inflater.inflate(R.layout.conversation_list_item, list, false);
            headerView.bind(getString(R.string.new_message),
                    getString(R.string.create_new_message));
            list.addHeaderView(headerView, null, true);
            */
        }
        else {
            // TODO restore state
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        	list.setItemsCanFocus(true);
        }

        // text for empty conversation list
        TextView text = (TextView) getActivity().findViewById(android.R.id.empty);
        text.setText(Html.fromHtml(getString(R.string.text_conversations_empty)));

        setListAdapter(mListAdapter);
        registerForContextMenu(list);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // TODO save state
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.conversation_list_menu, menu);

        // compose message
        MenuItem item = menu.findItem(R.id.menu_compose2);
        MenuItemCompat.setShowAsAction(item, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

        // search
        mSearchMenuAction = menu.findItem(R.id.menu_search2);
        MenuItemCompat.setShowAsAction(mSearchMenuAction, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);

        // search (might not exist)
        mSearchMenu = menu.findItem(R.id.menu_search);
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
            case R.id.menu_compose:
            case R.id.menu_compose2:
                chooseContact();
                return true;

            case R.id.menu_status:
                StatusActivity.start(getActivity());
                return true;

            case R.id.menu_offline:
                final Context ctx = getActivity();
                final boolean currentMode = MessagingPreferences.getOfflineMode(ctx);
                if (!currentMode && !MessagingPreferences.getOfflineModeUsed(ctx)) {
                    // show offline mode warning
                    new AlertDialog.Builder(ctx)
                        .setTitle(R.string.title_offline_mode_warning)
                        .setMessage(R.string.message_offline_mode_warning)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                MessagingPreferences.setOfflineModeUsed(ctx);
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
            case R.id.menu_search2:
                getActivity().onSearchRequested();
                return true;

            case R.id.menu_delete_all:
                deleteAll();
                return true;

            case R.id.menu_donate:
                launchDonate();
                return true;

            case R.id.menu_settings: {
                MessagingPreferences.start(getActivity());
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private static final int MENU_OPEN_THREAD = 1;
    private static final int MENU_VIEW_CONTACT = 2;
    private static final int MENU_DELETE_THREAD = 3;

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        ConversationListItem vitem = (ConversationListItem) info.targetView;
        Conversation conv = vitem.getConversation();
        if (conv != null) {
            Contact contact = conv.getContact();
            String title;
            if (contact != null)
                title = contact.getName() != null ? contact.getName() : contact.getNumber();
            else
                title = conv.getRecipient();

            menu.setHeaderTitle(title);
            menu.add(CONTEXT_MENU_GROUP_ID, MENU_OPEN_THREAD, MENU_OPEN_THREAD, R.string.view_conversation);
            if (contact != null)
                menu.add(CONTEXT_MENU_GROUP_ID, MENU_VIEW_CONTACT, MENU_VIEW_CONTACT, R.string.view_contact);
            menu.add(CONTEXT_MENU_GROUP_ID, MENU_DELETE_THREAD, MENU_DELETE_THREAD, R.string.delete_thread);
        }
    }

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

    private void deleteThread(final long threadId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.confirm_delete_thread);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.confirm_delete_all);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
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

        // hold message center
        MessageCenterService.hold(getActivity());
        startQuery();
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
        mListAdapter.changeCursor(null);
        // release message center
        MessageCenterService.release(getActivity());
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
            mSearchMenu.setEnabled(visible);
            mSearchMenu.setVisible(visible);
        }
        // if it's null it hasn't gone through onCreateOptionsMenu() yet
        if (mSearchMenuAction != null) {
            mSearchMenuAction.setEnabled(visible);
            mSearchMenuAction.setVisible(visible);
            mDeleteAllMenu.setEnabled(visible);
            mDeleteAllMenu.setVisible(visible);
        }
    }

    /** Updates offline mode menu. */
    private void updateOffline() {
        if (mOfflineMenu != null) {
            boolean offlineMode = MessagingPreferences.getOfflineMode(getActivity());
            int icon = (offlineMode) ? R.drawable.ic_menu_start_conversation :
                android.R.drawable.ic_menu_close_clear_cancel;
            int title = (offlineMode) ? R.string.menu_online : R.string.menu_offline;
            mOfflineMenu.setIcon(icon);
            mOfflineMenu.setTitle(title);
        }
    }

    private void switchOfflineMode() {
        Context ctx = getActivity();
        boolean currentMode = MessagingPreferences.getOfflineMode(ctx);
        MessagingPreferences.switchOfflineMode(ctx);
        updateOffline();
        // notify the user about the change
        int text = (currentMode) ? R.string.going_online : R.string.going_offline;
        Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
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

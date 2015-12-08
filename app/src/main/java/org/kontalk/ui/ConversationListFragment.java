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
import org.kontalk.ui.view.AbsListViewScrollDetector;
import org.kontalk.ui.view.ConversationListItem;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.akalipetis.fragment.ActionModeListFragment;
import com.akalipetis.fragment.MultiChoiceModeListener;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ListView;
import android.widget.Toast;


public class ConversationListFragment extends ActionModeListFragment
        implements Contact.ContactChangeListener, MultiChoiceModeListener {
    private static final String TAG = ConversationsActivity.TAG;

    private static final int THREAD_LIST_QUERY_TOKEN = 8720;

    private ThreadListQueryHandler mQueryHandler;
    private ConversationListAdapter mListAdapter;
    private boolean mDualPane;

    /** Search menu item. */
    private MenuItem mSearchMenu;
    private MenuItem mDeleteAllMenu;
    /** Offline mode menu item. */
    private MenuItem mOfflineMenu;

    private View mAction;
    private boolean mActionVisible;

    private int mCheckedItemCount;

    private final ConversationListAdapter.OnContentChangedListener mContentChangedListener =
        new ConversationListAdapter.OnContentChangedListener() {
        public void onContentChanged(ConversationListAdapter adapter) {
            if (!isFinishing())
                startQuery();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.conversation_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAction = view.findViewById(R.id.action);
        mAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseContact();
            }
        });
        mActionVisible = true;

        getListView().setOnScrollListener(new AbsListViewScrollDetector() {
            @Override
            public void onScrollUp() {
                if (mActionVisible) {
                    mActionVisible = false;
                    if (isAnimating())
                        mAction.clearAnimation();

                    Animation anim = AnimationUtils.loadAnimation(getActivity(), R.anim.exit_to_bottom);
                    anim.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            mAction.clearAnimation();
                            mAction.setVisibility(View.GONE);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                    mAction.startAnimation(anim);
                }
            }

            @Override
            public void onScrollDown() {
                if (!mActionVisible) {
                    mActionVisible = true;
                    if (isAnimating())
                        mAction.clearAnimation();

                    Animation anim = AnimationUtils.loadAnimation(getActivity(), R.anim.enter_from_bottom);
                    mAction.startAnimation(anim);
                    anim.setAnimationListener(new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            mAction.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            mAction.clearAnimation();
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {
                        }
                    });
                }
            }

            private boolean isAnimating() {
                return mAction.getAnimation() != null;
            }
        });
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
        if (isActionModeActive())
            return true;

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

    public boolean isActionModeActive() {
        return mCheckedItemCount > 0;
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
            deleteSelectedThreads(SystemUtils
                .cloneSparseBooleanArray(getListView().getCheckedItemPositions()));
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
        return false;
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
                try {
                    MessagesProvider.deleteThread(getActivity(), threadId);
                    MessagingNotification.updateMessagesNotification(getActivity().getApplicationContext(), false);
                }
                catch (SQLiteDiskIOException e) {
                    Log.w(TAG, "error deleting thread");
                    Toast.makeText(getActivity(), R.string.error_delete_thread,
                        Toast.LENGTH_LONG).show();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.create().show();
    }

    public void chooseContact() {
        ConversationsActivity parent = getParentActivity();
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

    public ConversationsActivity getParentActivity() {
        return (ConversationsActivity) getActivity();
    }

    public void startQuery() {
        Cursor c = null;
        Context ctx = getActivity();
        if (ctx != null) {
            try {
                c = Conversation.startQuery(ctx);
            }
            catch (SQLiteException e) {
                Log.e(TAG, "query error", e);
            }
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
        int choiceMode = l.getChoiceMode();
        if (choiceMode == ListView.CHOICE_MODE_NONE || choiceMode == ListView.CHOICE_MODE_SINGLE) {
            ConversationListItem cv = (ConversationListItem) v;
            Conversation conv = cv.getConversation();

            ConversationsActivity parent = getParentActivity();
            if (parent != null)
                parent.openConversation(conv, position);
        }
        else {
            super.onListItemClick(l, v, position, id);
        }
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
        ConversationsActivity context = getParentActivity();
        if (mOfflineMenu != null && context != null) {
            boolean offlineMode = Preferences.getOfflineMode(context);
            // set menu
            int icon = (offlineMode) ? R.drawable.ic_menu_start_conversation :
                android.R.drawable.ic_menu_close_clear_cancel;
            int title = (offlineMode) ? R.string.menu_online : R.string.menu_offline;
            mOfflineMenu.setIcon(icon);
            mOfflineMenu.setTitle(title);
            // set window title
            context.setTitle(offlineMode);
        }
    }

    private void switchOfflineMode() {
        ConversationsActivity ctx = getParentActivity();
        if (ctx != null) {
            boolean currentMode = Preferences.getOfflineMode(ctx);
            Preferences.switchOfflineMode(ctx);
            updateOffline();
            // notify the user about the change
            int text = (currentMode) ? R.string.going_online : R.string.going_offline;
            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show();
        }
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

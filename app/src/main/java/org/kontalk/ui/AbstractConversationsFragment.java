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
import java.util.List;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import android.app.Activity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MyMessages;
import org.kontalk.ui.adapter.ConversationListAdapter;
import org.kontalk.ui.model.ConversationsViewModel;
import org.kontalk.ui.view.ConversationListItem;


public abstract class AbstractConversationsFragment extends Fragment
        implements Contact.ContactChangeListener, ConversationListAdapter.OnItemClickListener {
    static final String TAG = ConversationsActivity.TAG;

    private View mEmptyView;
    private RecyclerView mListView;
    private ConversationListAdapter mListAdapter;
    private ConversationsViewModel mViewModel;
    private RecyclerView.AdapterDataObserver mObserver;

    private SelectionTracker<Long> mSelectionTracker;
    private ActionModeCallback mActionModeCallback;
    private ActionMode mActionMode;

    private Handler mHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        mViewModel = ViewModelProviders.of(this).get(ConversationsViewModel.class);
        mObserver = new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                final ConversationsCallback parent = getParentCallback();
                if (parent != null) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            parent.onDatabaseChanged();
                        }
                    });
                }
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
    }

    @Override
    public abstract View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState);

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mEmptyView = view.findViewById(android.R.id.empty);

        mListView = view.findViewById(android.R.id.list);
        mListView.setLayoutManager(new LinearLayoutManager(view.getContext(),
            LinearLayoutManager.VERTICAL, false));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mListAdapter = new ConversationListAdapter(getContext());
        mListAdapter.setItemListener(this);
        onAdapterCreated(mListAdapter);

        mListView.setAdapter(mListAdapter);
        mActionModeCallback = new ActionModeCallback();

        final ItemKeyProvider<Long> keyProvider = new ItemKeyProvider<Long>(ItemKeyProvider.SCOPE_MAPPED) {
            @Override
            public Long getKey(int position) {
                return mListAdapter.getItemId(position);
            }

            @Override
            public int getPosition(@NonNull Long key) {
                RecyclerView.ViewHolder viewHolder = mListView.findViewHolderForItemId(key);
                return viewHolder == null ? RecyclerView.NO_POSITION : viewHolder.getLayoutPosition();
            }
        };

        mSelectionTracker = new SelectionTracker.Builder<>(
                "conversation-selector",
                mListView,
                keyProvider,
                new ConversationListAdapter.ConversationItemDetailsLookup(mListView),
                StorageStrategy.createLongStorage())
            .withSelectionPredicate(new SelectionTracker.SelectionPredicate<Long>() {
                @Override
                public boolean canSetStateForKey(@NonNull Long key, boolean nextState) {
                    return key > 0;
                }

                @Override
                public boolean canSetStateAtPosition(int position, boolean nextState) {
                    if (position >= 0) {
                        Long key = keyProvider.getKey(position);
                        return key != null && key > 0;
                    }
                    return false;
                }

                @Override
                public boolean canSelectMultiple() {
                    return true;
                }
            })
            .build();
        mSelectionTracker.addObserver(new SelectionTracker.SelectionObserver<Long>() {
            @Override
            public void onSelectionChanged() {
                AbstractConversationsFragment.this.onSelectionChanged();
            }
        });

        mListAdapter.setSelectionTracker(mSelectionTracker);

        mViewModel.load(getContext(), isArchived());
        mViewModel.getData().observe(getViewLifecycleOwner(), new Observer<PagedList<Conversation>>() {
            @Override
            public void onChanged(@Nullable PagedList<Conversation> conversations) {
                mListAdapter.submitList(conversations);
                mEmptyView.setVisibility(conversations != null && conversations.size() > 0 ?
                    View.GONE : View.VISIBLE);
            }
        });
        mListAdapter.registerAdapterDataObserver(mObserver);

        if (savedInstanceState != null) {
            mSelectionTracker.onRestoreInstanceState(savedInstanceState);
        }

        if (mSelectionTracker.hasSelection()){
            mActionMode = getParentCallback().startSupportActionMode(mActionModeCallback);
            updateActionModeSelectionCount(mSelectionTracker.getSelection().size());
        }
    }

    /** Whether to enable hybrid single and multiple selection. */
    protected abstract boolean isSingleSelection();

    /** Child classes should use this to set item listeners. */
    protected abstract void onAdapterCreated(ConversationListAdapter adapter);

    /** Whether we should query for archived chats or not. */
    protected abstract boolean isArchived();

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mSelectionTracker.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mListAdapter != null)
            mListAdapter.unregisterAdapterDataObserver(mObserver);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return isActionModeActive() || super.onOptionsItemSelected(item);
    }

    public boolean isActionModeActive() {
        return mActionMode != null;
    }

    protected ConversationsCallback getParentCallback() {
        return (ConversationsCallback) getActivity();
    }

    public ConversationsViewModel getViewModel() {
        return mViewModel;
    }

    public ConversationListAdapter getListAdapter() {
        return mListAdapter;
    }

    public Selection<Long> getSelectedPositions() {
        return mSelectionTracker.getSelection();
    }

    @Override
    public void onStart() {
        super.onStart();
        Contact.registerContactChangeListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Contact.unregisterContactChangeListener(this);
    }

    @Override
    public void onItemClick(ConversationListItem item, int position) {
        if (mActionMode != null)
            return;

        Conversation conv = item.getConversation();

        ConversationsCallback parent = getParentCallback();
        if (parent != null) {
            //mMultiSelector.setSelectedPosition(position);
            parent.openConversation(conv);
        }
    }

    public void onSelectionChanged() {
        if (mSelectionTracker.hasSelection() && mActionMode == null) {
            mActionMode = getParentCallback().startSupportActionMode(mActionModeCallback);
            updateActionModeSelectionCount(mSelectionTracker.getSelection().size());
        }
        else if (!mSelectionTracker.hasSelection() && mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
        else if (mActionMode != null) {
            updateActionModeSelectionCount(mSelectionTracker.getSelection().size());
        }
    }

    private void updateActionModeSelectionCount(int count) {
        mActionMode.setTitle(getResources()
            .getQuantityString(R.plurals.context_selected,
                count, count));
        mActionMode.invalidate();
    }

    @Override
    public void onContactInvalidated(String userId) {
        Activity parent = getActivity();
        if (parent != null) {
            parent.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mListAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    public boolean hasListItems() {
        return mListAdapter != null && mListAdapter.getRealItemCount() > 0;
    }

    /** Utility for child classes to delete selected threads. */
    protected void deleteSelectedThreads() {
        boolean addGroupCheckbox = false;
        int checkedCount = 0;

        final Selection<Long> selected = getSelectedPositions();
        final List<Conversation> list = new ArrayList<>(selected.size());
        for (long id: selected) {
            Conversation conv = Conversation.loadFromId(getContext(), id);
            if (!addGroupCheckbox && conv.isGroupChat() &&
                conv.getGroupMembership() == MyMessages.Groups.MEMBERSHIP_MEMBER) {
                addGroupCheckbox = true;
            }
            list.add(conv);
        }

        final boolean hasGroupCheckbox = addGroupCheckbox;
        MaterialDialog.Builder builder = new MaterialDialog.Builder(getContext())
            .customView(R.layout.dialog_text2_check, false)
            .positiveText(android.R.string.ok)
            .positiveColorRes(R.color.button_danger)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    boolean promptCheckBoxChecked = false;
                    if (hasGroupCheckbox) {
                        CheckBox promptCheckbox = dialog
                            .getCustomView().findViewById(R.id.promptCheckbox);
                        promptCheckBoxChecked = promptCheckbox.isChecked();
                    }

                    for (Conversation conv : list) {
                        boolean hasLeftGroup = conv.isGroupChat() &&
                            (conv.getGroupMembership() == MyMessages.Groups.MEMBERSHIP_PARTED ||
                                conv.getGroupMembership() == MyMessages.Groups.MEMBERSHIP_KICKED);
                        conv.delete(hasGroupCheckbox ? promptCheckBoxChecked : hasLeftGroup);
                    }
                    getListAdapter().notifyDataSetChanged();
                }
            })
            .negativeText(android.R.string.cancel);

        MaterialDialog dialog = builder.build();

        ((TextView) dialog.getCustomView().findViewById(android.R.id.text1))
            .setText(getResources().getQuantityString(R.plurals.confirm_will_delete_threads, checkedCount));

        if (addGroupCheckbox) {
            TextView text2 = dialog.getCustomView().findViewById(android.R.id.text2);
            text2.setText(R.string.delete_threads_groups_disclaimer);
            text2.setVisibility(View.VISIBLE);

            CheckBox promptCheckbox = dialog.getCustomView().findViewById(R.id.promptCheckbox);
            promptCheckbox.setText(getResources()
                .getQuantityString(R.plurals.delete_threads_leave_groups, checkedCount));
            promptCheckbox.setVisibility(View.VISIBLE);
        }

        dialog.show();
    }

    // action mode callback methods for child classes

    /** For use to child classes. */
    protected abstract boolean onCreateActionMode(ActionMode mode, Menu menu);

    /** For use to child classes. */
    protected abstract boolean onPrepareActionMode(ActionMode mode, Menu menu);

    /** For use to child classes. */
    protected abstract boolean onActionItemClicked(ActionMode mode, MenuItem item);

    private final class ActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return AbstractConversationsFragment.this
                .onActionItemClicked(mode, item);
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return AbstractConversationsFragment.this.onCreateActionMode(mode, menu);
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return AbstractConversationsFragment.this.onPrepareActionMode(mode, menu);
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            mActionMode = null;
            mSelectionTracker.clearSelection();
            mListAdapter.notifyDataSetChanged();
        }
    }

}

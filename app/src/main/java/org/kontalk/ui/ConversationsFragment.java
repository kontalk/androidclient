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
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback;
import com.bignerdranch.android.multiselector.MultiSelector;
import com.github.clans.fab.FloatingActionMenu;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.arch.paging.PagedList;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
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


public class ConversationsFragment extends Fragment
        implements Contact.ContactChangeListener, ConversationListAdapter.OnItemClickListener {
    static final String TAG = ConversationsActivity.TAG;

    private static final String STATE_MULTISELECTOR = ConversationsFragment.class
        .getName() + ".multiselector";

    View mEmptyView;
    private RecyclerView mListView;
    ConversationListAdapter mListAdapter;
    private ConversationsViewModel mViewModel;

    private MultiSelector mMultiSelector;
    private ModalMultiSelectorCallback mActionModeCallback;
    private ActionMode mActionMode;

    private boolean mDualPane;

    FloatingActionMenu mAction;
    boolean mActionVisible;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(ConversationsViewModel.class);
        mMultiSelector = new MultiSelector();
        mActionModeCallback = new ActionModeCallback();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAction = view.findViewById(R.id.action);
        mAction.setClosedOnTouchOutside(true);
        mActionVisible = true;

        view.findViewById(R.id.action_compose).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseContact(false);
            }
        });
        view.findViewById(R.id.action_compose_group).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                chooseContact(true);
            }
        });

        mEmptyView = view.findViewById(android.R.id.empty);

        mListView = view.findViewById(android.R.id.list);
        mListView.setLayoutManager(new LinearLayoutManager(view.getContext(),
            LinearLayoutManager.VERTICAL, false));

        mListAdapter = new ConversationListAdapter(getContext(), mMultiSelector, this);
        mListView.setAdapter(mListAdapter);

        mViewModel.load(getContext());
        mViewModel.getData().observe(this, new Observer<PagedList<Conversation>>() {
            @Override
            public void onChanged(@Nullable PagedList<Conversation> conversations) {
                mListAdapter.submitList(conversations);
                mEmptyView.setVisibility(conversations != null && conversations.size() > 0 ?
                    View.GONE : View.VISIBLE);
            }
        });

        /* TODO
        mListView.addOnScrollListener(new AbsListViewScrollDetector() {
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
        */
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Check to see if we have a frame in which to embed the details
        // fragment directly in the containing UI.
        View detailsFrame = getActivity().findViewById(R.id.fragment_compose_message);
        mDualPane = detailsFrame != null
                && detailsFrame.getVisibility() == View.VISIBLE;

        if (savedInstanceState != null) {
            mMultiSelector.restoreSelectionStates(savedInstanceState
                .getBundle(STATE_MULTISELECTOR));
        }

        if (mMultiSelector.isSelectable()) {
            mActionModeCallback.setClearOnPrepare(false);
            mActionMode = getParentActivity().startSupportActionMode(mActionModeCallback);
        }

        if (mDualPane) {
            // TODO restore state
            /* TODO
            mListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            mListView.setItemsCanFocus(true);
            */
        }
        else {
            /* TODO
            mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            */
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(STATE_MULTISELECTOR, mMultiSelector.saveSelectionStates());
        // TODO save state
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return isActionModeActive() || super.onOptionsItemSelected(item);
    }

    public boolean isActionMenuOpen() {
        return mAction != null && mAction.isOpened();
    }

    public void closeActionMenu() {
        if (isActionMenuOpen())
            mAction.close(true);
    }

    public boolean isActionModeActive() {
        return mMultiSelector.getSelectedPositions().size() > 0;
    }

    private void archiveSelectedThreads() {
        List<Integer> selected = mMultiSelector.getSelectedPositions();
        for (int position: selected) {
            mViewModel.getData().getValue().get(position)
                .archive();
        }
    }

    private void deleteSelectedThreads() {
        boolean addGroupCheckbox = false;
        int checkedCount = 0;

        final List<Integer> selected = mMultiSelector.getSelectedPositions();
        final List<Conversation> list = new ArrayList<>(selected.size());
        for (int position: selected) {
            Conversation conv = mViewModel.getData().getValue().get(position);
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
                    mListAdapter.notifyDataSetChanged();
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

    private Conversation getCheckedItem() {
        List<Integer> selected = mMultiSelector.getSelectedPositions();
        if (selected.size() != 1)
            throw new IllegalStateException("checked items count must be exactly 1");

        return mViewModel.getData().getValue().get(selected.get(0));
    }

    private void stickSelectedThread() {
        Conversation conv = getCheckedItem();
        if (conv != null) {
            conv.setSticky(!conv.isSticky());
        }
        mListAdapter.notifyDataSetChanged();
    }

    public void chooseContact(boolean multiselect) {
        ConversationsActivity parent = getParentActivity();
        if (parent != null)
            parent.showContactPicker(multiselect);
    }

    public ConversationsActivity getParentActivity() {
        return (ConversationsActivity) getActivity();
    }

    public ConversationListAdapter getListAdapter() {
        return mListAdapter;
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
        if (isActionMenuOpen())
            mAction.close(false);
    }

    @Override
    public void onItemClick(ConversationListItem item, int position) {
        Conversation conv = item.getConversation();

        ConversationsActivity parent = getParentActivity();
        if (parent != null) {
            parent.openConversation(conv, position);
        }
    }

    @Override
    public void onStartMultiselect() {
        ConversationsActivity parent = getParentActivity();
        if (parent != null) {
            mActionMode = parent.startSupportActionMode(mActionModeCallback);
        }
    }

    @Override
    public void onItemSelected(ConversationListItem item, int position) {
        if (mActionMode != null) {
            int count = mMultiSelector.getSelectedPositions().size();
            if (count == 0) {
                mActionMode.finish();
            }
            else {
                mActionMode.setTitle(getResources()
                    .getQuantityString(R.plurals.context_selected,
                        count, count));
                mActionMode.invalidate();
            }
        }
    }

    /** Used only in fragment contexts. */
    public void endConversation(AbstractComposeFragment composer) {
        getFragmentManager().beginTransaction().remove(composer).commit();
    }

    public final boolean isFinishing() {
        return (getActivity() == null ||
                (getActivity() != null && getActivity().isFinishing())) ||
                isRemoving();
    }

    @Override
    public void onContactInvalidated(String userId) {
        // TODO mListAdapter.refresh();
    }

    public boolean hasListItems() {
        return mListAdapter != null && mListAdapter.getItemCount() > 0;
    }

    public boolean isDualPane() {
        return mDualPane;
    }

    private final class ActionModeCallback extends ModalMultiSelectorCallback {

        public ActionModeCallback() {
            super(mMultiSelector);
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.menu_archive:
                    mode.finish();
                    archiveSelectedThreads();
                    mMultiSelector.clearSelections();
                    return true;
                case R.id.menu_delete:
                    mode.finish();
                    deleteSelectedThreads();
                    mMultiSelector.clearSelections();
                    return true;
                case R.id.menu_sticky:
                    mode.finish();
                    stickSelectedThread();
                    mMultiSelector.clearSelections();
                    return true;
            }
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            super.onCreateActionMode(mode, menu);
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.conversation_list_ctx, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            super.onPrepareActionMode(mode, menu);
            boolean singleItem = (mMultiSelector.getSelectedPositions().size() == 1);
            menu.findItem(R.id.menu_sticky).setVisible(singleItem);
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode actionMode) {
            super.onDestroyActionMode(actionMode);
            mActionMode = null;
            // TODO getListView().clearChoices();
            //mListAdapter.notifyDataSetChanged();
        }
    }

}

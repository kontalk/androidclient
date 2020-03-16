/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import com.github.clans.fab.FloatingActionMenu;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.recyclerview.selection.Selection;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.ui.adapter.ConversationListAdapter;


public class ConversationsFragment extends AbstractConversationsFragment
        implements ConversationListAdapter.OnFooterClickListener {
    static final String TAG = ConversationsActivity.TAG;

    FloatingActionMenu mAction;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAction = view.findViewById(R.id.action);
        mAction.setClosedOnTouchOutside(true);

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
    }

    @Override
    protected boolean isArchived() {
        return false;
    }

    @Override
    protected boolean isSingleSelection() {
        return isDualPane();
    }

    private boolean isDualPane() {
        View detailsFrame = getActivity().findViewById(R.id.fragment_compose_message);
        return detailsFrame != null && detailsFrame.getVisibility() == View.VISIBLE;
    }

    @Override
    protected void onAdapterCreated(ConversationListAdapter adapter) {
        adapter.setFooterListener(this);
    }

    @Override
    protected boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.conversation_list_ctx, menu);
        return true;
    }

    @Override
    protected boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        boolean singleItem = (getSelectedPositions().size() == 1);
        menu.findItem(R.id.menu_sticky).setVisible(singleItem);
        return true;
    }

    @Override
    protected boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_archive:
                archiveSelectedThreads();
                mode.finish();
                return true;
            case R.id.menu_delete:
                deleteSelectedThreads();
                mode.finish();
                return true;
            case R.id.menu_sticky:
                stickSelectedThread();
                mode.finish();
                return true;
        }
        return false;
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

    private void archiveSelectedThreads() {
        final Context context = getContext();
        if (context == null)
            return;

        Selection<Long> selected = getSelectedPositions();
        for (long id: selected) {
            MessagesProviderClient.setArchived(context, id, true);
        }
    }

    private Conversation getCheckedItem() {
        Selection<Long> selected = getSelectedPositions();
        if (selected.size() != 1)
            throw new IllegalStateException("checked items count must be exactly 1");

        return Conversation.loadFromId(getContext(), selected.iterator().next());
    }

    private void stickSelectedThread() {
        Conversation conv = getCheckedItem();
        if (conv != null) {
            conv.setSticky(!conv.isSticky());
        }
        getListAdapter().notifyDataSetChanged();
    }

    public void chooseContact(boolean multiselect) {
        // we can cast to the activity because it can only happen there
        ConversationsActivity parent = getParentActivity();
        if (parent != null)
            parent.showContactPicker(multiselect);
    }

    private ConversationsActivity getParentActivity() {
        return (ConversationsActivity) getActivity();
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
    public void onFooterClick() {
        // we can cast to the activity because it can only happen there
        ConversationsActivity parent = getParentActivity();
        if (parent != null)
            parent.startArchivedConversations();
    }

    /** Used only in fragment contexts. */
    public void endConversation(AbstractComposeFragment composer) {
        getFragmentManager().beginTransaction().remove(composer).commit();
    }

}

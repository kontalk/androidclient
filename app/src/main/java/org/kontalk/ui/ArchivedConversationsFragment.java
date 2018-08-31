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

import java.util.List;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.kontalk.R;
import org.kontalk.ui.adapter.ConversationListAdapter;


public class ArchivedConversationsFragment extends AbstractConversationsFragment {
    static final String TAG = ConversationsActivity.TAG;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.archived_conversation_list, container, false);
    }

    @Override
    protected boolean isArchived() {
        return true;
    }

    @Override
    protected boolean isSingleSelection() {
        return false;
    }

    @Override
    protected void onAdapterCreated(ConversationListAdapter adapter) {
        // nothing to do
    }

    @Override
    protected boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.archived_conversation_list_ctx, menu);
        return true;
    }

    @Override
    protected boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    protected boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_unarchive:
                unarchiveSelectedThreads();
                mode.finish();
                return true;
            case R.id.menu_delete:
                deleteSelectedThreads();
                mode.finish();
                return true;
        }
        return false;
    }

    private void unarchiveSelectedThreads() {
        List<Integer> selected = getSelectedPositions();
        for (int position: selected) {
            getViewModel().getData().getValue().get(position)
                .unarchive();
        }
    }

}

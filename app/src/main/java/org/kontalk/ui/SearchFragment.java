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

import org.kontalk.R;
import org.kontalk.data.SearchItem;
import org.kontalk.ui.adapter.SearchListAdapter;
import org.kontalk.ui.view.SearchListItem;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;


/**
 * A basic search fragment for the entire database.
 * @author Daniele Ricci
 */
public class SearchFragment extends ListFragment {

    private Cursor mCursor;
    private String mQuery;
    private SearchListAdapter mListAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.search_list, container, false);

        // TODO empty list text view?

        return view;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        SearchListItem item = (SearchListItem) v;
        long msgId = item.getSearchItem().getMessageId();
        long threadId = item.getSearchItem().getThreadId();
        Intent i = ComposeMessage.fromConversation(getActivity(), threadId);
        i.putExtra(ComposeMessage.EXTRA_MESSAGE, msgId);
        i.putExtra(ComposeMessage.EXTRA_HIGHLIGHT, mQuery);
        startActivity(i);
    }

    public void setQuery(String query) {
        mQuery = query;

        Activity parent = getActivity();
        if (parent != null) {
            mCursor = SearchItem.query(parent, mQuery);
            if (mCursor != null)
                getActivity().startManagingCursor(mCursor);

            mListAdapter = new SearchListAdapter(parent, mCursor);
            // TODO mListAdapter.setOnContentChangedListener(mContentChangedListener);
            setListAdapter(mListAdapter);
        }
    }

}

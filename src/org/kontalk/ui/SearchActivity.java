/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.MenuItem;


/**
 * A basic search activity for the entire database.
 * @author Daniele Ricci
 */
public class SearchActivity extends SherlockListActivity {
    private static final String TAG = SearchActivity.class.getSimpleName();

    private Cursor mCursor;
    private String mQuery;
    private SearchListAdapter mListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_list);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            mQuery = intent.getStringExtra(SearchManager.QUERY);
            Log.i(TAG, "searching: " + mQuery);
            setTitle(getResources().getString(R.string.title_search, mQuery));

            mCursor = SearchItem.query(this, mQuery);
            startManagingCursor(mCursor);

            mListAdapter = new SearchListAdapter(this, mCursor);
            // TODO mListAdapter.setOnContentChangedListener(mContentChangedListener);
            setListAdapter(mListAdapter);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        SearchListItem item = (SearchListItem) v;
        long msgId = item.getSearchItem().getMessageId();
        long threadId = item.getSearchItem().getThreadId();
        Intent i = ComposeMessage.fromConversation(this, threadId);
        i.putExtra(ComposeMessage.EXTRA_MESSAGE, msgId);
        i.putExtra(ComposeMessage.EXTRA_HIGHLIGHT, mQuery);
        startActivity(i);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                startActivity(new Intent(this, ConversationList.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

}

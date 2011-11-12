package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.data.SearchItem;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;


/**
 * A basic search activity for the entire database.
 * @author Daniele Ricci
 */
public class SearchActivity extends ListActivity {
    private static final String TAG = SearchActivity.class.getSimpleName();

    private Cursor mCursor;
    private String mQuery;
    private SearchListAdapter mListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_list);

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

}

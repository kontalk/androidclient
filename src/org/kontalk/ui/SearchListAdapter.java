package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.data.SearchItem;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;


public class SearchListAdapter extends CursorAdapter {

    private static final String TAG = SearchListAdapter.class.getSimpleName();

    private final LayoutInflater mFactory;
    private OnContentChangedListener mOnContentChangedListener;

    public SearchListAdapter(Context context, Cursor cursor) {
        super(context, cursor, false);
        mFactory = LayoutInflater.from(context);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!(view instanceof SearchListItem)) {
            Log.e(TAG, "Unexpected bound view: " + view);
            return;
        }

        SearchListItem headerView = (SearchListItem) view;
        SearchItem found = SearchItem.fromCursor(context, cursor);
        headerView.bind(context, found);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mFactory.inflate(R.layout.search_list_item, parent, false);
    }

    public interface OnContentChangedListener {
        void onContentChanged(SearchListAdapter adapter);
    }

    public void setOnContentChangedListener(OnContentChangedListener l) {
        mOnContentChangedListener = l;
    }

    @Override
    protected void onContentChanged() {
        Cursor c = getCursor();
        if (c != null && !c.isClosed() && mOnContentChangedListener != null) {
            mOnContentChangedListener.onContentChanged(this);
        }
    }
}

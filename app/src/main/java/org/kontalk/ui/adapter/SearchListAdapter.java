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

package org.kontalk.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.data.SearchItem;
import org.kontalk.ui.SearchActivity;
import org.kontalk.ui.view.SearchListItem;


public class SearchListAdapter extends CursorAdapter {

    private static final String TAG = SearchActivity.TAG;

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

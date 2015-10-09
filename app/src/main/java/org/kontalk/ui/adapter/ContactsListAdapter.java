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

package org.kontalk.ui.adapter;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.ui.ContactsListActivity;
import org.kontalk.ui.view.ContactsListItem;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.AbsListView.RecyclerListener;
import android.widget.TextView;

import com.twotoasters.sectioncursoradapter.SectionCursorAdapter;


public class ContactsListAdapter extends SectionCursorAdapter {
    private static final String TAG = ContactsListActivity.TAG;

    private final LayoutInflater mFactory;
    private OnContentChangedListener mOnContentChangedListener;

    public ContactsListAdapter(Context context, ListView list) {
        super(context, null, false, null);
        mFactory = LayoutInflater.from(context);

        list.setRecyclerListener(new RecyclerListener() {
            public void onMovedToScrapHeap(View view) {
                if (view instanceof ContactsListItem) {
                    ((ContactsListItem) view).unbind();
                }
            }
        });
    }

    @Override
    protected View newSectionView(Context context, Object item, ViewGroup parent) {
        return getLayoutInflater().inflate(R.layout.item_section, parent, false);
    }

    @Override
    protected void bindSectionView(View convertView, Context context, int position, Object item) {
        ((TextView) convertView).setText((String) item);
    }

    @Override
    protected View newItemView(Context context, Cursor cursor, ViewGroup parent) {
        return mFactory.inflate(R.layout.contacts_list_item, parent, false);
    }

    @Override
    protected void bindItemView(View convertView, Context context, Cursor cursor) {
        if (!(convertView instanceof ContactsListItem)) {
            Log.e(TAG, "Unexpected bound view: " + convertView);
            return;
        }

        ContactsListItem headerView = (ContactsListItem) convertView;
        Contact contact = Contact.fromUsersCursor(cursor);
        headerView.bind(context, contact);
    }

    @Override
    protected Object getSectionFromCursor(Cursor cursor) {
        return Contact.getStringForSection(cursor);
    }

    public interface OnContentChangedListener {
        void onContentChanged(ContactsListAdapter adapter);
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

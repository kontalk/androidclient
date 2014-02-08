/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.regex.Pattern;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.MyMessages.Messages;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.RecyclerListener;
import android.widget.CursorAdapter;
import android.widget.ListView;

public class MessageListAdapter extends CursorAdapter {

    private static final String TAG = MessageListAdapter.class.getSimpleName();

    private final LayoutInflater mFactory;
    private final Pattern mHighlight;
    private OnContentChangedListener mOnContentChangedListener;

    private Contact mContact;

    public MessageListAdapter(Context context, Cursor cursor, Pattern highlight, ListView list) {
        super(context, cursor, false);
        mFactory = LayoutInflater.from(context);
        mHighlight = highlight;

        list.setRecyclerListener(new RecyclerListener() {
            public void onMovedToScrapHeap(View view) {
                if (view instanceof MessageListItem) {
                    ((MessageListItem) view).unbind();
                }
            }
        });
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!(view instanceof MessageListItem)) {
            Log.e(TAG, "unexpected bound view: " + view);
            return;
        }

        MessageListItem headerView = (MessageListItem) view;
        CompositeMessage msg = CompositeMessage.fromCursor(context, cursor);
        if (msg.getDirection() == Messages.DIRECTION_IN && mContact == null)
        	mContact = Contact.findByUserId(context, msg.getSender());

        headerView.bind(context, msg, mContact, mHighlight);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mFactory.inflate(R.layout.message_list_item, parent, false);
    }

    public interface OnContentChangedListener {
        void onContentChanged(MessageListAdapter adapter);
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

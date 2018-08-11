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

package org.kontalk.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.arch.paging.PositionalDataSource;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;
import android.support.annotation.NonNull;

import org.kontalk.provider.MyMessages;


/**
 * Data source for conversations (threads table)
 * TODO some way of altering the archived flag
 */
public class ConversationsDataSource extends PositionalDataSource<Conversation> {

    private static final String[] COUNT_PROJECTION = new String[] {
        MyMessages.Threads._COUNT,
    };

    private final Context mContext;
    private final ContentResolver mContentResolver;

    public ConversationsDataSource(Context context, Handler handler) {
        mContext = context.getApplicationContext();
        mContentResolver = mContext.getContentResolver();
        mContentResolver.registerContentObserver(MyMessages.Threads.CONTENT_URI,
            true, new ChangeObserver(handler));
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams params, @NonNull LoadInitialCallback<Conversation> callback) {
        int totalCount = countItems(false);
        if (totalCount == 0) {
            callback.onResult(Collections.<Conversation>emptyList(), 0, 0);
            return;
        }

        // bound the size requested, based on known count
        final int firstLoadPosition = computeInitialLoadPosition(params, totalCount);
        final int firstLoadSize = computeInitialLoadSize(params, firstLoadPosition, totalCount);

        List<Conversation> list = loadRange(firstLoadPosition, firstLoadSize);
        if (list != null && list.size() == firstLoadSize) {
            callback.onResult(list, firstLoadPosition, totalCount);
        }
        else {
            // null list, or size doesn't match request - DB modified between count and load
            invalidate();
        }
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params, @NonNull LoadRangeCallback<Conversation> callback) {
        List<Conversation> list = loadRange(params.startPosition, params.loadSize);
        if (list != null) {
            callback.onResult(list);
        }
        else {
            invalidate();
        }

    }

    private int countItems(boolean archived) {
        Cursor cursor = mContentResolver.query(MyMessages.Threads.CONTENT_URI,
            COUNT_PROJECTION, MyMessages.Threads.ARCHIVED + " = " + (archived ? "1" : "0"),
            null, null);
        try {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
        finally {
            cursor.close();
        }
    }

    private List<Conversation> getConversations(boolean archived, int limit, int offset) {
        Cursor cursor = mContentResolver.query(MyMessages.Threads.CONTENT_URI.buildUpon()
                .appendQueryParameter("limit", offset + "," + limit).build(),
            Conversation.PROJECTION, MyMessages.Threads.ARCHIVED + " = " + (archived ? "1" : "0"),
            null, MyMessages.Threads.DEFAULT_SORT_ORDER);

        List<Conversation> conversations = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                conversations.add(Conversation.createFromCursor(mContext, cursor));
            }
        }
        finally {
            cursor.close();
        }
        return conversations;
    }

    /**
     * Return the rows from startPos to startPos + loadCount
     */
    private List<Conversation> loadRange(int startPosition, int loadCount) {
        return getConversations(false, loadCount, startPosition);
    }

    class ChangeObserver extends ContentObserver {
        public ChangeObserver(Handler handler) {
            super(handler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            invalidate();
        }
    }

}

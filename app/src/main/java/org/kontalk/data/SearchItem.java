/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Messages.Fulltext;
import org.kontalk.reporting.ReportingManager;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;


/**
 * A message item found by a search.
 * @author Daniele Ricci
 */
public class SearchItem {
    private static final String[] SEARCH_PROJECTION = {
        Fulltext._ID + " AS " + Messages._ID,
        Fulltext.THREAD_ID,
        Fulltext.CONTENT
    };

    private final long mId;
    private final long mThreadId;
    private final String mText;
    private final Conversation mConversation;

    private SearchItem(Context context, long id, long threadId, String text) {
        mId = id;
        mThreadId = threadId;
        mText = text;
        mConversation = Conversation.loadFromId(context, threadId);
    }

    public long getMessageId() {
        return mId;
    }

    public long getThreadId() {
        return mThreadId;
    }

    public String getUserDisplayName() {
        if (mConversation != null) {
            if (mConversation.isGroupChat()) {
                return mConversation.getGroupSubject();
            }
            else {
                final Contact contact = mConversation.getContact();
                String name;
                if (contact != null)
                    name = contact.getName() + " <" + contact.getNumber() + ">";
                else
                    name = mConversation.getRecipient();
                return name;
            }
        }
        return null;
    }

    public String getText() {
        return mText;
    }

    public static SearchItem fromCursor(Context context, Cursor cursor) {
        long id = cursor.getLong(0);
        long threadId = cursor.getLong(1);
        String text = cursor.getString(2);
        return new SearchItem(context, id, threadId, text);
    }

    public static Cursor query(Context context, String query) {
        try {
            // TODO enhanced queries?
            return context.getContentResolver().query(Fulltext.CONTENT_URI
                    .buildUpon().appendQueryParameter("pattern", query + "*").build(),
                SEARCH_PROJECTION, null, null, null);
        }
        catch (SQLiteException e) {
            ReportingManager.logException(e);
            return null;
        }
    }
}

package org.kontalk.data;

import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Messages.Fulltext;

import android.content.Context;
import android.database.Cursor;


/**
 * A message item found by a search.
 * @author Daniele Ricci
 */
public class SearchItem {
    public static final String[] SEARCH_PROJECTION = {
        Fulltext._ID + " AS " + Messages._ID,
        Messages.THREAD_ID,
        Messages.CONTENT
    };

    protected final long mId;
    protected final long mThreadId;
    protected String mUserId;
    protected final String mText;
    protected Contact mContact;

    private SearchItem(Context context, long id, long threadId, String text) {
        mId = id;
        mThreadId = threadId;
        mText = text;
        Conversation conv = Conversation.loadFromId(context, threadId);
        if (conv != null) {
            mUserId = conv.getRecipient();
            mContact = conv.getContact();
        }
    }

    public long getMessageId() {
        return mId;
    }

    public long getThreadId() {
        return mThreadId;
    }

    public String getUserId() {
        return mUserId;
    }

    public String getText() {
        return mText;
    }

    public Contact getContact() {
        return mContact;
    }

    public static SearchItem fromCursor(Context context, Cursor cursor) {
        long id = cursor.getLong(0);
        long threadId = cursor.getLong(1);
        String text = cursor.getString(2);
        return new SearchItem(context, id, threadId, text);
    }

    public static Cursor query(Context context, String query) {
        // TODO enhanced queries?
        return context.getContentResolver().query(Fulltext.CONTENT_URI
                    .buildUpon().appendQueryParameter("pattern", query + "*").build(),
                SEARCH_PROJECTION, null, null, null);
    }
}

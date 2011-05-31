package org.nuntius.data;

import org.nuntius.provider.MyMessages.Threads;

import android.content.AsyncQueryHandler;
import android.content.Context;
import android.database.Cursor;

public class Conversation {

    private static final String[] ALL_THREADS_PROJECTION = {
        Threads._ID,
        Threads.PEER,
        Threads.DIRECTION,
        Threads.COUNT,
        Threads.UNREAD,
        Threads.MIME,
        Threads.CONTENT,
        Threads.TIMESTAMP
    };

    private final Context mContext;

    private long mThreadId;

    private String mRecipient;
    private long mDate;
    private int mMessageCount;
    private String mSubject;
    private int mUnreadCount;
    private boolean mHasAttachment;
    private boolean mHasError;

    private Conversation(Context context) {
        mContext = context;
        mThreadId = 0;
    }

    private Conversation(Context context, Cursor c) {
        mContext = context;
        synchronized (this) {
            mThreadId = c.getLong(c.getColumnIndex(Threads._ID));
            mDate = c.getLong(c.getColumnIndex(Threads.TIMESTAMP));

            mRecipient = c.getString(c.getColumnIndex(Threads.PEER));
            mSubject = c.getString(c.getColumnIndex(Threads.CONTENT));

            mUnreadCount = c.getInt(c.getColumnIndex(Threads.UNREAD));
            mMessageCount = c.getInt(c.getColumnIndex(Threads.COUNT));

            // TODO attachments & errors
        }
    }

    public static Conversation createNew(Context context) {
        return new Conversation(context);
    }

    public static Conversation from(Context context, Cursor cursor) {
        return new Conversation(context, cursor);
    }

    public long getDate() {
        return mDate;
    }

    public void setDate(long mDate) {
        this.mDate = mDate;
    }

    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String mSubject) {
        this.mSubject = mSubject;
    }

    public String getRecipient() {
        return mRecipient;
    }

    public void setRecipient(String mRecipient) {
        this.mRecipient = mRecipient;
    }

    public int getMessageCount() {
        return mMessageCount;
    }

    public int getUnreadCount() {
        return mUnreadCount;
    }

    public long getThreadId() {
        return mThreadId;
    }

    public static void startQuery(AsyncQueryHandler handler, int token) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, null, null, Threads.DEFAULT_SORT_ORDER);
    }
}

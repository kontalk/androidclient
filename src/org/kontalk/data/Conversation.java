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

package org.kontalk.data;

import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.ui.MessagingNotification;

import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;


/**
 * A class represeting a conversation thread.
 * @author Daniele Ricci
 * @version 1.0
 */
public class Conversation {

    private static final String[] ALL_THREADS_PROJECTION = {
        Threads._ID,
        Threads.PEER,
        Threads.COUNT,
        Threads.UNREAD,
        Threads.MIME,
        Threads.CONTENT,
        Threads.TIMESTAMP,
        Threads.STATUS,
        Threads.ENCRYPTED,
        Threads.DRAFT,
        Threads.REQUEST_STATUS,
    };

    private final int COLUMN_ID = 0;
    private final int COLUMN_PEER = 1;
    private final int COLUMN_COUNT = 2;
    private final int COLUMN_UNREAD = 3;
    private final int COLUMN_MIME = 4;
    private final int COLUMN_CONTENT = 5;
    private final int COLUMN_TIMESTAMP = 6;
    private final int COLUMN_STATUS = 7;
    private final int COLUMN_ENCRYPTED = 8;
    private final int COLUMN_DRAFT = 9;
    private final int COLUMN_REQUEST_STATUS = 10;

    private final Context mContext;

    private long mThreadId;
    private Contact mContact;

    private String mRecipient;
    private long mDate;
    private int mMessageCount;
    private String mMime;
    private String mSubject;
    private int mUnreadCount;
    private int mStatus;
    private String mDraft;
    private String mNumberHint;
    private boolean mEncrypted;
    private int mRequestStatus;

    private Conversation(Context context) {
        mContext = context;
        mThreadId = 0;
    }

    private Conversation(Context context, Cursor c) {
        mContext = context;
        synchronized (this) {
            mThreadId = c.getLong(COLUMN_ID);
            mDate = c.getLong(COLUMN_TIMESTAMP);

            mRecipient = c.getString(COLUMN_PEER);
            mMime = c.getString(COLUMN_MIME);
            mSubject = c.getString(COLUMN_CONTENT);

            mUnreadCount = c.getInt(COLUMN_UNREAD);
            mMessageCount = c.getInt(COLUMN_COUNT);
            mStatus = c.getInt(COLUMN_STATUS);
            mEncrypted = c.getInt(COLUMN_ENCRYPTED) != 0;
            mDraft = c.getString(COLUMN_DRAFT);
            mRequestStatus = c.getInt(COLUMN_REQUEST_STATUS);

            loadContact();
        }
    }

    public static Conversation createNew(Context context) {
        return new Conversation(context);
    }

    public static Conversation createFromCursor(Context context, Cursor cursor) {
        return new Conversation(context, cursor);
    }

    public static Conversation loadFromUserId(Context context, String userId) {
        Conversation cv = null;
        Cursor cp = context.getContentResolver().query(Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, Threads.PEER + " = ?", new String[] { userId }, null);
        if (cp.moveToFirst())
            cv = createFromCursor(context, cp);

        cp.close();
        return cv;
    }

    public static Conversation loadFromId(Context context, long id) {
        Conversation cv = null;
        Cursor cp = context.getContentResolver().query(
                ContentUris.withAppendedId(Threads.CONTENT_URI, id),
                ALL_THREADS_PROJECTION, null, null, null);
        if (cp.moveToFirst())
            cv = createFromCursor(context, cp);

        cp.close();
        return cv;
    }

    private void loadContact() {
        mContact = Contact.findByUserId(mContext, mRecipient, mNumberHint);
    }

    public Contact getContact() {
        return mContact;
    }

    public long getDate() {
        return mDate;
    }

    public void setDate(long mDate) {
        this.mDate = mDate;
    }

    public String getMime() {
		return mMime;
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
        // reload contact
        loadContact();
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

    public int getStatus() {
        return mStatus;
    }

    public boolean isEncrypted() {
    	return mEncrypted;
    }

    public String getDraft() {
        return mDraft;
    }

    public int getRequestStatus() {
    	return mRequestStatus;
    }

    public String getNumberHint() {
        return mNumberHint;
    }

    /**
     * Sets a phone number hint that will be used if there is no match in the
     * users database.
     */
    public void setNumberHint(String numberHint) {
        mNumberHint = numberHint;
    }

    public static void startQuery(AsyncQueryHandler handler, int token) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, null, null, Threads.DEFAULT_SORT_ORDER);
    }

    public static void startQuery(AsyncQueryHandler handler, int token, long threadId) {
        // cancel previous operations
        handler.cancelOperation(token);
        handler.startQuery(token, null, Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, Threads._ID + " = " + threadId, null, Threads.DEFAULT_SORT_ORDER);
    }

    public static Cursor startQuery(Context context) {
        return context.getContentResolver().query(Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, null, null, Threads.DEFAULT_SORT_ORDER);
    }

    public static Cursor startQuery(Context context, long threadId) {
        return context.getContentResolver().query(Threads.CONTENT_URI,
                ALL_THREADS_PROJECTION, Threads._ID + " = " + threadId, null, Threads.DEFAULT_SORT_ORDER);
    }

    public void markAsRead() {
        if (mThreadId > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    MessagesProvider.markThreadAsRead(mContext, mThreadId);

                    MessagingNotification.updateMessagesNotification(mContext.getApplicationContext(), false);
                }
            }).start();
        }
    }

}

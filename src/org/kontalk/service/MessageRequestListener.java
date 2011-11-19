/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.service;

import org.kontalk.client.MessageSender;
import org.kontalk.client.Protocol;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Messages;

import com.google.protobuf.MessageLite;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;


/**
 * A {@link RequestListener} to be used for message requests.
 * Could be subclassed by UI to get notifications about message deliveries.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageRequestListener implements RequestListener {
    private static final String TAG = MessageRequestListener.class.getSimpleName();

    protected final Context mContext;
    protected final ContentResolver mContentResolver;

    public MessageRequestListener(Context context) {
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    @Override
    public void response(RequestJob job, MessageLite response) {
        MessageSender job2 = (MessageSender) job;
        Protocol.MessageSent resGroup = (Protocol.MessageSent) response;

        if (response != null && resGroup.getEntryCount() > 0) {
            Uri uri = job2.getMessageUri();
            Protocol.MessageSentEntry res = resGroup.getEntry(0);

            // message accepted!
            if (res.getStatus() == Protocol.Status.STATUS_SUCCESS) {
                if (res.hasMessageId()) {
                    String msgId = res.getMessageId();
                    if (!TextUtils.isEmpty(msgId)) {
                        /* FIXME this should use changeMessageStatus, but it
                         * won't work because of the newly messageId included
                         * in values, which is not handled by changeMessageStatus
                         */
                        ContentValues values = new ContentValues(2);
                        values.put(Messages.MESSAGE_ID, msgId);
                        values.put(Messages.STATUS, Messages.STATUS_SENT);
                        values.put(Messages.STATUS_CHANGED, System.currentTimeMillis());
                        int n = mContentResolver.update(uri, values, null, null);
                        Log.i(TAG, "message sent and updated (" + n + ")");
                    }
                }
            }

            // message refused!
            else {
                MessagesProvider.changeMessageStatus(mContext, uri,
                        Messages.STATUS_NOTACCEPTED, -1, System.currentTimeMillis());
                Log.w(TAG, "message not accepted by server and updated (" + res.getStatus() + ")");
            }
        }
        else {
            // empty response!? :O
            error(job, new IllegalArgumentException("empty response"));
        }
    }

    @Override
    public boolean error(RequestJob job, Throwable e) {
        Log.e(TAG, "error sending message", e);
        MessageSender job2 = (MessageSender) job;
        Uri uri = job2.getMessageUri();
        MessagesProvider.changeMessageStatus(mContext, uri,
                Messages.STATUS_ERROR, -1, System.currentTimeMillis());
        return false;
    }

    @Override
    public void uploadProgress(RequestJob job, long bytes) {
        // TODO
    }

    @Override
    public void downloadProgress(RequestJob job, long bytes) {
        // TODO
    }

}

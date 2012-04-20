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

import org.kontalk.client.ClientConnection;
import org.kontalk.client.MessageSender;
import org.kontalk.client.Protocol.MessagePostResponse;
import org.kontalk.client.Protocol.MessagePostResponse.MessageSent;
import org.kontalk.client.Protocol.MessagePostResponse.MessageSent.MessageSentStatus;
import org.kontalk.client.TxListener;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MyMessages.Messages;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.protobuf.MessageLite;


/**
 * A {@link RequestListener} to be used for message requests.
 * Could be subclassed by UI to get notifications about message deliveries.
 * @author Daniele Ricci
 */
public class MessageRequestListener implements RequestListener {
    private static final String TAG = MessageRequestListener.class.getSimpleName();
    private static final String selectionOutgoing = Messages.DIRECTION + "=" + Messages.DIRECTION_OUT;

    protected final Context mContext;
    protected final ContentResolver mContentResolver;
    protected final RequestListener mParentListener;

    public MessageRequestListener(Context context, RequestListener parent) {
        mContext = context;
        mContentResolver = context.getContentResolver();
        mParentListener = parent;
    }

    @Override
    public void done(ClientThread client, RequestJob job, String txId) {
        // store the message request
        final MessageSender job2 = (MessageSender) job;
        TxListener listener = new TxListener() {
            @Override
            public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
                Uri uri = job2.getMessageUri();
                MessagePostResponse list = (MessagePostResponse) pack;

                for (int i = 0; i < list.getEntryCount(); i++) {
                    MessageSent res = list.getEntry(i);

                    // message accepted!
                    if (res.getStatus() == MessageSentStatus.STATUS_SUCCESS) {
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
                                int n = mContentResolver.update(uri, values, selectionOutgoing, null);
                                Log.v(TAG, "message sent and updated (" + n + ")");
                            }
                        }
                    }

                    // message refused!
                    else {
                        MessagesProvider.changeMessageStatus(mContext, uri, Messages.DIRECTION_OUT,
                                Messages.STATUS_NOTACCEPTED, -1, System.currentTimeMillis());
                        Log.w(TAG, "message not accepted by server and updated (" + res.getStatus() + ")");
                    }
                }

                return false;
            }
        };

        // set listener for message sent response
        client.setTxListener(txId, listener);
    }

    @Override
    public boolean error(ClientThread client, RequestJob job, Throwable e) {
        // sending is canceled only if the user deleted the message, so no need
        // to update its status
        if (!job.isCanceled()) {
            MessageSender job2 = (MessageSender) job;
            Uri uri = job2.getMessageUri();
            MessagesProvider.changeMessageStatus(mContext, uri, Messages.DIRECTION_OUT,
                    Messages.STATUS_ERROR, -1, System.currentTimeMillis());
        }
        return false;
    }

    @Override
    public void starting(ClientThread client, RequestJob job) {
        mParentListener.starting(client, job);
    }

    @Override
    public void uploadProgress(ClientThread client, RequestJob job, long bytes) {
        mParentListener.uploadProgress(client, job, bytes);
    }

    @Override
    public void downloadProgress(ClientThread client, RequestJob job, long bytes) {
        mParentListener.downloadProgress(client, job, bytes);
    }

}

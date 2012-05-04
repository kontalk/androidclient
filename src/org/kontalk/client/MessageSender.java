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

package org.kontalk.client;

import java.io.IOException;

import org.kontalk.client.Protocol.FileUploadResponse;
import org.kontalk.client.Protocol.FileUploadResponse.FileUploadStatus;
import org.kontalk.client.Protocol.MessagePostRequest;
import org.kontalk.crypto.Coder;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.service.ClientThread;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;
import org.kontalk.ui.MessagingPreferences;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;

import com.google.protobuf.ByteString;


/**
 * A {@link RequestJob} for sending messages.
 * @author Daniele Ricci
 */
public class MessageSender extends RequestJob {

    private final byte[] mContent;
    private final String mPeer;
    private final Uri mUri;
    private final String mMime;
    private final Uri mSourceDataUri;
    private final String mEncryptKey;
    private final boolean mAttachment;

    /** A {@link MessageSender} for raw byte contents. */
    public MessageSender(String userId, byte[] content, String mime, Uri msgUri, String encryptKey, boolean attachment) {
        mContent = content;
        mPeer = userId;
        mUri = msgUri;
        mMime = mime;
        mSourceDataUri = null;
        mEncryptKey = encryptKey;
        mAttachment = attachment;
    }

    /** A {@link MessageSender} for a file {@link Uri}. */
    public MessageSender(String userId, Uri fileUri, String mime, Uri msgUri, String encryptKey) {
        mContent = null;
        mPeer = userId;
        mUri = msgUri;
        mMime = mime;
        mSourceDataUri = fileUri;
        mEncryptKey = encryptKey;
        mAttachment = false;
    }

    public byte[] getContent() {
        return mContent;
    }

    public long getContentLength(Context context) throws IOException {
        if (mContent != null)
            return mContent.length;
        else {
            AssetFileDescriptor fd = context.getContentResolver()
                .openAssetFileDescriptor(mSourceDataUri, "r");
            return fd.getLength();
        }
    }

    public Uri getSourceUri() {
        return mSourceDataUri;
    }

    public Uri getMessageUri() {
        return mUri;
    }

    public String getUserId() {
        return mPeer;
    }

    public String getMime() {
        return mMime;
    }

    public String getEncryptKey() {
        return mEncryptKey;
    }

    public boolean isAttachment() {
        return mAttachment;
    }

    @Override
    public boolean isAsync() {
        return (mSourceDataUri != null);
    }

    @Override
    public boolean isCanceled(Context context) {
        if (!mCancel) {
            // check if the message lives :)
            Cursor c = context.getContentResolver().
                query(mUri, new String[] { Messages._ID }, null, null, null);
            if (!c.moveToFirst())
                super.cancel();
            c.close();
        }

        return mCancel;
    }

    @Override
    public String execute(ClientThread client, RequestListener listener,
            Context context) throws IOException {

        if (mContent != null) {
            MessagePostRequest.Builder b = MessagePostRequest.newBuilder();
            b.addRecipient(mPeer);
            b.setMime(mMime);

            if (mAttachment)
                b.addFlags("attachment");

            byte[] toMessage = null;
            Coder coder = null;
            // check if we have to encrypt the message
            if (mEncryptKey != null) {
                try {
                    coder = MessagingPreferences.getEncryptCoder(mEncryptKey);
                    if (coder != null)
                        toMessage = coder.encrypt(mContent);
                }
                catch (Exception e) {
                    // TODO notify/ask user this message will be sent cleartext
                    coder = null;
                }
            }

            if (coder == null)
                toMessage = mContent;
            else
                b.addFlags("encrypted");

            b.setContent(ByteString.copyFrom(toMessage));
            return client.getConnection().send(b.build());
        }

        else {
            ClientHTTPConnection conn = client.getHttpConnection();
            FileUploadResponse res = conn.message(new String[] { mPeer }, mMime, mSourceDataUri, context, this, mListener);
            if (res != null) {
                // TODO other statuses??
                if (res.getStatus() == FileUploadStatus.STATUS_SUCCESS) {
                    // return the fileid so we can go on with message post
                    return res.getFileId();
                }
            }

            // TODO shall we notify something went wrong?
            return null;
        }

    }
}

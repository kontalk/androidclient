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
import java.io.InputStream;
import java.io.OutputStream;

import org.kontalk.client.Protocol.MessagePostRequest;
import org.kontalk.crypto.Coder;
import org.kontalk.service.ClientThread;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.SendingOutputStream;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;

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
    private ContentObserver mObserver;
    private final String mEncryptKey;

    /** A {@link MessageSender} for raw byte contents. */
    public MessageSender(String userId, byte[] content, String mime, Uri msgUri, String encryptKey) {
        mContent = content;
        mPeer = userId;
        mUri = msgUri;
        mMime = mime;
        mSourceDataUri = null;
        mEncryptKey = encryptKey;
    }

    /** A {@link MessageSender} for a file {@link Uri}. */
    public MessageSender(String userId, Uri fileUri, String mime, Uri msgUri, String encryptKey) {
        mContent = null;
        mPeer = userId;
        mUri = msgUri;
        mMime = mime;
        mSourceDataUri = fileUri;
        mEncryptKey = encryptKey;
    }

    public void observe(Context context, Handler handler) {
        mObserver = new MessageSenderObserver(context, handler);
        context.getContentResolver().registerContentObserver(mUri, false,
                mObserver);
    }

    public void unobserve(Context context) {
        if (mObserver != null)
            context.getContentResolver().unregisterContentObserver(mObserver);
    }

    private final class MessageSenderObserver extends ContentObserver {
        private final Context mContext;

        public MessageSenderObserver(Context context, Handler handler) {
            super(handler);
            mContext = context;
        }

        @Override
        public void onChange(boolean selfChange) {
            // cancel the request if the content doesn't exist
            Cursor c = mContext.getContentResolver()
                .query(mUri, new String[] { BaseColumns._ID }, null, null, null);
            if (c == null || !c.moveToFirst())
                cancel();
            if (c != null)
                c.close();
        }

        @Override
        public boolean deliverSelfNotifications() {
            return false;
        }
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

    @Override
    public String execute(ClientThread client, RequestListener listener,
            Context context) throws IOException {

        OutputStream stream = null;
        MessagePostRequest.Builder b = MessagePostRequest.newBuilder();
        b.addRecipient(mPeer);
        b.setMime(mMime);

        if (mContent != null) {

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
        }

        else {
            //AssetFileDescriptor stat = context.getContentResolver()
            //        .openAssetFileDescriptor(mSourceDataUri, "r");
            //long length = stat.getLength();
            InputStream in = context.getContentResolver().openInputStream(mSourceDataUri);

            InputStream toMessage = null;
            //long toLength = 0;
            Coder coder = null;
            // check if we have to encrypt the message
            if (mEncryptKey != null) {
                try {
                    coder = MessagingPreferences.getEncryptCoder(mEncryptKey);
                    if (coder != null) {
                        toMessage = coder.wrapInputStream(in);
                        //toLength = Coder.getEncryptedLength(length);
                    }
                }
                catch (Exception e) {
                    // TODO notify/ask user this message will be sent cleartext
                    coder = null;
                }
            }

            if (coder == null) {
                toMessage = in;
                //toLength = length;
            }
            else {
                b.addFlags("encrypted");
            }

            byte[] buf = new byte[4096];
            int read;
            ByteString.Output prebuf = ByteString.newOutput();
            while ((read = toMessage.read(buf)) != -1)
                prebuf.write(buf, 0, read);
            // close input file
            toMessage.close();

            // get bytes and close buffer
            b.setContent(prebuf.toByteString());
            prebuf.close();

            // setup the output stream
            // FIXME using a private field
            stream = new SendingOutputStream(client.getConnection().out, client, this, mListener);
        }

        return client.getConnection().send(b.build(), stream);
    }
}

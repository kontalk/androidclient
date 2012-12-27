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

package org.kontalk.xmpp.client;

import java.io.IOException;

import org.jivesoftware.smack.packet.Message;
import org.kontalk.xmpp.message.PlainTextMessage;
import org.kontalk.xmpp.message.VCardMessage;
import org.kontalk.xmpp.provider.MyMessages.Messages;
import org.kontalk.xmpp.service.ClientThread;
import org.kontalk.xmpp.service.RequestJob;
import org.kontalk.xmpp.service.RequestListener;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;


/**
 * A {@link RequestJob} for sending messages.
 * @author Daniele Ricci
 */
public class MessageSender extends RequestJob {
    private static final String TAG = MessageSender.class.getSimpleName();
    // TODO retrieve this from serverinfo
    private static final int MAX_MESSAGE_SIZE = 102400;

    private byte[] mContent;
    private long mCachedLength = -1;
    private final String mPeer;
    private final Uri mUri;
    private final String mMime;
    private final Uri mSourceDataUri;
    private final String mEncryptKey;
    private boolean mAttachment;
    private String mFileId;

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
        if (mCachedLength < 0) {
            if (mContent != null)
                mCachedLength = mContent.length;
            else {
                AssetFileDescriptor fd = null;
                try {
                    fd = context.getContentResolver()
                        .openAssetFileDescriptor(mSourceDataUri, "r");
                    mCachedLength = fd.getLength();
                }
                finally {
                    try {
                        fd.close();
                    }
                    catch (Exception e) {
                        // ignored
                    }
                }
            }
        }

        return mCachedLength;
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

    public String getFileId() {
        return mFileId;
    }

    @Override
    public boolean isAsync(Context context) {
        if (mSourceDataUri != null) {
            if (PlainTextMessage.supportsMimeType(mMime) || VCardMessage.supportsMimeType(mMime)) {
                long length;
                try {
                    length = getContentLength(context);
                }
                catch (Exception e) {
                    length = -1;
                }
                return (length > MAX_MESSAGE_SIZE);
            }
            return true;
        }
        return false;
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
    public String execute(ClientThread client, RequestListener listener, Context context)
            throws IOException {

        if (mContent != null) {
            Message m = new Message(mPeer + "@" + client.getNetwork(), Message.Type.chat);

            // this will generate the packet id
            String txId = m.getPacketID();

            m.setBody(new String(mContent, "UTF-8"));

            // send and return id
            client.getConnection().sendPacket(m);
            return txId;

            /* TODO text message
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
            */
        }

        else {
            /*
             * TODO attachments
            // if message is plain text, send it as normal text if possible
            // FIXME abstract
            if (PlainTextMessage.supportsMimeType(mMime) || VCardMessage.supportsMimeType(mMime)) {
                // if message is bigger than a reasonable size, send it as attachment
                // otherwise proceed to normal text sending
                InputStream in = null;
                long length = -1;
                try {
                    length = getContentLength(context);
                    if (length >= 0 && length <= MAX_MESSAGE_SIZE) {
                        in = context.getContentResolver().openInputStream(mSourceDataUri);
                        // this approach is safe for now because max size is hard-coded
                        mContent = new byte[(int) length];
                        in.read(mContent);
                    }
                }
                catch (Exception e) {
                    Log.w(TAG, "unable to read file contents - sending file as attachment");
                }
                finally {
                    try {
                        in.close();
                    }
                    catch (Exception e) {
                        // ignored
                    }
                }

                // sending file as plain text
                if (length >= 0 && mContent != null) {
                    mAttachment = false;
                    return execute(client, listener, context);
                }
            }

            ClientHTTPConnection conn = client.getHttpConnection();
            FileUploadResponse res = conn.message(new String[] { mPeer }, mMime, mSourceDataUri, context, this, mListener);
            if (res != null) {
                // TODO other statuses??
                if (res.getStatus() == FileUploadStatus.STATUS_SUCCESS) {
                    // return the fileid so we can go on with message post
                    mFileId = res.getFileId();
                    return mFileId;
                }
            }

            // no response or non-success status
            throw new IOException("upload failed");
            */
            return null;
        }

    }
}

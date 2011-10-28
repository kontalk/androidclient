package org.kontalk.client;

import java.io.IOException;

import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;

import com.google.protobuf.MessageLite;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;


/**
 * A {@link RequestJob} for sending plain text messages.
 * @author Daniele Ricci
 * @version 1.0
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
    public MessageLite call(RequestClient client, RequestListener listener,
            Context context) throws IOException {

        if (mContent != null)
            return client.message(new String[] { mPeer },
                    mMime, mContent, this, listener);
        else
            return client.message(new String[] { mPeer },
                    mMime, mSourceDataUri, context, this, listener);
    }
}

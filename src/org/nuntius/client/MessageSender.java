package org.nuntius.client;

import java.io.IOException;

import org.nuntius.service.RequestJob;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;


/**
 * A {@link RequestJob} for sending plain text messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageSender extends RequestJob {

    protected final String mPeer;
    protected final Uri mUri;
    protected final String mMime;
    protected Uri mSourceDataUri;

    /** A {@link MessageSender} for raw byte contents. */
    public MessageSender(String userId, byte[] content, String mime, Uri msgUri) {
        super("message", null, content);

        mPeer = userId;
        mUri = msgUri;
        mMime = mime;
    }

    /** A {@link MessageSender} for a file {@link Uri}. */
    public MessageSender(String userId, Uri fileUri, String mime, Uri msgUri) {
        super("message", null, null);

        mPeer = userId;
        mUri = msgUri;
        mMime = mime;
        mSourceDataUri = fileUri;
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
}

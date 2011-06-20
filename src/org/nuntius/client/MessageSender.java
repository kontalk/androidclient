package org.nuntius.client;

import org.nuntius.service.RequestJob;

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

    public MessageSender(String userId, String text, String mime, Uri uri) {
        super("message", null, null);

        mPeer = userId;
        mUri = uri;
        mMime = mime;
        mContent = text;
    }

    public Uri getUri() {
        return mUri;
    }

    public String getUserId() {
        return mPeer;
    }

    public String getMime() {
        return mMime;
    }
}

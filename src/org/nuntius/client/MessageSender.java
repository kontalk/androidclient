package org.nuntius.client;

import org.nuntius.service.RequestJob;

import android.net.Uri;
import android.text.TextUtils;


/**
 * A basic worker thread for sending messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageSender extends RequestJob {

    private final String mPeer;
    private final String mText;
    private final Uri mUri;

    public MessageSender(String userId, String text, Uri uri) {
        super("message", null, null);

        mPeer = userId;
        mText = text;
        mUri = uri;

        // TODO build xml in a proper manner
        mContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<body><m><t>"+mPeer+"</t><c t=\"text/plain\">"+
            TextUtils.htmlEncode(mText)
        + "</c></m></body>";
    }

    public Uri getUri() {
        return mUri;
    }

    public String getUserId() {
        return mPeer;
    }
}

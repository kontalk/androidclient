package org.nuntius.client;

import java.util.List;
import java.util.Map;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;


/**
 * A basic worker thread for sending messages.
 * @author Daniele Ricci
 * @version 1.0
 */
public class MessageSender extends Thread {
    private static final String TAG = MessageSender.class.getSimpleName();

    private final Context mContext;
    private final EndpointServer mServer;
    private final String mPeer;
    private final String mText;
    private final String mAuthToken;
    private final RequestClient mClient;
    private final Uri mUri;
    private MessageSenderListener mListener;

    public MessageSender(Context context, EndpointServer server, String token, String userId, String text, Uri uri) {
        mContext = context;
        mServer = server;
        mPeer = userId;
        mText = text;
        mAuthToken = token;
        mUri = uri;
        mClient = new RequestClient(mServer, null);
    }

    @Override
    public void run() {
        try {
            RequestClient mClient = new RequestClient(mServer, mAuthToken);
            // TODO build xml in a proper manner
            String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<body><m><t>"+mPeer+"</t><c t=\"text/plain\">"+
                    TextUtils.htmlEncode(mText)
                + "</c></m></body>";

            List<StatusResponse> res = mClient.request("message", null, body);
            if (res.size() > 0) {
                if (mListener != null) {
                    StatusResponse st = res.get(0);
                    if (st.code == StatusResponse.STATUS_SUCCESS) {
                        Map<String, String> extra = st.extra;
                        if (extra != null) {
                            String msgId = extra.get("i");
                            if (!TextUtils.isEmpty(msgId))
                                mListener.onMessageSent(this, msgId);
                        }
                    }
                    else {
                        mListener.onMessageError(this, st.code);
                    }
                }
            }
            else {
                // empty response!? :O
                throw new IllegalArgumentException("invalid arguments");
            }
        }
        catch (Throwable e) {
            if (mListener != null)
                mListener.onError(this, e);
        }
    }

    /**
     * Shuts down this thread gracefully.
     */
    public synchronized void shutdown() {
        Log.w(TAG, "shutting down");
        try {
            mClient.abort();
            interrupt();
            join();
        }
        catch (InterruptedException e) {
            // ignored
        }
        Log.w(TAG, "exiting");
    }

    public synchronized void setListener(MessageSenderListener listener) {
        mListener = listener;
    }

    public Uri getUri() {
        return mUri;
    }

    public abstract interface MessageSenderListener {
        /** Called if an exception get thrown. */
        public void onError(MessageSender s, Throwable e);

        /** Called if the message has been accepted by the server. */
        public void onMessageSent(MessageSender s, final String msgId);

        /** Called if the message was refused by the server. */
        public void onMessageError(MessageSender s, final int reason);
    }
}

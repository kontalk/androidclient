package org.kontalk.service;

import java.util.List;
import java.util.Map;

import org.kontalk.client.MessageSender;
import org.kontalk.client.StatusResponse;
import org.kontalk.provider.MyMessages.Messages;

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
    public void response(RequestJob job, List<StatusResponse> res) {
        MessageSender job2 = (MessageSender) job;
        if (res != null && res.size() > 0) {
            Uri uri = job2.getMessageUri();
            StatusResponse st = res.get(0);

            // message accepted!
            if (st.code == StatusResponse.STATUS_SUCCESS) {
                Map<String, Object> extra = st.extra;
                if (extra != null) {
                    String msgId = (String) extra.get("i");
                    if (!TextUtils.isEmpty(msgId)) {
                        ContentValues values = new ContentValues(1);
                        values.put(Messages.MESSAGE_ID, msgId);
                        values.put(Messages.STATUS, Messages.STATUS_SENT);
                        int n = mContentResolver.update(uri, values, null, null);
                        Log.i(TAG, "message sent and updated (" + n + ")");
                    }
                }
            }

            // message refused!
            else {
                ContentValues values = new ContentValues(1);
                values.put(Messages.STATUS, Messages.STATUS_NOTACCEPTED);
                mContentResolver.update(uri, values, null, null);
                Log.w(TAG, "message not accepted by server and updated (" + st.code + ")");
            }
        }
        else {
            // empty response!? :O
            error(job, new IllegalArgumentException("empty response"));
        }
    }

    @Override
    public boolean error(RequestJob job, Throwable e) {
        MessageSender job2 = (MessageSender) job;
        Uri uri = job2.getMessageUri();
        ContentValues values = new ContentValues(1);
        values.put(Messages.STATUS, Messages.STATUS_ERROR);
        mContentResolver.update(uri, values, null, null);
        Log.e(TAG, "error sending message", e);
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

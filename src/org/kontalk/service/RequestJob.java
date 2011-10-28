package org.kontalk.service;

import java.io.IOException;

import org.kontalk.client.RequestClient;

import android.content.Context;

import com.google.protobuf.MessageLite;


public abstract class RequestJob {

    protected RequestListener mListener;
    protected boolean mCancel;

    public void setListener(RequestListener listener) {
        mListener = listener;
    }

    public RequestListener getListener() {
        return mListener;
    }

    /** Implement this to do the actual task the child should execute. */
    public abstract MessageLite call(RequestClient client,
            RequestListener listener, Context context) throws IOException;

    /**
     * Sets the cancel flag.
     * The {@link RequestWorker} will see this flag and abort executing the
     * request if still possible.
     */
    public void cancel() {
        mCancel = true;
    }

    public boolean isCanceled() {
        return mCancel;
    }
}

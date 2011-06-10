package org.nuntius.service;

import java.io.IOException;
import java.util.List;

import org.nuntius.authenticator.Authenticator;
import org.nuntius.client.*;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class RequestWorker extends Thread {
    private static final String TAG = RequestWorker.class.getSimpleName();
    private static final int MSG_REQUEST_JOB = 1;

    private static final long DEFAULT_RETRY_DELAY = 5000;

    private Handler mHandler;
    private final Context mContext;
    private final EndpointServer mServer;
    private String mAuthToken;

    private RequestClient mClient;
    private ResponseListener mListener;

    public RequestWorker(Context context, EndpointServer server) {
        mContext = context;
        mServer = server;
    }

    public void setResponseListener(ResponseListener listener) {
        this.mListener = listener;
    }

    public void run() {
        Looper.prepare();
        mAuthToken = Authenticator.getDefaultAccountToken(mContext);
        Log.i(TAG, "using token: " + mAuthToken);

        mClient = new RequestClient(mServer, mAuthToken);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_REQUEST_JOB) {
                    RequestJob job = (RequestJob) msg.obj;
                    Log.w("RequestWorker", job.toString());

                    // try to use the custom listener
                    ResponseListener listener = job.getListener();
                    if (listener == null)
                        listener = mListener;

                    List<StatusResponse> list;
                    try {
                        list = mClient.request(job.getCommand(), job.getParams(), job.getContent());

                        if (listener != null)
                            listener.response(job, list);
                    } catch (IOException e) {
                        boolean requeue = true;
                        Log.e("RequestWorker", "request error", e);
                        if (listener != null)
                            requeue = listener.error(job, e);

                        if (requeue) {
                            Log.i(TAG, "requeuing job " + job);
                            push(job, DEFAULT_RETRY_DELAY);
                        }
                    }
                }

                else
                    super.handleMessage(msg);
            };
        };

        Looper.loop();
    }

    public void push(RequestJob job) {
        push(job, 0);
    }

    public void push(RequestJob job, long delay) {
        // max wait time 10 seconds
        int retries = 20;

        while(!isAlive() || retries <= 0) {
            try {
                // 500ms should do the job...
                Thread.sleep(500);
                Thread.yield();
                retries--;
            } catch (InterruptedException e) {
                // interrupted - do not send message
                return;
            }
        }

        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(MSG_REQUEST_JOB, job),
                delay);
    }

    /**
     * Shuts down this request worker gracefully.
     */
    public void shutdown() {
        Log.w(getClass().getSimpleName(), "shutting down");
        if (mClient != null)
            mClient.abort();
        if (mHandler != null)
            mHandler.getLooper().quit();

        Log.w(getClass().getSimpleName(), "exiting");
        mClient = null;
        mHandler = null;
    }
}

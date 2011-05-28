package org.nuntius.service;

import java.io.IOException;
import java.util.List;

import org.nuntius.client.*;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

public class RequestWorker extends Thread {

    private static final int MSG_REQUEST_JOB = 1;

    private final Context mContext;
    private Handler mHandler;
    private final EndpointServer mServer;
    private String mAuthToken;

    private RequestClient mClient;
    private ResponseListener mListener;

    public RequestWorker(Context context, EndpointServer server) {
        this.mContext = context;
        this.mServer = server;
    }

    public void setAuthToken(String token) {
        this.mAuthToken = token;
    }

    public void setResponseListener(ResponseListener listener) {
        this.mListener = listener;
    }

    public void run() {
        Looper.prepare();
        mClient = new RequestClient(mServer, mAuthToken);

        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_REQUEST_JOB) {
                    RequestJob job = (RequestJob) msg.obj;
                    Log.w("RequestWorker", job.toString());

                    List<StatusResponse> list;
                    try {
                        list = mClient.request(job.getCommand(), job.getParams(), job.getContent());

                        if (mListener != null)
                            mListener.response(job, list);
                    } catch (IOException e) {
                        Log.e("RequestWorker", "request error", e);
                    }
                }

                else
                    super.handleMessage(msg);
            };
        };

        Looper.loop();
    }

    public void push(RequestJob job) {
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
        mHandler.sendMessage(mHandler.obtainMessage(MSG_REQUEST_JOB, job));
    }

    /**
     * Shuts down this request worker gracefully.
     */
    public void shutdown() {
        Log.w(getClass().getSimpleName(), "shutting down");
        mClient.abort();
        mHandler.getLooper().quit();

        Log.w(getClass().getSimpleName(), "exiting");
        mClient = null;
    }
}

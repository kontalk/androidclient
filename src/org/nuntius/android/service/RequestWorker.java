package org.nuntius.android.service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.nuntius.android.client.*;

import android.content.Context;
import android.util.Log;

public class RequestWorker extends Thread {

    private final Context mContext;
    private final EndpointServer mServer;
    private String mAuthToken;
    private boolean mRunning;

    private RequestClient mClient;
    private ResponseListener mListener;
    private Queue<RequestJob> mQueue;

    public RequestWorker(Context context, EndpointServer server) {
        this.mContext = context;
        this.mServer = server;
        this.mQueue = new ConcurrentLinkedQueue<RequestJob>();
    }

    public void setAuthToken(String token) {
        this.mAuthToken = token;
    }

    public void setResponseListener(ResponseListener listener) {
        this.mListener = listener;
    }

    public void run() {
        mRunning = true;
        mClient = new RequestClient(mServer, mAuthToken);

        while(mRunning) {
            try {
                RequestJob job;
                while ((job = mQueue.poll()) != null) {
                    // TODO process job
                    Log.w(getClass().getSimpleName(), job.toString());
                }

                synchronized (this) {
                    wait();
                }
            } catch (Exception e) {
                Log.e(getClass().getSimpleName(), "request error", e);
            }
        }
    }

    public synchronized void push(RequestJob job) {
        synchronized (this) {
            mQueue.add(job);
            notifyAll();
        }
    }

    /**
     * Shuts down this request worker gracefully.
     */
    public synchronized void shutdown() {
        mRunning = false;
        try {
            interrupt();
            join();
        }
        catch (InterruptedException e) {
            // ignored
        }
    }
}

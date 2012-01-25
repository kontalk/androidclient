/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service;

import java.io.InterruptedIOException;
import java.util.List;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.PollingClient;
import org.kontalk.message.AbstractMessage;

import android.accounts.Account;
import android.content.Context;
import android.os.Process;
import android.util.Log;


/**
 * The polling thread.
 * @author Daniele Ricci
 */
public class PollingThread extends Thread {
    private final static String TAG = PollingThread.class.getSimpleName();

    private final static int MAX_IDLES = 3;

    private final Context mContext;
    private final EndpointServer mServer;
    private String mAuthToken;

    private PollingClient mClient;
    private MessageListener mListener;

    private boolean mIdle;
    private boolean mInterrupted;
    private String mPushRegistrationId;

    public PollingThread(Context context, EndpointServer server) {
        super(PollingThread.class.getSimpleName());
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        mServer = server;
        mContext = context;
    }

    public void setMessageListener(MessageListener listener) {
        this.mListener = listener;
    }

    public String getPushRegistrationId() {
        return mPushRegistrationId;
    }

    public void setPushRegistrationId(String regId) {
        mPushRegistrationId = regId;
    }

    @Override
    public void interrupt() {
        super.interrupt();
        mInterrupted = true;
    }

    @Override
    public boolean isInterrupted() {
        return mInterrupted;
    }

    public boolean isIdle() {
        return mIdle;
    }

    @Override
    public void run() {
        mAuthToken = Authenticator.getDefaultAccountToken(mContext);
        if (mAuthToken == null) {
            Log.w(TAG, "invalid token - exiting");
            return;
        }

        // exposing sensitive data - Log.d(TAG, "using token: " + mAuthToken);

        Account acc = Authenticator.getDefaultAccount(mContext);
        // exposing sensitive data - Log.d(TAG, "using account name " + acc.name + " as my number");
        Log.d(TAG, "using server " + mServer.toString());
        mClient = new PollingClient(mContext, mServer, mAuthToken, acc.name);

        int numIdle = 0;
        try {
            while(!mInterrupted) {
                try {
                    List<AbstractMessage<?>> list = null;
                    try {
                        list = mClient.poll();
                    }
                    catch (InterruptedIOException interrupted) {
                        // interrupted
                    }

                    if (mInterrupted) {
                        Log.d(TAG, "shutdown request");
                        break;
                    }

                    if (list != null) {
                        Log.i(TAG, list.toString());

                        if (mListener != null)
                            mListener.incoming(list);
                    }

                    // success - wait just 1s
                    if (!mInterrupted) {
                        if (list == null || list.size() == 0) {
                            numIdle++;
                            if (mIdle) {
                                Log.v(TAG, "idle shutdown request");
                                return;
                            }

                            if (numIdle >= MAX_IDLES && mPushRegistrationId != null) {
                                // push notifications enabled - we can stop our parent :)
                                Log.d(TAG, "shutting down message center due to inactivity");
                                MessageCenterService.idleMessageCenter(mContext);
                            }
                        }

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {}
                    }
                }
                catch (Exception e) {
                    Log.e(TAG, "polling error", e);
                    if (!mInterrupted) {
                        // error - wait longer - 5s
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e1) {}
                    }
                }
            }
        }

        finally {
            mInterrupted = true;
        }
    }

    /**
     * Shuts down this polling thread gracefully.
     */
    public synchronized void shutdown() {
        Log.d(TAG, "shutting down");
        interrupt();

        if (mClient != null)
            mClient.abort();
        // do not join - just discard the thread

        Log.d(TAG, "exiting");
        mClient = null;
    }

    /** Schedules polling thread exit as soon as possible. */
    public void idle() {
        mIdle = true;
    }
}

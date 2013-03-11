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

import java.io.IOException;
import java.util.concurrent.Future;

import android.content.Context;
import android.util.Log;


/**
 * Extend this class to implement custom jobs to be carried out by the
 * {@link RequestWorker}.
 * @author Daniele Ricci
 */
public abstract class RequestJob {

    protected RequestListener mListener;
    protected boolean mCancel;
    /** Used only if job is asynchronous. */
    protected Future<?> mFuture;
    protected boolean mDone;
    /** Execution attempt counter. */
    protected int mCount;

    public void setListener(RequestListener listener) {
        mListener = listener;
    }

    public RequestListener getListener() {
        return mListener;
    }

    public Future<?> getFuture() {
        return mFuture;
    }

    /**
     * Keeps a reference to the future executing this job.
     * Only for asynchronous jobs.
     */
    public void setFuture(Future<?> future) {
        mFuture = future;
    }

    /**
     * Implement this to do the actual task the child should execute.
     * @return the transaction id (if any, can be null)
     */
    public abstract String execute(ClientThread client, RequestListener listener, Context context) throws IOException;

    /**
     * Sets the cancel flag.
     * The {@link RequestWorker} will see this flag and abort executing the
     * request if still possible.
     */
    public void cancel() {
        Log.v("RequestJob", "canceling job " + toString());
        mCancel = true;
    }

    public boolean isCanceled(Context context) {
        return mCancel;
    }

    /** Returns true if this job needs to be executed in a separate thread. */
    public boolean isAsync(Context context) {
        return false;
    }

    /** Marks this job as done, resetting the execution counter. */
    public void done() {
        mDone = true;
        mCount = 0;
    }

    public boolean isDone() {
        return mDone;
    }

    public void begin() {
        mCount++;
    }

    public int getStartCount() {
        return mCount;
    }

}

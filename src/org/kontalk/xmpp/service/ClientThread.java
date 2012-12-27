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

package org.kontalk.xmpp.service;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.XMPPException;
import org.kontalk.xmpp.authenticator.Authenticator;
import org.kontalk.xmpp.client.ClientHTTPConnection;
import org.kontalk.xmpp.client.ClientListener;
import org.kontalk.xmpp.client.EndpointServer;
import org.kontalk.xmpp.client.KontalkConnection;

import android.content.Context;
import android.os.Process;
import android.util.Log;


/**
 * Kontalk client thread.
 * @author Daniele Ricci
 */
public class ClientThread extends Thread {
    private static final String TAG = ClientThread.class.getSimpleName();

    /** Max connection retry count if idle. */
    private static final int MAX_IDLE_BACKOFF = 10;

    private final Context mContext;
    private EndpointServer mServer;
    private boolean mServerDirty;
    private String mAuthToken;

    private volatile boolean mInterrupted;

    /** Connection retry count for exponential backoff. */
    private int mRetryCount;

    /** Connection is re-created on demand if necessary. */
    private Connection mClient;

    /** Client listener. */
    private ClientListener mListener;

    /** HTTP connection to server. */
    protected ClientHTTPConnection mHttpConn;

    /** Parent thread to be notified. */
    private final ParentThread mParent;

    /**
     * The pack lock. This is used to block receiving packs to allow pack
     * senders to setup their own transaction listeners.
     */
    private final Object mPackLock = new Object();

    public ClientThread(Context context, ParentThread parent, EndpointServer server) {
        super(ClientThread.class.getSimpleName());
        mContext = context;
        mServer = server;
        mParent = parent;
    }

    public void setClientListener(ClientListener listener) {
        mListener = listener;
    }

    @Override
    public void run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            mAuthToken = Authenticator.getDefaultAccountToken(mContext);
            if (mAuthToken == null) {
                Log.w(TAG, "invalid token - exiting");
                return;
            }

            while (!mInterrupted) {
                Log.d(TAG, "using server " + mServer.toString());
                try {
                    if (mClient == null || mServerDirty)
                        mClient = new KontalkConnection(mServer);

                    if (mListener != null)
                        mListener.created(this);

                    // connect
                    mClient.connect();

                    if (mListener != null)
                        mListener.connected(this);

                    // login
                    mClient.login("dummy", mAuthToken);

                    if (mListener != null)
                        mListener.authenticated(this);

                    // this should be the right moment
                    mRetryCount = 0;

                    // all done!
                    break;
                }

                catch (XMPPException ie) {
                    // uncontrolled interrupt - handle errors
                    if (!mInterrupted) {
                        Log.e(TAG, "connection error", ie);
                        // max reconnections - idle message center
                        if (mRetryCount >= MAX_IDLE_BACKOFF) {
                            Log.d(TAG, "maximum number of reconnections - idling message center");
                            MessageCenterServiceLegacy.idleMessageCenter(mContext);
                        }

                        mRetryCount++;

                        // notify parent we are respawning
                        mParent.childRespawning(0);
                    }
                }

                mRetryCount = 0;
            }
        }
        finally {
            // reason not used for now
            mParent.childTerminated(0);
        }
    }

    public Connection getConnection() {
        return mClient;
    }

    public ClientHTTPConnection getHttpConnection() {
        // TODO
        /*if (mHttpConn == null)
            mHttpConn = new ClientHTTPConnection(this, mContext, mServer, mAuthToken);*/
        return mHttpConn;
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

    public boolean isConnected() {
        return (mClient != null && mClient.isConnected());
    }

    /** Shortcut for {@link EndpointServer#getNetwork()}. */
    public String getNetwork() {
        return mServer.getNetwork();
    }

    /** Sets the server the next time we will connect to. */
    public void setServer(EndpointServer server) {
        mServer = server;
        mServerDirty = true;
    }

    /** Shuts down this client thread gracefully. */
    public synchronized void shutdown() {
        interrupt();

        if (mClient != null)
            mClient.disconnect();
        // do not join - just discard the thread
    }

    public Object getPackLock() {
        return mPackLock;
    }

}

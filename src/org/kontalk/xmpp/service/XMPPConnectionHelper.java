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
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPException;
import org.kontalk.xmpp.authenticator.Authenticator;
import org.kontalk.xmpp.client.ClientHTTPConnection;
import org.kontalk.xmpp.client.EndpointServer;
import org.kontalk.xmpp.client.KontalkConnection;

import android.content.Context;
import android.util.Log;


/**
 * XMPP connection helper.
 * @author Daniele Ricci
 */
public class XMPPConnectionHelper {
    private static final String TAG = XMPPConnectionHelper.class.getSimpleName();

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
    protected Connection mConn;

    /** Client listener. */
    private ConnectionHelperListener mListener;

    /** HTTP connection to server. */
    protected ClientHTTPConnection mHttpConn;

    /** Limited connection flag. */
    protected boolean mLimited;

    /** Retry enabled flag. */
    protected boolean mRetryEnabled = true;

    /** Connecting flag. */
    protected volatile boolean mConnecting;

    /** Running flag (that is, inside connect() method). */
    protected volatile boolean mRunning;

    /**
     * Creates a new instance.
     * @param context
     * @param server server to connect to.
     * @param limited if true connection will be carried out even when there is
     * no token; connection will be available for unauthenticated operations
     * only (e.g. registration).
     */
    public XMPPConnectionHelper(Context context, EndpointServer server, boolean limited) {
        mContext = context;
        mServer = server;
        mLimited = limited;
    }

    public void setListener(ConnectionHelperListener listener) {
        mListener = listener;
    }

    public void setRetryEnabled(boolean enabled) {
        mRetryEnabled = enabled;
    }

    public void connectOnce() throws XMPPException {
        Log.d(TAG, "using server " + mServer.toString());

        if (mServerDirty) {
            if (mConn != null) {
                mConn.disconnect();
                mConn = null;
            }
        }

        // recreate connection if closed
        if (mConn == null || !mConn.isConnected()) {
            mConn = new KontalkConnection(mServer);
            if (mListener != null)
                mListener.created();
        }

        // connect
        mConnecting = true;
        mConn.connect();
        mConnecting = false;

        if (mListener != null) {
            mConn.addConnectionListener(mListener);
            mListener.connected();
        }

        // login
        if (mAuthToken != null)
            mConn.login("dummy", mAuthToken);

        if (mListener != null)
            mListener.authenticated();
    }

    public void reconnect() {
        mInterrupted = false;
        mRetryCount = 0;
        connect();
    }

    public void connect() {
        mAuthToken = Authenticator.getDefaultAccountToken(mContext);
        if (mAuthToken == null && !mLimited) {
            Log.w(TAG, "invalid token - exiting");
            MessageCenterService.stop(mContext);
            return;
        }

        mRunning = true;
        while (!mInterrupted) {
            try {
                connectOnce();

                // this should be the right moment
                mRetryCount = 0;

                // all done!
                break;
            }

            catch (Exception ie) {
                // uncontrolled interrupt - handle errors
                if (!mInterrupted) {
                    Log.e(TAG, "connection error", ie);
                    if (mRetryEnabled) {
                        try {
                            // max reconnections - idle message center
                            if (mRetryCount >= MAX_IDLE_BACKOFF) {
                                Log.d(TAG, "maximum number of reconnections - stopping message center");
                                MessageCenterService.stop(mContext);
                                // no need to continue
                                break;
                            }

                            // exponential backoff :)
                            float time = (float) ((Math.pow(2, ++mRetryCount)) - 1) / 2;
                            Log.d(TAG, "retrying in " + time + " seconds (retry="+mRetryCount+")");
                            // notify listener we are reconnecting
                            if (mListener != null)
                                mListener.reconnectingIn((int) time);

                            Thread.sleep((long) (time * 1000));
                            // this is to avoid the exponential backoff counter to be reset
                            continue;
                        }
                        catch (InterruptedException intexc) {
                            // interrupted - exit
                            break;
                        }
                    }
                    else {
                        // retry disabled - notify and exit
                        if (mListener != null)
                            mListener.connectionClosedOnError(ie);
                        break;
                    }
                }
            }

            mRetryCount = 0;
        }
        mRunning = false;
    }

    public Connection getConnection() {
        return mConn;
    }

    public ClientHTTPConnection getHttpConnection() {
        // TODO
        /*if (mHttpConn == null)
            mHttpConn = new ClientHTTPConnection(this, mContext, mServer, mAuthToken);*/
        return mHttpConn;
    }

    public void interrupt() {
        mInterrupted = true;
    }

    public boolean isInterrupted() {
        return mInterrupted;
    }

    public boolean isConnected() {
        return (mConn != null && mConn.isAuthenticated());
    }

    public boolean isConnecting(boolean alsoRunning) {
        return mConnecting && (alsoRunning ? mRunning : true);
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
    public void shutdown() {
        interrupt();

        if (mConn != null)
            mConn.disconnect();
    }


    public interface ConnectionHelperListener extends ConnectionListener {
        public void created();
        public void connected();
        public void authenticated();
    }
}

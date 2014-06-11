/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.XMPPException;
import org.kontalk.Kontalk;
import org.kontalk.authenticator.LegacyAuthentication;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.util.InternalTrustStore;
import org.kontalk.util.Preferences;
import org.spongycastle.openpgp.PGPException;

import android.content.Context;
import android.util.Log;


/**
 * XMPP connection helper.
 * @author Daniele Ricci
 */
public class XMPPConnectionHelper extends Thread {
    private static final String TAG = XMPPConnectionHelper.class.getSimpleName();

    /** Max connection retry count if idle. */
    private static final int MAX_IDLE_BACKOFF = 10;

    private final Context mContext;
    private EndpointServer mServer;
    private boolean mServerDirty;

    /** Connection retry count for exponential backoff. */
    private int mRetryCount;

    /** Connection is re-created on demand if necessary. */
    protected Connection mConn;

    /** Client listener. */
    private ConnectionHelperListener mListener;

    /** Limited connection flag. */
    protected boolean mLimited;

    /** Retry enabled flag. */
    protected boolean mRetryEnabled = true;

    /** Connecting flag. */
    protected volatile boolean mConnecting;

    /**
     * Creates a new instance.
     * @param context
     * @param server server to connect to.
     * @param limited if true connection will be carried out even when there is
     * no personal key; connection will be available for unauthenticated
     * operations only (e.g. registration).
     */
    public XMPPConnectionHelper(Context context, EndpointServer server, boolean limited) {
        super("XMPPConnector");
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

    @Override
    public synchronized void start() {
        mConnecting = true;
        super.start();
    }

    public void run() {
        connect();
    }

    public void connectOnce(PersonalKey key) throws XMPPException,
    		PGPException, KeyStoreException, NoSuchProviderException,
    		NoSuchAlgorithmException, CertificateException, IOException {

        connectOnce(key, null);
    }

    private void connectOnce(PersonalKey key, String token) throws XMPPException,
    		PGPException, IOException, KeyStoreException,
    		NoSuchProviderException, NoSuchAlgorithmException, CertificateException {

        Log.d(TAG, "using server " + mServer.toString());

        if (mServerDirty) {
            // reset dirty server status
            mServerDirty = false;

            // destroy connection
            if (mConn != null) {
                mConn.disconnect();
                mConn = null;
            }
        }

        // recreate connection if closed
        if (mConn == null || !mConn.isConnected()) {
            if (key == null) {
                mConn = new KontalkConnection(mServer);
            }

            else {
            	KeyStore trustStore = null;
            	boolean acceptAnyCertificate = Preferences.getAcceptAnyCertificate(mContext);
            	if (!acceptAnyCertificate)
            		trustStore = InternalTrustStore.getTrustStore(mContext);

                mConn = new KontalkConnection(mServer,
                    key.getBridgePrivateKey(),
                    key.getBridgeCertificate(),
                    acceptAnyCertificate,
                    trustStore);
            }

            if (mListener != null)
                mListener.created();
        }

        // connect
        mConn.connect();

        if (mListener != null) {
            mConn.addConnectionListener(mListener);
            mListener.connected();
        }

        // login
        if (key != null || token != null)
            // the dummy values are not actually used
            mConn.login("dummy", token != null ? token : "dummy");

        if (mListener != null)
            mListener.authenticated();
    }

    public void connect() {
        PersonalKey key = null;
        try {
            key = ((Kontalk)mContext.getApplicationContext()).getPersonalKey();
        }
        catch (Exception e) {
            Log.e(Kontalk.TAG, "unable to retrieve personal key - not using SSL", e);
        }

        String token = LegacyAuthentication.getAuthToken(mContext);

        if (key == null && token == null && !mLimited) {
            Log.w(TAG, "no personal key found - exiting");
            // unrecoverable error
            if (mListener != null)
                mListener.aborted(null);
            return;
        }

        while (mConnecting) {
            try {
                connectOnce(key, token);

                // this should be the right moment
                mRetryCount = 0;

                // all done!
                break;
            }

            catch (Exception ie) {
                // uncontrolled interrupt - handle errors
                if (mConnecting) {
                    Log.e(TAG, "connection error", ie);
                    // forcibly close connection, no matter what
                    try {
                        mConn.disconnect();
                    }
                    catch (Exception e) {
                        // ignored
                    }
                    // EXTERMINATE!!
                    mConn = null;

                    if (mRetryEnabled) {
                        try {
                            // max reconnections - idle message center
                            if (mRetryCount >= MAX_IDLE_BACKOFF) {
                                Log.d(TAG, "maximum number of reconnections - stopping message center");
                                if (mListener != null)
                                    mListener.aborted(ie);
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
                            Log.e(TAG, "- interrupted.");
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
        mConnecting = false;
    }

    public Connection getConnection() {
        return mConn;
    }

    public boolean isConnected() {
        return (mConn != null && mConn.isAuthenticated());
    }

    public boolean isConnecting() {
        return mConnecting;
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
        mConnecting = false;
        interrupt();

        if (mConn != null)
            mConn.disconnect();
    }


    public interface ConnectionHelperListener extends ConnectionListener {
        public void created();
        public void connected();
        public void authenticated();

        public void aborted(Exception e);
    }
}

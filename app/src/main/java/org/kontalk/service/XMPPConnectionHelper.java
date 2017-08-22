/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import com.segment.backo.Backo;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.sasl.SASLError;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.spongycastle.openpgp.PGPException;

import android.content.Context;
import android.provider.Settings;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.authenticator.LegacyAuthentication;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.PGPKeyPairRingProvider;
import org.kontalk.util.InternalTrustStore;
import org.kontalk.util.Preferences;


/**
 * XMPP connection helper.
 * @author Daniele Ricci
 */
public class XMPPConnectionHelper extends Thread {
    private static final String TAG = MessageCenterService.TAG;

    /** Whether to use STARTTLS or direct SSL connection. */
    private static final boolean USE_STARTTLS = true;

    /** Max connection retry count if idle. */
    private static final int MAX_IDLE_BACKOFF = 10;

    /** Max retries after for authentication error. */
    private static final int MAX_AUTH_ERRORS = 3;

    private final Context mContext;
    private EndpointServer mServer;
    private boolean mServerDirty;

    /** Connection retry count for exponential backoff. */
    private int mRetryCount;
    /** Exponential backoff calculator. */
    private final Backo mRetryBackoff;

    /** Connection is re-created on demand if necessary. */
    protected KontalkConnection mConn;

    /** Client listener. */
    private ConnectionHelperListener mListener;

    /** Limited connection flag. */
    protected boolean mLimited;

    /** Retry enabled flag. */
    protected boolean mRetryEnabled = true;

    /** Waiting for exponential backoff. */
    protected boolean mBackoff;

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
        mRetryBackoff = Backo.builder()
            .base(TimeUnit.MILLISECONDS, 1500)
            .cap(TimeUnit.SECONDS, 300)
            .factor(2)
            .jitter(1)
            .build();
    }

    public XMPPConnectionHelper(Context context, EndpointServer server, boolean limited, KontalkConnection reuseConnection) {
        this(context, server, limited);
        mConn = reuseConnection;
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

    public void connectOnce(PersonalKey key, boolean forceLogin) throws XMPPException, SmackException,
            PGPException, KeyStoreException, NoSuchProviderException,
            NoSuchAlgorithmException, CertificateException, IOException, InterruptedException {

        connectOnce(key, null, forceLogin);
    }

    private void connectOnce(PersonalKey key, String token, boolean forceLogin) throws XMPPException,
            SmackException, PGPException, IOException, KeyStoreException,
            NoSuchProviderException, NoSuchAlgorithmException, CertificateException, InterruptedException {

        Log.d(TAG, "using server " + mServer.toString());

        if (mServerDirty) {
            // reset dirty server status
            mServerDirty = false;

            // destroy connection
            if (mConn != null) {
                mConn.instantShutdown();
                mConn = null;
            }
        }

        // recreate connection if closed
        if (mConn == null) {

            KeyStore trustStore = null;
            boolean acceptAnyCertificate = Preferences.getAcceptAnyCertificate(mContext);
            if (!acceptAnyCertificate)
                trustStore = InternalTrustStore.getTrustStore(mContext);

            String resource = getResource(mContext);

            if (key == null) {
                mConn = new KontalkConnection(resource, mServer, !USE_STARTTLS,
                    acceptAnyCertificate, trustStore, token);
            }

            else {
                mConn = new KontalkConnection(resource, mServer, !USE_STARTTLS,
                    key.getBridgePrivateKey(),
                    key.getBridgeCertificate(),
                    acceptAnyCertificate,
                    trustStore, token);
            }

            // apply packet timeout based on retry count
            mConn.setPacketReplyTimeout((mRetryCount + 1) * KontalkConnection.DEFAULT_PACKET_TIMEOUT);

            if (mListener != null)
                mListener.created(mConn);
        }

        // connect
        mConn.connect();

        if (mListener != null) {
            mConn.addConnectionListener(mListener);
            mListener.connected(mConn);
        }

        // login
        if ((!mLimited || forceLogin) && (key != null || token != null))
            mConn.login();

    }

    public void connect() {
        PersonalKey key = null;

        if (LegacyAuthentication.isUpgrading() && mListener != null) {
            PGPKeyPairRingProvider keyProv = mListener.getKeyPairRingProvider();
            if (keyProv != null) {
                PGP.PGPKeyPairRing keyring = keyProv.getKeyPair();
                if (keyring != null) {
                    String passphrase = ((Kontalk) mContext.getApplicationContext()).getCachedPassphrase();

                    try {
                        X509Certificate bridgeCert = X509Bridge.createCertificate(keyring.publicKey,
                            keyring.secretKey.getSecretKey(), passphrase);

                        key = PersonalKey.load(keyring.secretKey, keyring.publicKey,
                            passphrase, bridgeCert);
                    }
                    catch (Exception e) {
                        // this will go crap...
                        Log.e(TAG, "unable to create temporary personal key - not using SSL", e);
                    }
                }
            }
        }

        if (key == null) {
            try {
                key = ((Kontalk) mContext.getApplicationContext()).getPersonalKey();
            }
            catch (Exception e) {
                Log.e(TAG, "unable to retrieve personal key - not using SSL", e);
            }
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
                connectOnce(key, token, false);

                // this should be the right moment
                mRetryCount = 0;

                // all done!
                break;
            }

            catch (Exception ie) {
                // uncontrolled interrupt - handle errors
                if (mConnecting) {
                    Log.e(TAG, "connection error", ie);
                    if (mConn != null) {
                        // forcibly close connection, no matter what
                        mConn.instantShutdown();
                    }

                    // SASL: not authorized
                    if (ie instanceof SASLErrorException) {
                        SASLError error = ((SASLErrorException) ie).getSASLFailure().getSASLError();
                        if ((error == SASLError.not_authorized || error == SASLError.invalid_authzid) &&
                            mRetryCount >= MAX_AUTH_ERRORS) {

                            if (mListener != null) {
                                mListener.authenticationFailed();
                                // this ends here.
                                break;
                            }
                        }
                    }

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
                            long time = mRetryBackoff.backoff(++mRetryCount);
                            Log.d(TAG, "retrying in " + (time/1000) + " seconds (retry="+mRetryCount+")");
                            // notify listener we are reconnecting
                            if (mListener != null)
                                mListener.reconnectingIn((int) time);

                            mBackoff = true;
                            Thread.sleep(time);
                            // this is to avoid the exponential backoff counter to be reset
                            continue;
                        }
                        catch (InterruptedException intexc) {
                            // interrupted - exit
                            Log.e(TAG, "- interrupted.");
                            break;
                        }
                        finally {
                            mBackoff = false;
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

    private static String getResource(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    public AbstractXMPPConnection getConnection() {
        return mConn;
    }

    public boolean isConnected() {
        return (mConn != null && mConn.isAuthenticated());
    }

    public boolean isConnecting() {
        return mConnecting;
    }

    public boolean isStruggling() {
        return mConnecting && mRetryCount > 5;
    }

    public boolean isServerDirty() {
        return mServerDirty;
    }

    public boolean isBackingOff() {
        return mBackoff;
    }

    /** Shortcut for {@link EndpointServer#getNetwork()}. */
    public String getNetwork() {
        return mServer.getNetwork();
    }

    public EndpointServer getServer() {
        return mServer;
    }

    /** Sets the server the next time we will connect to. */
    public void setServer(EndpointServer server) {
        mServer = server;
        mServerDirty = true;
    }

    /** Shuts down this client thread gracefully. */
    public void shutdown() throws NotConnectedException {
        mConnecting = false;
        interrupt();

        if (mConn != null)
            mConn.instantShutdown();
    }


    public interface ConnectionHelperListener extends ConnectionListener {
        /** Connection has been created. */
        public void created(XMPPConnection connection);

        /** Connection was aborted and will never be tried again. */
        public void aborted(Exception e);

        public void authenticationFailed();

        public PGPKeyPairRingProvider getKeyPairRingProvider();
    }
}

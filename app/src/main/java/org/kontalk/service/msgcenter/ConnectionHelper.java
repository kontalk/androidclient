package org.kontalk.service.msgcenter;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.sasl.SASLError;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.spongycastle.openpgp.PGPException;

import android.content.Context;
import android.util.Log;

import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.util.InternalTrustStore;
import org.kontalk.util.Preferences;


/**
 * Connection helper for the message center.
 * @author Daniele Ricci
 */
public class ConnectionHelper extends Thread implements ConnectionListener {
    private static final String TAG = MessageCenterService.TAG;

    /** Whether to use STARTTLS or direct SSL connection. */
    private static final boolean USE_STARTTLS = true;

    /** Max retries after for authentication error. */
    private static final int MAX_AUTH_ERRORS = 3;

    /** Max connection retry count if idle. */
    private static final int MAX_IDLE_BACKOFF = 10;

    private WeakReference<ConnectionHelperListener> mListener;
    private KontalkConnection mConnection;
    private EndpointServer mServer;
    private boolean mLimited;
    private boolean mDirty = true;

    private int mRetryCount;
    private int mFailedAuthCount;

    public ConnectionHelper(EndpointServer server, boolean limited) {
        mServer = server;
        mLimited = limited;
    }

    public void reconnect() {
        if (mConnection != null) {
            mConnection.instantShutdown();
            run();
        }
    }

    public void disconnect() {
        if (mConnection != null) {
            try {
                // disable automatic reconnection
                ReconnectionManager.getInstanceFor(mConnection)
                    .disableAutomaticReconnection();

                mConnection.disconnect();
            }
            catch (SmackException.NotConnectedException e) {
                // ignored
            }
        }
    }

    public boolean isConnected() {
        return mConnection != null && mConnection.isAuthenticated();
    }

    public Roster getRoster() {
        return mConnection != null ? mConnection.getRoster() : null;
    }

    public void addPacketListener(PacketListener listener, PacketFilter filter) {
        if (mConnection != null)
            mConnection.addPacketListener(listener, filter);
    }

    public void removePacketListener(PacketListener listener) {
        if (mConnection != null)
            mConnection.removePacketListener(listener);
    }

    public void sendIqWithResponseCallback(IQ iq, PacketListener listener) throws SmackException.NotConnectedException {
        if (mConnection != null)
            mConnection.sendIqWithResponseCallback(iq, listener);
    }

    @Override
    public void run() {
        ConnectionHelperListener listener = mListener.get();
        if (listener == null) {
            // service died - abort silently
            return;
        }

        try {
            // create and setup connection
            // this will bring up credentials, keys, and so on
            createConnection();
        }
        catch (IllegalArgumentException e) {
            // personal key not found - abort
            listener.aborted(e);
        }
        catch (Exception e) {
            Log.e(TAG, "error creating connection", e);
            listener.aborted(e);
        }

        try {
            mConnection.connect();
        }
        catch (Exception e) {
            Log.e(TAG, "connection error", e);
            // ReconnectionManager will try to reconnect
        }
    }

    private synchronized void createConnection() throws PGPException,
        NoSuchAlgorithmException, CertificateException, NoSuchProviderException,
        KeyStoreException, IOException {

        ConnectionHelperListener listener = mListener.get();
        if (listener != null) {

            if (mDirty || mConnection == null) {
                PersonalKey key = listener.getPersonalKey();
                if (key == null) {
                    Log.e(TAG, "unable to retrieve personal key - not using SSL");
                }

                String token = listener.getLegacyAuthToken();
                if (key == null && token == null && !mLimited) {
                    Log.w(TAG, "no personal key found - exiting");
                    // unrecoverable error
                    throw new IllegalArgumentException("no personal key found!");
                }

                KeyStore trustStore = null;
                boolean acceptAnyCertificate = Preferences.getAcceptAnyCertificate(listener.getContext());
                if (!acceptAnyCertificate)
                    trustStore = InternalTrustStore.getTrustStore(listener.getContext());

                if (key == null) {
                    mConnection = new KontalkConnection(mServer, !USE_STARTTLS,
                        acceptAnyCertificate, trustStore, token);
                } else {
                    mConnection = new KontalkConnection(mServer, !USE_STARTTLS,
                        key.getBridgePrivateKey(),
                        key.getBridgeCertificate(),
                        acceptAnyCertificate,
                        trustStore, token);
                }

                // we want to be the first connection listener
                mConnection.addConnectionListener(this);

                // enable automatic reconnection
                ReconnectionManager.getInstanceFor(mConnection)
                    .enableAutomaticReconnection();

                listener.created(mConnection);

                mDirty = false;
            }
        }
    }

    @Override
    public void connected(XMPPConnection connection) {
        try {
            ((AbstractXMPPConnection) connection).login();
            mFailedAuthCount = 0;
        }
        catch (XMPPException e) {
            // SASL: not-authorized
            if (e instanceof SASLErrorException && ((SASLErrorException) e)
                .getSASLFailure().getSASLError() == SASLError.not_authorized) {
                mFailedAuthCount++;

                if (mFailedAuthCount >= MAX_AUTH_ERRORS) {
                    // max number of authorization errors reached
                    ConnectionHelperListener listener = mListener.get();
                    if (listener != null) {
                        // disable automatic reconnection
                        ReconnectionManager.getInstanceFor((AbstractXMPPConnection) connection)
                            .disableAutomaticReconnection();

                        // authentication failed
                        listener.authenticationFailed();
                    }
                }
            }
        }
        catch (Exception e) {
            // unrecoverable exception
            ConnectionHelperListener listener = mListener.get();
            if (listener != null) {
                listener.aborted(e);
            }

            // TEST crash!!!
            throw new RuntimeException("LOGIN ERROR", e);
        }
    }

    @Override
    public void authenticated(XMPPConnection connection) {
        ConnectionHelperListener listener = mListener.get();
        if (listener != null) {
            listener.authenticated(connection);
        }
    }

    @Override
    public void connectionClosed() {

    }

    /**
     * This method is called from a Smack thread so we can safely do expensive
     * stuff, e.g. recreating the connection :)
     */
    @Override
    public void connectionClosedOnError(Exception e) {
        if (mDirty) {
            run();
        }
    }

    @Override
    public void reconnectingIn(int seconds) {
        // handled by ReconnectionManager.
    }

    @Override
    public void reconnectionSuccessful() {
        // handled by ReconnectionManager.
    }

    @Override
    public void reconnectionFailed(Exception e) {
        mRetryCount++;

        // max reconnections - idle message center
        if (mRetryCount >= MAX_IDLE_BACKOFF) {
            Log.d(TAG, "maximum number of reconnections - stopping message center");
            ConnectionHelperListener listener = mListener.get();
            if (listener != null) {
                // disable automatic reconnection
                ReconnectionManager.getInstanceFor((AbstractXMPPConnection) mConnection)
                    .disableAutomaticReconnection();

                // authentication failed
                listener.aborted(e);
            }
        }
    }

    public void sendPacket(Packet packet) throws SmackException.NotConnectedException {
        mConnection.sendPacket(packet);
    }

    public void setListener(ConnectionHelperListener listener) {
        mListener = new WeakReference<ConnectionHelperListener>(listener);
    }

    /**
     * Sets the server to connect to the next time.
     * No automatic reconnection is triggered.
     */
    public void setServer(EndpointServer server) {
        mServer = server;
        mDirty = true;
    }

    public interface ConnectionHelperListener {
        public void created(XMPPConnection connection);

        public void aborted(Exception e);

        public void authenticated(XMPPConnection connection);
        public void authenticationFailed();

        public PersonalKey getPersonalKey();
        public String getLegacyAuthToken();

        public Context getContext();
    }

}

package org.kontalk.xmpp.client;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;
import org.kontalk.xmpp.Kontalk;

import android.util.Log;


public class KontalkConnection extends XMPPConnection {

    protected EndpointServer mServer;

    public KontalkConnection(EndpointServer server) throws XMPPException {
        super(new AndroidConnectionConfiguration(server.getHost(), server.getPort()));

        mServer = server;
        // network name
        config.setServiceName(server.getNetwork());
        // disable reconnection
        config.setReconnectionAllowed(false);
        // enable SASL
        config.setSASLAuthenticationEnabled(true);
        // we don't need the roster
        config.setRosterLoadedAtLogin(false);
        // enable compression
        config.setCompressionEnabled(true);
        // enable encryption
        config.setSecurityMode(SecurityMode.enabled);
        // we will send a custom presence
        config.setSendPresence(false);
    }

    public KontalkConnection(EndpointServer server, PrivateKey privateKey, X509Certificate bridgeCert) throws XMPPException {
        this(server);

        setupSSL(privateKey, bridgeCert);
    }

    private void setupSSL(PrivateKey privateKey, X509Certificate bridgeCert) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLSv1");

            // in-memory keystore
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null, null);
            keystore.setKeyEntry("private", privateKey, null, new Certificate[] { bridgeCert });

            // key managers
            KeyManager[] km;
            KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmFactory.init(keystore, null);

            km = kmFactory.getKeyManagers();

            // trust managers
            TrustManager[] tm = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType)
                        throws CertificateException {
                    }
                }
            };
            /*
            TODO builtin keystore
            TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmFactory.init((KeyStore) null);

            tm = tmFactory.getTrustManagers();
            */

            ctx.init(km, tm, null);
            config.setCustomSSLContext(ctx);
            //config.setSocketFactory(SSLSocketFactory.getDefault());

            // enable SASL EXTERNAL
            SASLAuthentication.supportSASLMechanism("EXTERNAL");
        }
        catch (Exception e) {
            Log.w(Kontalk.TAG, "unable to setup SSL connection", e);
        }
    }

    @Override
    public void disconnect() {
        Log.v("KontalkConnection", "disconnecting (no presence)");
        super.disconnect();
    }

    @Override
    public synchronized void disconnect(Presence presence) {
        Log.v("KontalkConnection", "disconnecting ("+presence+")");
        super.disconnect(presence);
    }

}

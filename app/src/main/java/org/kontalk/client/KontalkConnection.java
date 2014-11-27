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

package org.kontalk.client;

import java.io.IOException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;

import android.util.Log;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;


public class KontalkConnection extends XMPPTCPConnection {
    private static final String TAG = Kontalk.TAG;

    protected EndpointServer mServer;

    public KontalkConnection(EndpointServer server, boolean secure,
        boolean acceptAnyCertificate, KeyStore trustStore, String legacyAuthToken)
            throws XMPPException {

        this(server, secure, null, null, acceptAnyCertificate, trustStore, legacyAuthToken);
    }

    public KontalkConnection(EndpointServer server, boolean secure,
            PrivateKey privateKey, X509Certificate bridgeCert,
            boolean acceptAnyCertificate, KeyStore trustStore, String legacyAuthToken) throws XMPPException {

        super(buildConfiguration(server, secure,
            privateKey, bridgeCert, acceptAnyCertificate, trustStore, legacyAuthToken));

        mServer = server;

        // enable SM without resumption
        setUseStreamManagement(true);
        setUseStreamManagementResumption(false);
    }

    @Override
    public void disconnect() throws NotConnectedException {
        Log.v(TAG, "disconnecting (no presence)");
        super.disconnect();
    }

    @Override
    public synchronized void disconnect(Presence presence) throws NotConnectedException {
        Log.v(TAG, "disconnecting ("+presence+")");
        super.disconnect(presence);
    }

    private static XMPPTCPConnectionConfiguration buildConfiguration(EndpointServer server,
        boolean secure, PrivateKey privateKey, X509Certificate bridgeCert,
        boolean acceptAnyCertificate, KeyStore trustStore, String legacyAuthToken) {
        XMPPTCPConnectionConfiguration.XMPPTCPConnectionConfigurationBuilder builder =
            XMPPTCPConnectionConfiguration.builder();

        builder
            // connection parameters
            .setHost(server.getHost())
            .setPort(secure ? server.getSecurePort() : server.getPort())
            .setServiceName(server.getNetwork())
            // the dummy value is not actually used
            .setUsernameAndPassword(null, legacyAuthToken != null ? legacyAuthToken : "dummy")
            .setCallbackHandler(new CallbackHandler() {
                @Override
                public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                    for (Callback cb : callbacks)
                        Log.v(TAG, "callback = " + cb);
                }
            })
            // TODO requesting the roster could be expensive
            .setRosterLoadedAtLogin(true)
            // enable compression
            .setCompressionEnabled(true)
            // enable encryption
            .setSecurityMode(secure ? SecurityMode.disabled : SecurityMode.required)
            // we will send a custom presence
            .setSendPresence(false)
            // disable session initiation
            .setLegacySessionDisabled(true)
            // enable debugging
            .setDebuggerEnabled(BuildConfig.DEBUG);

        // setup SSL
        setupSSL(builder, secure, privateKey, bridgeCert, acceptAnyCertificate, trustStore);

        return builder.build();
    }

    private static void setupSSL(XMPPTCPConnectionConfiguration.XMPPTCPConnectionConfigurationBuilder builder,
        boolean direct, PrivateKey privateKey, X509Certificate bridgeCert,
        boolean acceptAnyCertificate, KeyStore trustStore) {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");

            KeyManager[] km = null;
            if (privateKey != null && bridgeCert != null) {
                // in-memory keystore
                KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
                keystore.load(null, null);
                keystore.setKeyEntry("private", privateKey, null, new Certificate[] { bridgeCert });

                // key managers
                KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmFactory.init(keystore, null);

                km = kmFactory.getKeyManagers();

                // blacklist PLAIN mechanism
                SASLAuthentication.blacklistSASLMechanism("PLAIN");
            }

            // trust managers
            TrustManager[] tm;

            if (acceptAnyCertificate) {
                tm = new TrustManager[] {
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
            }

            else {
                // builtin keystore
                TrustManagerFactory tmFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmFactory.init(trustStore);

                tm = tmFactory.getTrustManagers();
            }

            ctx.init(km, tm, null);
            builder.setCustomSSLContext(ctx);
            if (direct)
                builder.setSocketFactory(ctx.getSocketFactory());

            // SASL EXTERNAL is already enabled in Smack
        }
        catch (Exception e) {
            Log.w(TAG, "unable to setup SSL connection", e);
        }
    }
}

/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.sm.StreamManagementException;
import org.jivesoftware.smack.sm.predicates.ForMatchingPredicateOrAfterXStanzas;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jxmpp.stringprep.XmppStringprepException;

import android.annotation.SuppressLint;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.client.smack.XMPPTCPConnection;
import org.kontalk.service.msgcenter.SecureConnectionManager;


public class KontalkConnection extends XMPPTCPConnection {
    private static final String TAG = Kontalk.TAG;

    /** Packet reply timeout. */
    public static final int DEFAULT_PACKET_TIMEOUT = 15000;

    protected EndpointServer mServer;

    /** Actually a copy of the same Smack map, but since we need access to the listeners... */
    private final Map<String, AckMultiListener> mStanzaIdAcknowledgedListeners = new ConcurrentHashMap<>();

    public KontalkConnection(String resource, EndpointServer server, boolean secure,
        boolean acceptAnyCertificate, KeyStore trustStore)
        throws XmppStringprepException {

        this(resource, server, secure, null, null, acceptAnyCertificate, trustStore);
    }

    public KontalkConnection(String resource, EndpointServer server, boolean secure,
            PrivateKey privateKey, X509Certificate bridgeCert,
            boolean acceptAnyCertificate, KeyStore trustStore) throws XmppStringprepException {

        super(buildConfiguration(resource, server, secure,
            privateKey, bridgeCert, acceptAnyCertificate, trustStore));

        mServer = server;

        // enable SM without resumption
        setUseStreamManagement(true);
        setUseStreamManagementResumption(false);
        // set custom ack predicate
        addRequestAckPredicate(AckPredicate.INSTANCE);
        // set custom packet reply timeout
        setReplyTimeout(DEFAULT_PACKET_TIMEOUT);
    }

    private static XMPPTCPConnectionConfiguration buildConfiguration(String resource,
        EndpointServer server, boolean secure, PrivateKey privateKey, X509Certificate bridgeCert,
        boolean acceptAnyCertificate, KeyStore trustStore) throws XmppStringprepException {
        XMPPTCPConnectionConfiguration.Builder builder =
            XMPPTCPConnectionConfiguration.builder();

        String host = server.getHost();
        InetAddress inetAddress = null;
        if (host != null) {
            try {
                inetAddress = InetAddress.getByName(host);
            }
            catch (UnknownHostException e) {
                Log.w(TAG, "unable to resolve host " + host + ", will try again during connect", e);
            }
        }

        // connection parameters
        if (inetAddress != null) {
            builder.setHostAddress(inetAddress);
        }
        else if (host != null) {
            // try a last time through Smack
            builder.setHost(host);
        }
        // else: try with XMPP domain

        builder
            .setPort(secure ? server.getSecurePort() : server.getPort())
            .setXmppDomain(server.getNetwork())
            .setResource(resource)
            // for EXTERNAL
            .allowEmptyOrNullUsernames()
            // enable compression
            .setCompressionEnabled(true)
            // enable encryption
            .setSecurityMode(secure ? SecurityMode.disabled : SecurityMode.required)
            // we will send a custom presence
            .setSendPresence(false);

        if (Log.isDebug()) {
            // enable debugging
            builder.enableDefaultDebugger();
        }

        // setup SSL
        setupSSL(builder, secure, privateKey, bridgeCert, acceptAnyCertificate, trustStore);

        return builder.build();
    }

    @SuppressLint("AllowAllHostnameVerifier")
    private static void setupSSL(XMPPTCPConnectionConfiguration.Builder builder,
                                 boolean direct, PrivateKey privateKey, X509Certificate bridgeCert,
                                 boolean acceptAnyCertificate, KeyStore trustStore) {
        try {
            // wait for secure connection stuff
            SecureConnectionManager.waitForInit();

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

                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        }

                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        }
                    }
                };
                builder.setHostnameVerifier(new AllowAllHostnameVerifier());
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

    @Override
    protected void processStanza(Stanza packet) throws InterruptedException {
        boolean isMessage = packet instanceof Message;
        if (isMessage) {
            /*
             * We are receiving a message. Suspend SM ack replies because we
             * want to wait for our message listener to be invoked and have time
             * to store the message to the database.
             */
            suspendSmAck();
        }

        super.processStanza(packet);

        if (isMessage) {
            /* Resume SM ack replies now. */
            try {
                resumeSmAck();
            }
            catch (SmackException ignored) {
            }
        }
    }

    public EndpointServer getServer() {
        return mServer;
    }

    @Override
    public StanzaListener addStanzaIdAcknowledgedListener(String id, StanzaListener listener) throws StreamManagementException.StreamManagementNotEnabledException {
        AckMultiListener multi = mStanzaIdAcknowledgedListeners.get(id);
        StanzaListener old = null;
        if (multi == null) {
            multi = new AckMultiListener();
            mStanzaIdAcknowledgedListeners.put(id, multi);
            old = super.addStanzaIdAcknowledgedListener(id, multi);
        }

        multi.addListener(listener);
        return old;
    }

    @Override
    public StanzaListener removeStanzaIdAcknowledgedListener(String id) {
        mStanzaIdAcknowledgedListeners.remove(id);
        return super.removeStanzaIdAcknowledgedListener(id);
    }

    /** An ack listener for handling multiple listeners for a given stanza ID. */
    static final class AckMultiListener implements StanzaListener {
        private Collection<StanzaListener> mListeners = new ConcurrentLinkedQueue<>();

        @Override
        public void processStanza(Stanza packet) throws SmackException.NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
            for (StanzaListener l : mListeners) {
                l.processStanza(packet);
            }
        }

        void addListener(StanzaListener listener) {
            mListeners.add(listener);
        }

        void removeListener(StanzaListener listener) {
            mListeners.remove(listener);
        }
    }

    /**
     * A custom ack predicate that allows ack after a message with a delivery
     * receipt, a receipt request, an out-of-band extension or a body, or after 5 stanzas.
     */
    private static final class AckPredicate extends ForMatchingPredicateOrAfterXStanzas {

        public static final AckPredicate INSTANCE = new AckPredicate();

        private AckPredicate() {
            super(new StanzaFilter() {
                @Override
                public boolean accept(Stanza packet) {
                    return (packet instanceof Message &&
                        (((Message) packet).getBody() != null ||
                          DeliveryReceipt.from((Message) packet) != null ||
                           DeliveryReceiptRequest.from(packet) != null ||
                            packet.hasExtension(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE)));
                }
            }, 5);
        }
    }
}

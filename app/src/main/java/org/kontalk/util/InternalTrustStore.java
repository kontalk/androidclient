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

package org.kontalk.util;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Enumeration;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.kontalk.R;
import org.kontalk.crypto.PGP;

import android.content.Context;


/** Some trust store utilities. */
public class InternalTrustStore {

    private static KeyStore sTrustStore;

    private static boolean sInitialized;

    /** Sets all {@link HttpsURLConnection}s to use our trust store. */
    public static void initUrlConnections(Context context)
            throws  NoSuchAlgorithmException, CertificateException,
            KeyStoreException, IOException,
            KeyManagementException {
        if (!sInitialized) {
            sInitialized = true;
            TrustManagerFactory tmFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmFactory.init(getTrustStore(context));
            TrustManager[] tm = tmFactory.getTrustManagers();

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tm, null);

            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        }
    }

    /**
     * Returns a trust store merged from the internal keystore and system
     * keystore.
     */
    public static KeyStore getTrustStore(Context context)
            throws KeyStoreException,
            NoSuchAlgorithmException,
            CertificateException,
            IOException {

        if (sTrustStore == null) {
            // load internal truststore from file
            sTrustStore = KeyStore.getInstance("BKS", PGP.PROVIDER);
            InputStream in = context.getResources()
                    .openRawResource(R.raw.truststore);
            sTrustStore.load(in, "changeit".toCharArray());

            // load system trust store
            KeyStore systemStore = loadSystemTrustStore();

            // copy system entries to our trust store
            Enumeration<String> aliases = systemStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate cert = systemStore.getCertificate(alias);

                if (sTrustStore.containsAlias(alias))
                    alias = "system_" + alias;

                sTrustStore.setCertificateEntry(alias, cert);
            }
        }

        return sTrustStore;
    }

    /** Loads the system trust store. */
    private static KeyStore loadSystemTrustStore()
            throws KeyStoreException,
            NoSuchAlgorithmException,
            CertificateException,
            IOException {

        KeyStore ks = KeyStore.getInstance("AndroidCAStore");
        ks.load(null, null);
        return ks;
    }
}

/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;

import info.guardianproject.netcipher.client.TlsOnlySocketFactory;

import org.kontalk.Log;
import org.kontalk.message.CompositeMessage;
import org.kontalk.service.DownloadListener;
import org.kontalk.util.InternalTrustStore;
import org.kontalk.util.Preferences;
import org.kontalk.util.ProgressOutputStreamEntity;


/**
 * FIXME this is actually specific to Kontalk Dropbox server.
 * @author Daniele Ricci
 */
public class ClientHTTPConnection {
    private static final String TAG = ClientHTTPConnection.class.getSimpleName();

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");
    /** Minimum delay for progress notification updates in milliseconds. */
    private static final int PROGRESS_PUBLISH_DELAY = 1000;

    private final Context mContext;

    private final PrivateKey mPrivateKey;
    private final X509Certificate mCertificate;

    private HttpURLConnection currentRequest;
    private final static int CONNECT_TIMEOUT = 15000;
    private final static int READ_TIMEOUT = 40000;

    public ClientHTTPConnection(Context context) {
        this(context, null, null);
    }

    public ClientHTTPConnection(Context context, PrivateKey privateKey, X509Certificate bridgeCert) {
        mContext = context;
        mPrivateKey = privateKey;
        mCertificate = bridgeCert;
    }

    public void abort() {
        close();
    }

    public void close() {
        try {
            currentRequest.disconnect();
        }
        catch (Exception ignored) {
        }
    }

    /**
     * A generic download request.
     * @param url URL to download
     * @return the request object
     */
    private HttpURLConnection prepareURLDownload(String url, boolean acceptAnyCertificate) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        try {
            setupClient(conn, acceptAnyCertificate);
        }
        catch (Exception e) {
            throw innerException("error setting up SSL connection", e);
        }
        return conn;
    }

    private IOException innerException(String detail, Throwable cause) {
        return new IOException(detail, cause);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("AllowAllHostnameVerifier")
    private void setupClient(HttpURLConnection conn, boolean acceptAnyCertificate)
            throws CertificateException, UnrecoverableKeyException,
            NoSuchAlgorithmException, KeyStoreException,
            KeyManagementException, NoSuchProviderException,
            IOException {

        // bug caused by Lighttpd
        //conn.setRequestProperty("Expect", "100-continue");
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setDoInput(true);
        if (conn instanceof HttpsURLConnection) {
            ((HttpsURLConnection) conn).setSSLSocketFactory(setupSSLSocketFactory(mContext,
                mPrivateKey, mCertificate, acceptAnyCertificate));
            if (acceptAnyCertificate)
                ((HttpsURLConnection) conn).setHostnameVerifier(new AllowAllHostnameVerifier());
        }
    }

    public static SSLSocketFactory setupSSLSocketFactory(Context context,
                PrivateKey privateKey, X509Certificate certificate,
                boolean acceptAnyCertificate)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
                IOException, KeyManagementException, UnrecoverableKeyException,
                NoSuchProviderException {

        // in-memory keystore
        KeyManager[] km = null;
        if (privateKey != null && certificate != null) {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null, null);
            keystore.setKeyEntry("private", privateKey, null, new Certificate[]{certificate});

            // key managers
            KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmFactory.init(keystore, null);
            km = kmFactory.getKeyManagers();
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
        }
        else {
            // load merged truststore (system + internal)
            KeyStore trustStore = InternalTrustStore.getTrustStore(context);

            // builtin keystore
            TrustManagerFactory tmFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmFactory.init(trustStore);

            tm = tmFactory.getTrustManagers();
        }

        SSLContext ctx = SSLContext.getInstance("TLSv1");
        ctx.init(km, tm, null);
        return new TlsOnlySocketFactory(ctx.getSocketFactory(), true);
    }

    /**
     * Downloads to a directory represented by a {@link File} object,
     * determining the file name from the Content-Disposition header.
     */
    public void downloadAutofilename(String url, @NonNull File defaultFile, Date timestamp, DownloadListener listener) throws IOException {
        _download(url, defaultFile, timestamp, listener);
    }

    private void _download(String url, @NonNull File defaultFile, Date timestamp, DownloadListener listener) throws IOException {
        boolean acceptAnyCertificate = Preferences.getAcceptAnyCertificate(mContext);
        currentRequest = prepareURLDownload(url, acceptAnyCertificate);

        int code = currentRequest.getResponseCode();
        // HTTP/1.1 200 OK -- other codes should throw Exceptions
        if (code == 200) {
            // use a more suitable filename, taking only the extension
            String contentType = currentRequest.getContentType();
            File destination = null;
            if (contentType != null) {
                destination = CompositeMessage.getIncomingFile(contentType,
                    timestamp != null ? timestamp : new Date());
            }

            // still having problems?
            if (destination == null) {
                String name = null;
                String disp = currentRequest.getHeaderField("Content-Disposition");
                if (disp != null)
                    name = parseContentDisposition(disp);

                if (name != null) {
                    // combine default file directory with server-provided filename
                    destination = new File(defaultFile.getParentFile(), name);
                }
                else {
                    // fallback to default filename
                    destination = defaultFile;
                }
            }

            // we need to wrap the entity to monitor the download progress
            ProgressOutputStreamEntity entity =
                new ProgressOutputStreamEntity(currentRequest, url, destination, listener, PROGRESS_PUBLISH_DELAY);
            FileOutputStream out = new FileOutputStream(destination);
            entity.writeTo(out);
            out.close();
            return;
        }

        Log.d(TAG, "invalid response: " + code);
        listener.error(url, null, new IOException("invalid response: " + code));
    }

    /**
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) {
                return m.group(1);
            }
        }
        catch (IllegalStateException ex) {
            // This function is defined as returning null when it can't parse the header
        }
        return null;
    }

}

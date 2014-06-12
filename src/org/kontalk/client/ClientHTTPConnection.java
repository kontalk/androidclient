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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.kontalk.service.DownloadListener;
import org.kontalk.util.InternalTrustStore;
import org.kontalk.util.Preferences;
import org.kontalk.util.ProgressOutputStreamEntity;

import android.content.Context;
import android.util.Log;


/**
 * FIXME this is actually specific to Kontalk Dropbox server.
 * @author Daniele Ricci
 */
public class ClientHTTPConnection {
    private static final String TAG = ClientHTTPConnection.class.getSimpleName();

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    private final Context mContext;

    private final PrivateKey mPrivateKey;
    private final X509Certificate mCertificate;

    private HttpRequestBase currentRequest;
    private HttpClient mConnection;

    public ClientHTTPConnection(Context context, PrivateKey privateKey, X509Certificate bridgeCert) {
        mContext = context;
        mPrivateKey = privateKey;
        mCertificate = bridgeCert;
    }

    public void abort() {
        if (currentRequest != null)
            currentRequest.abort();
    }

    /**
     * A generic download request.
     * @param url URL to download
     * @return the request object
     * @throws IOException
     */
    private HttpRequestBase prepareURLDownload(String url) throws IOException {
        HttpGet req = new HttpGet(url);
        return req;
    }

    public static SSLSocketFactory setupSSLSocketFactory(Context context,
                PrivateKey privateKey, X509Certificate certificate,
                boolean acceptAnyCertificate)
            throws KeyStoreException, NoSuchAlgorithmException, CertificateException,
                IOException, KeyManagementException, UnrecoverableKeyException,
                NoSuchProviderException {

        // in-memory keystore
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(null, null);
        keystore.setKeyEntry("private", privateKey, null, new Certificate[] { certificate });

        // load merged truststore (system + internal)
        KeyStore truststore = InternalTrustStore.getTrustStore(context);

    	if (acceptAnyCertificate)
    		return new BlackholeSSLSocketFactory(keystore, null, truststore);

    	else
	        return new SSLSocketFactory(keystore, null, truststore);
    }

    /**
     * Executes the given request.
     * @param request the request
     * @return the response
     * @throws IOException
     */
    private HttpResponse execute(HttpRequestBase request) throws IOException {
        // execute!
        try {
            if (mConnection == null) {
                SchemeRegistry registry = new SchemeRegistry();
                try {
                    registry.register(new Scheme("http",  PlainSocketFactory.getSocketFactory(), 80));

                    boolean acceptAnyCertificate = Preferences.getAcceptAnyCertificate(mContext);
                    registry.register(new Scheme("https", setupSSLSocketFactory(mContext,
                    	mPrivateKey, mCertificate, acceptAnyCertificate), 443));
                }
                catch (Exception e) {
                    IOException ie = new IOException("unable to create keystore");
                    ie.initCause(e);
                    throw ie;
                }

                HttpParams params = new BasicHttpParams();
                // handle redirects :)
                params.setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
                // HttpClient bug caused by Lighttpd
                params.setBooleanParameter("http.protocol.expect-continue", false);

                // create connection manager
                ClientConnectionManager connMgr = new SingleClientConnManager(params, registry);

                mConnection = new DefaultHttpClient(connMgr, params);
            }
            return mConnection.execute(request);
        }
        catch (ClientProtocolException e) {
            IOException ie = new IOException("client protocol error");
            ie.initCause(e);
            throw ie;
        }

    }

    /** Downloads to a directory represented by a {@link File} object,
     * determining the file name from the Content-Disposition header. */
    public void downloadAutofilename(String url, File base, DownloadListener listener) throws IOException {
        _download(url, base, listener);
    }

    private void _download(String url, File base, DownloadListener listener) throws IOException {
        currentRequest = prepareURLDownload(url);
        HttpResponse response = execute(currentRequest);

        int code = response.getStatusLine().getStatusCode();
        // HTTP/1.1 200 OK -- other codes should throw Exceptions
        if (code == 200) {
            Header disp = response.getFirstHeader("Content-Disposition");
            if (disp != null) {
                String name = parseContentDisposition(disp.getValue());
                // TODO should check for content-disposition parsing here
                // and choose another filename if necessary

                HttpEntity _entity = response.getEntity();
                if (name != null && _entity != null) {
                    // we need to wrap the entity to monitor the download progress
                    File destination = new File(base, name);
                    ProgressOutputStreamEntity entity = new ProgressOutputStreamEntity(_entity, url, destination, listener);
                    FileOutputStream out = new FileOutputStream(destination);
                    entity.writeTo(out);
                    out.close();
                    return;
                }
            }
        }

        Log.e(TAG, "invalid response: " + code);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            Log.e(TAG, EntityUtils.toString(entity));
            entity.consumeContent();
        }
        listener.error(url, null, new IOException("invalid response: " + code));
    }

    /*
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

    /** A socket factory for accepting any SSL certificate. */
    private static final class BlackholeSSLSocketFactory extends SSLSocketFactory {
        SSLContext sslContext = SSLContext.getInstance("TLS");

        public BlackholeSSLSocketFactory(KeyStore keystore, String keystorePassword, KeyStore truststore)
        		throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
            super(keystore, keystorePassword, truststore);

            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            };

            // key managers
            KeyManager[] km;
            KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmFactory.init(keystore, null);
            km = kmFactory.getKeyManagers();

            sslContext.init(km, new TrustManager[] { tm }, null);
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
            return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
        }

        @Override
        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.upload;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;

import org.apache.http.conn.ssl.AllowAllHostnameVerifier;

import android.content.Context;
import android.net.Uri;

import info.guardianproject.netcipher.NetCipher;

import org.kontalk.Kontalk;
import org.kontalk.client.ClientHTTPConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.ProgressListener;
import org.kontalk.util.Preferences;
import org.kontalk.util.ProgressInputStreamEntity;


/**
 * Upload service implementation for Kontalk Box dropbox service.
 * @author Daniele Ricci
 */
public class KontalkBoxUploadConnection implements UploadConnection {

    /** Message flags header. */
    private static final String HEADER_MESSAGE_FLAGS = "X-Message-Flags";

    protected final Context mContext;

    protected HttpsURLConnection currentRequest;

    private final static int CONNECT_TIMEOUT = 15000;
    private final static int READ_TIMEOUT = 40000;

    private final PrivateKey mPrivateKey;
    private final X509Certificate mCertificate;

    private final String mBaseUrl;

    public KontalkBoxUploadConnection(Context context, String url,
            PrivateKey privateKey, X509Certificate bridgeCert) {
        mContext = context;
        mBaseUrl = url;
        mPrivateKey = privateKey;
        mCertificate = bridgeCert;
    }

    @Override
    public void abort() {
        if (currentRequest != null)
            currentRequest.disconnect();
    }

    @Override
    public String upload(Uri uri, String mime, boolean encrypt, String to, ProgressListener listener)
            throws IOException {

        InputStream inMessage = null;
        try {
            inMessage = mContext.getContentResolver().openInputStream(uri);

            boolean encrypted = false;
            // check if we have to encrypt the message
            if (encrypt) {
                PersonalKey key = Kontalk.get(mContext).getPersonalKey();
                EndpointServer server = Preferences.getEndpointServer(mContext);
                Coder coder = UsersProvider.getEncryptCoder(mContext, server, key, new String[] { to });
                if (coder != null) {
                    // create a temporary file to store encrypted data
                    File temp = File.createTempFile("media", null, mContext.getCacheDir());
                    FileOutputStream out = new FileOutputStream(temp);

                    coder.encryptFile(inMessage, out);
                    // close original file and encrypted file
                    inMessage.close();
                    out.close();

                    // open the encrypted file
                    inMessage = new FileInputStream(temp);
                    encrypted = true;

                    // delete the encrypted file
                    // it will stay until all streams are closed
                    temp.delete();
                }
            }

            // http request!
            boolean acceptAnyCertificate = Preferences.getAcceptAnyCertificate(mContext);
            currentRequest = prepareMessage(mime, encrypted, acceptAnyCertificate);

            // execute!
            ProgressInputStreamEntity entity = new ProgressInputStreamEntity(inMessage, this, listener);
            entity.writeTo(currentRequest.getOutputStream());

            if (currentRequest.getResponseCode() != 200)
                throw new IOException(currentRequest.getResponseCode() + " " + currentRequest.getResponseMessage());

            return responseToString(currentRequest, Charset.defaultCharset());
        }
        catch (Exception e) {
            throw innerException("upload error", e);
        }
        finally {
            currentRequest = null;
            if (inMessage != null) {
                try {
                    inMessage.close();
                }
                catch (Exception e) {
                    // ignore
                }
            }
        }
    }

    public static String responseToString(HttpURLConnection conn, final Charset charset) throws IOException {
        final InputStream instream = conn.getInputStream();
        if (instream == null) {
            return null;
        }
        try {
            int i = conn.getContentLength();
            if (i < 0) {
                i = 4096;
            }
            final Reader reader = new InputStreamReader(instream, charset);
            final StringBuilder buffer = new StringBuilder(i);
            final char[] tmp = new char[1024];
            int l;
            while((l = reader.read(tmp)) != -1) {
                buffer.append(tmp, 0, l);
            }
            return buffer.toString();
        } finally {
            instream.close();
        }
    }

    private IOException innerException(String detail, Throwable cause) {
        IOException ie = new IOException(detail);
        ie.initCause(cause);
        return ie;
    }

    private void setupClient(HttpsURLConnection conn, String mime, boolean encrypted, boolean acceptAnyCertificate)
        throws CertificateException, UnrecoverableKeyException,
        NoSuchAlgorithmException, KeyStoreException,
        KeyManagementException, NoSuchProviderException,
        IOException {

        conn.setSSLSocketFactory(ClientHTTPConnection.setupSSLSocketFactory(mContext,
            mPrivateKey, mCertificate, acceptAnyCertificate));
        if (acceptAnyCertificate)
            conn.setHostnameVerifier(new AllowAllHostnameVerifier());
        conn.setRequestProperty("Content-Type", mime != null ? mime
            : "application/octet-stream");
        if (encrypted)
            conn.setRequestProperty(HEADER_MESSAGE_FLAGS, "encrypted");
        // bug caused by Lighttpd
        conn.setRequestProperty("Expect", "100-continue");

        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
    }

    /** A message posting method. */
    private HttpsURLConnection prepareMessage(String mime, boolean encrypted, boolean acceptAnyCertificate)
            throws IOException {

        // create uri
        HttpsURLConnection conn = NetCipher.getHttpsURLConnection(new URL(mBaseUrl));
        try {
            setupClient(conn, mime, encrypted, acceptAnyCertificate);
        }
        catch (Exception e) {
            throw new IOException("error setting up SSL connection", e);
        }

        return conn;
    }

}

package org.kontalk.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kontalk.client.Protocol.FileUploadResponse;
import org.kontalk.crypto.Coder;
import org.kontalk.service.ClientThread;
import org.kontalk.service.RequestListener;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.ProgressInputStreamEntity;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;


public class ClientHTTPConnection {

    /** The authentication token header. */
    private static final String HEADER_NAME_AUTHORIZATION = "Authorization";
    private static final String HEADER_VALUE_AUTHORIZATION = "KontalkToken auth=";

    /** Message flags header. */
    private static final String HEADER_MESSAGE_FLAGS = "X-Message-Flags";

    protected final Context mContext;
    protected final EndpointServer mServer;
    protected final String mAuthToken;
    protected final ClientThread mClient;

    protected HttpRequestBase currentRequest;
    protected HttpClient mConnection;

    public ClientHTTPConnection(ClientThread client, Context context, EndpointServer server, String token) {
        mContext = context;
        mServer = server;
        mAuthToken = token;
        mClient = client;
    }

    public void abort() {
        if (currentRequest != null)
            currentRequest.abort();
    }

    public FileUploadResponse message(final String[] group, final String mime, final Uri uri,
            final Context context, final MessageSender job, final RequestListener listener)
                throws IOException {

        try {
            AssetFileDescriptor stat = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            long length = stat.getLength();
            InputStream in = context.getContentResolver().openInputStream(uri);

            InputStream toMessage = null;
            long toLength = 0;
            Coder coder = null;
            boolean encrypted = false;
            // check if we have to encrypt the message
            if (job.getEncryptKey() != null) {
                coder = MessagingPreferences.getEncryptCoder(job.getEncryptKey());
                if (coder != null) {
                    toMessage = coder.wrapInputStream(in);
                    toLength = Coder.getEncryptedLength(length);
                    encrypted = true;
                }
            }

            if (coder == null) {
                toMessage = in;
                toLength = length;
            }

            // http request!
            currentRequest = prepareMessage(job, listener, mAuthToken, group, mime, toMessage, toLength, encrypted);
            HttpResponse response = execute(currentRequest);
            return FileUploadResponse.parseFrom(response.getEntity().getContent());
        }
        catch (Exception e) {
            throw innerException("post message error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    private IOException innerException(String detail, Throwable cause) {
        IOException ie = new IOException(detail);
        ie.initCause(cause);
        return ie;
    }

    /**
     * A generic endpoint request method for the messaging server.
     * @param path request path
     * @param params additional GET parameters
     * @param token the autentication token (if needed)
     * @param mime if null will use <code>application/x-google-protobuf</code>
     * @param content the POST body content, if null it will use GET
     * @param forcePost force a POST request even with null content (useful for
     * post-poning entity creation)
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase prepare(String path,
            List<NameValuePair> params, String token,
            String mime, byte[] content, boolean forcePost) throws IOException {

        HttpRequestBase req;

        // compose uri
        StringBuilder uri = new StringBuilder(mServer.getHttpUrl());
        uri.append(path);
        if (params != null)
            uri.append("?").
                append(URLEncodedUtils.format(params, "UTF-8"));

        // request type
        if (content != null || forcePost) {
            req = new HttpPost(uri.toString());
            req.setHeader("Content-Type", mime != null ?
                    mime : "application/x-google-protobuf");
            if (content != null)
                ((HttpPost)req).setEntity(new ByteArrayEntity(content));
        }
        else
            req = new HttpGet(uri.toString());

        // token
        if (token != null)
            req.setHeader(HEADER_NAME_AUTHORIZATION,
                    HEADER_VALUE_AUTHORIZATION + token);

        return req;
    }

    /**
     * A message posting method.
     * @param listener the uploading listener
     * @param token the autentication token
     * @param group the recipients
     * @param mime message mime type
     * @param data data to be sent
     * @param length length of data
     * @return the request object
     * @throws IOException
     */
    private HttpRequestBase prepareMessage(
            MessageSender job, RequestListener listener,
            String token, String[] group, String mime,
            InputStream data, long length, boolean encrypted)
            throws IOException {

        HttpPost req = (HttpPost) prepare("/upload", null, token, mime, null, true);
        req.setEntity(new ProgressInputStreamEntity(data, length, mClient, job, listener));

        if (encrypted)
            req.addHeader(HEADER_MESSAGE_FLAGS, "encrypted");

        return req;
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
                mConnection = new DefaultHttpClient();
                // handle redirects :)
                mConnection.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
                // HttpClient bug caused by Lighttpd
                mConnection.getParams().setBooleanParameter("http.protocol.expect-continue", false);
            }
            return mConnection.execute(request);
        }
        catch (ClientProtocolException e) {
            IOException ie = new IOException("client protocol error");
            ie.initCause(e);
            throw ie;
        }

    }

}

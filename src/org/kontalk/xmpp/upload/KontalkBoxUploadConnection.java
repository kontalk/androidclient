package org.kontalk.xmpp.upload;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
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
import org.apache.http.util.EntityUtils;
import org.kontalk.xmpp.Kontalk;
import org.kontalk.xmpp.crypto.Coder;
import org.kontalk.xmpp.crypto.PersonalKey;
import org.kontalk.xmpp.service.ProgressListener;
import org.kontalk.xmpp.util.ProgressInputStreamEntity;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;


/**
 * Upload service implementation for Kontalk Box dropbox service.
 * @author Daniele Ricci
 */
public class KontalkBoxUploadConnection implements UploadConnection {
    private static final String TAG = KontalkBoxUploadConnection.class.getSimpleName();

    /** The authentication token header. */
    private static final String HEADER_NAME_AUTHORIZATION = "Authorization";
    private static final String HEADER_VALUE_AUTHORIZATION = "KontalkToken auth=";

    /** Message flags header. */
    private static final String HEADER_MESSAGE_FLAGS = "X-Message-Flags";

    protected final Context mContext;

    protected HttpRequestBase currentRequest;
    protected HttpClient mConnection;
    protected final String mAuthToken;

    private final String mBaseUrl;

    public KontalkBoxUploadConnection(Context context, String url, String token) {
        mContext = context;
        mBaseUrl = url;
        mAuthToken = token;
    }

    @Override
    public void abort() {
        if (currentRequest != null)
            currentRequest.abort();
    }

    @Override
    public String upload(Uri uri, String mime, boolean encrypt, ProgressListener listener)
            throws IOException {

        HttpResponse response = null;
        try {
            AssetFileDescriptor stat = mContext.getContentResolver()
                .openAssetFileDescriptor(uri, "r");
            long length = stat.getLength();
            stat.close();

            InputStream in = mContext.getContentResolver().openInputStream(uri);

            InputStream toMessage = null;
            long toLength = 0;
            Coder coder = null;
            boolean encrypted = false;
            // check if we have to encrypt the message
            if (encrypt) {
                PersonalKey key = ((Kontalk)mContext.getApplicationContext()).getPersonalKey();
                // TODO recipients?
                coder = null; // TODO UsersProvider.getEncryptCoder(key, null);
                if (coder != null) {
                    toMessage = coder.wrapInputStream(in);
                    toLength = coder.getEncryptedLength(length);
                    encrypted = true;
                }
            }

            if (coder == null) {
                toMessage = in;
                toLength = length;
            }

            // http request!
            currentRequest = prepareMessage(listener, mAuthToken,
                mime, toMessage, toLength, encrypted);
            response = execute(currentRequest);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
                throw new HttpException(response.getStatusLine().getReasonPhrase());

            return EntityUtils.toString(response.getEntity());
        }
        catch (Exception e) {
            throw innerException("upload error", e);
        }
        finally {
            currentRequest = null;
            try {
                response.getEntity().consumeContent();
            }
            catch (Exception e) {
                // ignore
            }
        }
    }

    private IOException innerException(String detail, Throwable cause) {
        IOException ie = new IOException(detail);
        ie.initCause(cause);
        return ie;
    }

    /**
     * A generic endpoint request method for the messaging server.
     *
     * @param path
     *            request path
     * @param params
     *            additional GET parameters
     * @param token
     *            the autentication token (if needed)
     * @param mime
     *            if null will use <code>application/x-google-protobuf</code>
     * @param content
     *            the POST body content, if null it will use GET
     * @param forcePost
     *            force a POST request even with null content (useful for
     *            post-poning entity creation)
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase prepare(List<NameValuePair> params,
        String token, String mime, byte[] content, boolean forcePost)
        throws IOException {

        HttpRequestBase req;

        // compose uri
        StringBuilder uri = new StringBuilder(mBaseUrl);
        if (params != null)
            uri.append("?").append(URLEncodedUtils.format(params, "UTF-8"));

        // request type
        if (content != null || forcePost) {
            req = new HttpPost(uri.toString());
            req.setHeader("Content-Type", mime != null ? mime
                : "application/octet-stream");
            if (content != null)
                ((HttpPost) req).setEntity(new ByteArrayEntity(content));
        }
        else
            req = new HttpGet(uri.toString());

        // token
        if (token != null)
            req.setHeader(HEADER_NAME_AUTHORIZATION, HEADER_VALUE_AUTHORIZATION
                + token);

        return req;
    }

    /**
     * A message posting method.
     *
     * @param listener
     *            the uploading listener
     * @param token
     *            the autentication token
     * @param group
     *            the recipients
     * @param mime
     *            message mime type
     * @param data
     *            data to be sent
     * @param length
     *            length of data
     * @return the request object
     * @throws IOException
     */
    private HttpRequestBase prepareMessage(ProgressListener listener,
        String token, String mime, InputStream data, long length, boolean encrypted)
            throws IOException {

        HttpPost req = (HttpPost) prepare(null, token, mime, null, true);
        req.setEntity(new ProgressInputStreamEntity(data, length, this, listener));

        if (encrypted)
            req.addHeader(HEADER_MESSAGE_FLAGS, "encrypted");

        return req;
    }

    /**
     * Executes the given request.
     *
     * @param request
     *            the request
     * @return the response
     * @throws IOException
     */
    private HttpResponse execute(HttpRequestBase request) throws IOException {
        // execute!
        try {
            if (mConnection == null) {
                mConnection = new DefaultHttpClient();
                // handle redirects :)
                mConnection.getParams().setBooleanParameter(
                    ClientPNames.HANDLE_REDIRECTS, true);
                // HttpClient bug caused by Lighttpd
                mConnection.getParams().setBooleanParameter(
                    "http.protocol.expect-continue", false);
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

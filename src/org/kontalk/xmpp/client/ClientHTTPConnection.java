package org.kontalk.xmpp.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
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
import org.apache.http.util.EntityUtils;
import org.kontalk.xmpp.crypto.Coder;
import org.kontalk.xmpp.service.ClientThread;
import org.kontalk.xmpp.service.DownloadListener;
import org.kontalk.xmpp.service.RequestListener;
import org.kontalk.xmpp.ui.MessagingPreferences;
import org.kontalk.xmpp.util.ProgressInputStreamEntity;
import org.kontalk.xmpp.util.ProgressOutputStreamEntity;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.util.Log;


public class ClientHTTPConnection {
    private static final String TAG = ClientHTTPConnection.class.getSimpleName();

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");


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
     * A generic download request, with optional authentication token.
     * @param token the authentication token
     * @param url URL to download
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase prepareURLDownload(String token, String url) throws IOException {
        HttpGet req = new HttpGet(url);

        if (token != null)
            req.addHeader(HEADER_NAME_AUTHORIZATION,
                    HEADER_VALUE_AUTHORIZATION + token);

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

    /** Downloads to a directory represented by a {@link File} object,
     * determining the file name from the Content-Disposition header. */
    public void downloadAutofilename(String url, File base, DownloadListener listener) throws IOException {
        _download(url, base, listener);
    }

    private void _download(String url, File base, DownloadListener listener) throws IOException {
        currentRequest = prepareURLDownload(mAuthToken, url);
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

}

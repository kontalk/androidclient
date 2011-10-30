package org.kontalk.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
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
import org.apache.http.message.BasicNameValuePair;
import org.kontalk.client.Protocol;
import org.kontalk.service.RequestListener;
import org.kontalk.util.ProgressInputStreamEntity;


/**
 * Defines a server and some methods to communicate with it.
 * @author Daniele Ricci
 * @version 1.0
 */
public class EndpointServer {

    public static final String SERVERINFO_PATH = "/serverinfo";
    public static final String VALIDATION_PATH = "/validation";
    public static final String AUTHENTICATION_PATH = "/authentication";
    public static final String LOOKUP_PATH = "/lookup";
    public static final String RECEIVED_PATH = "/received";
    public static final String MESSAGE_PATH = "/message";
    public static final String POLLING_PATH = "/polling";

    /** The authentication token header. */
    public static final String HEADER_NAME_AUTHORIZATION = "Authorization";
    public static final String HEADER_VALUE_AUTHORIZATION = "KontalkToken auth=";

    /** The recipients list header. */
    public static final String HEADER_RECIPIENTS = "X-Recipients";

    private final String baseURL;

    public EndpointServer(String baseURL) {
        this.baseURL = baseURL;
    }

    @Override
    public String toString() {
        return baseURL;
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
        StringBuilder uri = new StringBuilder(baseURL);
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

    public HttpRequestBase prepareValidation(String phone) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("n", phone));
        return prepare(VALIDATION_PATH, params, null, null, null, false);
    }

    public HttpRequestBase prepareAuthentication(String validationCode) throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>(1);
        params.add(new BasicNameValuePair("v", validationCode));
        return prepare(AUTHENTICATION_PATH, params, null, null, null, false);
    }

    public HttpRequestBase prepareLookup(String token, Collection<String> userId) throws IOException {
        Protocol.LookupRequest.Builder b = Protocol.LookupRequest.newBuilder();
        b.addAllUserId(userId);
        Protocol.LookupRequest req = b.build();
        return _prepareLookup(token, req);
    }

    public HttpRequestBase prepareLookup(String token, String userId) throws IOException {
        Protocol.LookupRequest.Builder b = Protocol.LookupRequest.newBuilder();
        b.addUserId(userId);
        Protocol.LookupRequest req = b.build();
        return _prepareLookup(token, req);
    }

    private HttpRequestBase _prepareLookup(String token, Protocol.LookupRequest req) throws IOException {
        return prepare(LOOKUP_PATH, null, token, null, req.toByteArray(), true);
    }

    /**
     * A polling method for the messaging server.
     * @param token the autentication token
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase preparePolling(String token) throws IOException {
        return prepare(POLLING_PATH, null, token, null, null, false);
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
    public HttpRequestBase prepareMessage(
            MessageSender job, RequestListener listener,
            String token, String[] group, String mime,
            InputStream data, long length) throws IOException {

        HttpPost req = (HttpPost) prepare(MESSAGE_PATH, null, token, mime, null, true);
        req.setEntity(new ProgressInputStreamEntity(data, length, job, listener));

        for (int i = 0; i < group.length; i++) {
            // TODO check multiple values support
            req.addHeader(HEADER_RECIPIENTS, group[i]);
        }

        return req;
    }

    /**
     * TODO
     * @param token
     * @param messageIds
     * @return
     * @throws IOException
     */
    public HttpRequestBase prepareReceived(String token, String[] messageIds)
            throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        for (String id : messageIds)
            params.add(new BasicNameValuePair("i", id));

        return _prepareReceived(token, params);
    }

    /**
     * TODO
     * @param token
     * @param messageIds
     * @return
     * @throws IOException
     */
    public HttpRequestBase prepareReceived(String token, Collection<String> messageIds)
            throws IOException {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        for (String id : messageIds)
            params.add(new BasicNameValuePair("i", id));

        return _prepareReceived(token, params);
    }

    private HttpRequestBase _prepareReceived(String token, List<NameValuePair> params)
            throws IOException {
        return prepare(RECEIVED_PATH, params, token, null, null, false);
    }

    /**
     * A generic download request, with optional authorization token.
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
    public HttpResponse execute(HttpRequestBase request) throws IOException {
        // execute!
        try {
            HttpClient client = new DefaultHttpClient();
            // handle redirects :)
            client.getParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, true);
            // HttpClient bug caused by Lighttpd
            client.getParams().setBooleanParameter("http.protocol.expect-continue", false);
            return client.execute(request);
        }
        catch (ClientProtocolException e) {
            IOException ie = new IOException("client protocol error");
            ie.initCause(e);
            throw ie;
        }

    }
}

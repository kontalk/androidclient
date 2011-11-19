/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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
import org.kontalk.service.RequestListener;
import org.kontalk.util.ProgressInputStreamEntity;


/**
 * Defines a server and some methods to communicate with it.
 * @author Daniele Ricci
 * @version 1.0
 */
public class EndpointServer {

    private static final String SERVERINFO_PATH = "/serverinfo";
    private static final String VALIDATION_PATH = "/validation";
    private static final String AUTHENTICATION_PATH = "/authentication";
    private static final String LOOKUP_PATH = "/lookup";
    private static final String RECEIVED_PATH = "/received";
    private static final String MESSAGE_PATH = "/message";
    private static final String POLLING_PATH = "/polling";
    private static final String DOWNLOAD_PATH = "/download";
    private static final String SERVERLIST_PATH = "/serverlist";
    private static final String UPDATE_PATH = "/update";

    /** The authentication token header. */
    private static final String HEADER_NAME_AUTHORIZATION = "Authorization";
    private static final String HEADER_VALUE_AUTHORIZATION = "KontalkToken auth=";

    /** Recipients list header. */
    private static final String HEADER_RECIPIENTS = "X-Recipients";
    /** Message flags header. */
    private static final String HEADER_MESSAGE_FLAGS = "X-Message-Flags";
    /** User status message header. */
    private static final String HEADER_STATUS_MESSAGE = "X-Status-Message";
    /** Google C2DM registration ID. */
    private static final String HEADER_GOOGLE_REGID = "X-Google-Registration";

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
        return prepare(VALIDATION_PATH + "/" + phone, null, null, null, null, false);
    }

    public HttpRequestBase prepareAuthentication(String validationCode) throws IOException {
        return prepare(AUTHENTICATION_PATH + "/" + validationCode, null, null, null, null, false);
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
            InputStream data, long length, boolean encrypted)
            throws IOException {

        HttpPost req = (HttpPost) prepare(MESSAGE_PATH, null, token, mime, null, true);
        req.setEntity(new ProgressInputStreamEntity(data, length, job, listener));

        for (int i = 0; i < group.length; i++) {
            // TODO check multiple values support
            req.addHeader(HEADER_RECIPIENTS, group[i]);
        }

        if (encrypted)
            req.addHeader(HEADER_MESSAGE_FLAGS, "encrypted");

        return req;
    }

    /**
     * Message received notification.
     * @param token
     * @param messageIds
     * @return the request
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
     * Message received notification.
     * @param token
     * @param messageIds
     * @return the request
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
     * Attachment download request.
     * @param token the authentication token
     * @param filename attachment filename
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase prepareDownload(String token, String filename) throws IOException {
        return prepare(DOWNLOAD_PATH + "/" + filename, null, token, null, null, false);
    }

    /**
     * Server list download request.
     * @param token the authentication token
     * @param filename attachment filename
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase prepareServerListRequest() throws IOException {
        return prepare(SERVERLIST_PATH, null, null, null, null, false);
    }

    /**
     * User update request.
     * @param token
     * @param messageIds
     * @return the request
     * @throws IOException
     */
    public HttpRequestBase prepareUpdate(String token, String statusMessage, String googleRegId)
            throws IOException {

        HttpRequestBase req = prepare(UPDATE_PATH, null, token, null, null, false);
        if (statusMessage != null)
            req.addHeader(HEADER_STATUS_MESSAGE, statusMessage);
        if (googleRegId != null)
            req.addHeader(HEADER_GOOGLE_REGID, googleRegId);

        return req;
    }

    public HttpRequestBase prepareServerinfo() throws IOException {
        return prepare(SERVERINFO_PATH, null, null, null, null, false);
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

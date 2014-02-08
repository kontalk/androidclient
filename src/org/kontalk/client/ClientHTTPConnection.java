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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.kontalk.service.DownloadListener;
import org.kontalk.util.ProgressOutputStreamEntity;

import android.content.Context;
import android.util.Log;


public class ClientHTTPConnection {
    private static final String TAG = ClientHTTPConnection.class.getSimpleName();

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");


    /** The authentication token header. */
    private static final String HEADER_NAME_AUTHORIZATION = "Authorization";
    private static final String HEADER_VALUE_AUTHORIZATION = "KontalkToken auth=";

    protected final Context mContext;
    protected final String mAuthToken;

    protected HttpRequestBase currentRequest;
    protected HttpClient mConnection;

    public ClientHTTPConnection(Context context, String token) {
        mContext = context;
        mAuthToken = token;
    }

    public void abort() {
        if (currentRequest != null)
            currentRequest.abort();
    }

    /**
     * A generic download request, with optional authentication token.
     * @param token the authentication token
     * @param url URL to download
     * @return the request object
     * @throws IOException
     */
    private HttpRequestBase prepareURLDownload(String token, String url) throws IOException {
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

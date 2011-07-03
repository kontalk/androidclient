package org.kontalk.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kontalk.service.RequestListener;
import org.kontalk.util.ProgressInputStreamEntity;


/**
 * Defines a server and some methods to communicate with it.
 * @author Daniele Ricci
 * @version 1.0
 */
public class EndpointServer {

    private static final String POLLING_PATH = "/polling.php";
    private static final String REQUEST_PATH = "/request.php?cmd=";
    private static final String MESSAGE_PATH = "/postmessage.php";
    private static final String DOWNLOAD_PATH = "/download.php?f=";

    /** The authentication token header. */
    public static final String HEADER_AUTH_TOKEN = "X-Auth-Token";

    /** The recipients list header. */
    public static final String HEADER_RECIPIENTS = "X-Recipients";

    private final String requestURL;
    private final String pollingURL;
    private final String messageURL;
    private final String downloadURL;

    public EndpointServer(String baseURL) {
        this.requestURL = baseURL + REQUEST_PATH;
        this.pollingURL = baseURL + POLLING_PATH;
        this.messageURL = baseURL + MESSAGE_PATH;
        this.downloadURL = baseURL + DOWNLOAD_PATH;
    }

    /**
     * A generic endpoint request method for the messaging server.
     * @param cmd the request command
     * @param params additional GET parameters
     * @param token the autentication token (if needed)
     * @param content the POST body content, if null it will use GET
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase prepareRequest(String cmd,
            List<NameValuePair> params, String token,
            byte[] content) throws IOException {

        HttpRequestBase req;

        // compose uri
        String extra = (params != null) ?
                URLEncodedUtils.format(params, "UTF-8") : "";
        String uri = requestURL + cmd + "&" + extra;

        // request type
        if (content != null) {
            req = new HttpPost(uri);
            req.setHeader("Content-Type", "text/xml");
            ((HttpPost)req).setEntity(new ByteArrayEntity(content));
        }
        else
            req = new HttpGet(uri);

        // token
        if (token != null)
            req.setHeader(HEADER_AUTH_TOKEN, token);

        return req;
    }

    /**
     * A polling method for the messaging server.
     * @param token the autentication token
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase preparePolling(String token) throws IOException {
        HttpGet req = new HttpGet(pollingURL);

        if (token != null)
            req.setHeader(HEADER_AUTH_TOKEN, token);

        return req;
    }

    /**
     * A message posting method for the postmessage service.
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

        HttpPut req = new HttpPut(messageURL);
        req.setEntity(new ProgressInputStreamEntity(data, length, job, listener));

        if (token != null)
            req.setHeader(HEADER_AUTH_TOKEN, token);

        // standard headers
        req.setHeader("Content-Type", mime);
        // mmm... "header Content-Length already added..."
        //req.setHeader("Content-Length", String.valueOf(length));

        for (int i = 0; i < group.length; i++) {
            // TODO check multiple values support
            req.addHeader(HEADER_RECIPIENTS, group[i]);
        }

        return req;
    }

    /**
     * A download method for the upload service.
     * @param token the autentication token
     * @param filename filename to download
     * @return the request object
     * @throws IOException
     */
    public HttpRequestBase prepareDownload(String token, String filename) throws IOException {
        HttpGet req;

        // compose uri
        String uri = downloadURL + URLEncoder.encode(filename, "UTF-8");
        req = new HttpGet(uri);

        if (token != null)
            req.addHeader(HEADER_AUTH_TOKEN, token);

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

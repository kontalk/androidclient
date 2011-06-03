package org.nuntius.client;

import java.io.IOException;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;


/**
 * Defines a server and some methods to communicate with it.
 * @author Daniele Ricci
 * @version 1.0
 */
public class EndpointServer {

    private static final String POLLING_PATH = "/polling.php";
    private static final String REQUEST_PATH = "/request.php?cmd=";

    /**
     * The authentication token header.
     */
    public static final String HEADER_AUTH_TOKEN = "X-Auth-Token";

    private final String requestURL;
    private final String pollingURL;

    public EndpointServer(String baseURL) {
        this.requestURL = baseURL + REQUEST_PATH;
        this.pollingURL = baseURL + POLLING_PATH;
    }

    /**
     * A generic endpoint request method for the messaging server.
     * @param cmd the request command
     * @param params additional GET parameters
     * @param token the autentication token (if needed)
     * @param content the POST body content, if null it will use GET
     * @return the response
     * @throws IOException
     */
    public HttpRequestBase prepareRequest(String cmd,
            List<NameValuePair> params, String token,
            String content) throws IOException {

        HttpRequestBase req;

        // compose uri
        String extra = (params != null) ?
                URLEncodedUtils.format(params, "UTF-8") : "";
        String uri = requestURL + cmd + "&" + extra;

        // request type
        if (content != null) {
            req = new HttpPost(uri);
            req.addHeader("Content-Type", "text/xml");
            ((HttpPost)req).setEntity(new StringEntity(content, "UTF-8"));
        }
        else
            req = new HttpGet(uri);

        // token
        if (token != null)
            req.addHeader(HEADER_AUTH_TOKEN, token);

        return req;
    }

    /**
     * A polling method for the messaging server.
     * @param token the autentication token (if needed)
     * @return the response
     * @throws IOException
     */
    public HttpRequestBase preparePolling(String token) throws IOException {
        HttpGet req = new HttpGet(pollingURL);

        if (token != null)
            req.addHeader(HEADER_AUTH_TOKEN, token);

        return req;
    }

    public HttpResponse execute(HttpRequestBase request) throws IOException {
        // execute!
        try {
            HttpClient client = new DefaultHttpClient();
            return client.execute(request);
        }
        catch (ClientProtocolException e) {
            IOException ie = new IOException("client protocol error");
            ie.initCause(e);
            throw ie;
        }

    }
}

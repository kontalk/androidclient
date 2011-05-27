package org.nuntius.android.client;

import android.content.Context;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

/**
 * Generic HTTP request client.
 * @author Daniele Ricci
 * @version 1.0
 */
public abstract class AbstractClient extends AsyncHttpClient {

    public static final String HEADER_AUTH_TOKEN = "X-Auth-Token";

    protected final Context mContext;
    protected final EndpointServer mServer;
    protected String mAuthToken;
    protected AsyncHttpResponseHandler mHandler;

    public AbstractClient(Context context, EndpointServer server) {
        this(context, server, null);
    }

    public AbstractClient(Context context, EndpointServer server, String userAgent) {
        super(userAgent);

        this.mContext = context;
        this.mServer = server;
    }

    public void setAuthToken(String token) {
        this.mAuthToken = token;
    }

    public void setResponseHandler(AsyncHttpResponseHandler handler) {
        this.mHandler = handler;
    }

}

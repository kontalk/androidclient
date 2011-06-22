package org.nuntius.client;

import org.apache.http.client.methods.HttpRequestBase;

import android.content.Context;


public abstract class AbstractClient {

    protected final Context mContext;
    protected final EndpointServer mServer;
    protected final String mAuthToken;
    protected HttpRequestBase currentRequest;

    public AbstractClient(Context context, EndpointServer server, String token) {
        mContext = context;
        mServer = server;
        mAuthToken = token;
    }

    public void abort() {
        if (currentRequest != null)
            currentRequest.abort();
    }
}

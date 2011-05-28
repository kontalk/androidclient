package org.nuntius.client;

import org.apache.http.client.methods.HttpRequestBase;


public abstract class AbstractClient {

    protected EndpointServer mServer;
    protected String mAuthToken;
    protected HttpRequestBase currentRequest;

    public AbstractClient(EndpointServer server, String token) {
        mServer = server;
        mAuthToken = token;
    }

    public void abort() {
        if (currentRequest != null)
            currentRequest.abort();
    }
}

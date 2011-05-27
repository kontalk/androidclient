package org.nuntius.android.client;

public class RequestClient {

    private EndpointServer mServer;
    private String mAuthToken;

    public RequestClient(EndpointServer server, String token) {
        mServer = server;
        mAuthToken = token;
    }

}

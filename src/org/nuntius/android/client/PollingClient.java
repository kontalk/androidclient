package org.nuntius.android.client;

import java.io.IOException;

import org.apache.http.HttpResponse;


/**
 * A client for the polling service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class PollingClient {

    private EndpointServer mServer;
    private String mAuthToken;

    public PollingClient(EndpointServer server, String token) {
        mServer = server;
        mAuthToken = token;
    }

    /**
     * Polls server for new messages.
     * @throws IOException
     */
    public void poll() throws IOException {
        HttpResponse response = mServer.polling(mAuthToken);
        // TODO process response
    }
}

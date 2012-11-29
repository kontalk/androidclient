package org.kontalk.client;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;

public class KontalkConnection extends XMPPConnection {

    protected EndpointServer mServer;

    public KontalkConnection(EndpointServer server) {
        super(new ConnectionConfiguration(server.getHost(), server.getPort()));

        mServer = server;
        config.setSASLAuthenticationEnabled(true);
        config.setReconnectionAllowed(true);
        config.setServiceName(server.getNetwork());
    }

}

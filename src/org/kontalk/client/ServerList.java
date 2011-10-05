package org.kontalk.client;

import java.util.ArrayList;
import java.util.Collection;


/**
 * A convenient server list using {@link EndpointServer}.
 * @author Daniele Ricci
 */
public class ServerList extends ArrayList<EndpointServer> {

    private static final long serialVersionUID = -8798498388829449277L;

    public ServerList() {
        super();
    }

    public ServerList(Collection<EndpointServer> list) {
        super(list);
    }

}

package org.kontalk.client;

import org.kontalk.service.ClientThread;


/**
 * Interface for listening to client connection events.
 * @author Daniele Ricci
 */
public interface ClientListener {

    /** Called when a connection to the server has been estabilished. */
    public void connected(ClientThread client);
}

package org.kontalk.client;

import org.kontalk.service.ClientThread;


/**
 * Interface for listening to client thread events.
 * @author Daniele Ricci
 */
public interface ClientListener {

    /** Called when a connection instance has been created. */
    public void created(ClientThread client);

    /** Called when a connection has been estabilished. */
    public void connected(ClientThread client);

    /** Called after successful authentication. */
    public void authenticated(ClientThread client);
}

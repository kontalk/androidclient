package org.kontalk.client;

import com.google.protobuf.MessageLite;


/**
 * Listener interface for client transactions.
 * @author Daniele Ricci
 */
public interface TxListener {

    /** Called when a pack has been received from the server. */
    public void tx(ClientConnection connection, String txId, MessageLite pack);

}

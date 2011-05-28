package org.nuntius.service;

import java.util.List;

import org.nuntius.client.AbstractMessage;

/**
 * Listener interface for message receivers.
 * @author Daniele Ricci
 * @version 1.0
 */
public interface MessageListener {

    /**
     * Notifies pending incoming messages.
     * @param messages
     */
    public void incoming(List<AbstractMessage<?>> messages);
}

package org.kontalk.ui.view;


import android.net.Uri;

import org.kontalk.message.MessageComponent;

/**
 * Listeners for the composer bar.
 * @author Daniele Ricci
 */
public interface ComposerListener {

    /** Sends a text message. */
    void sendTextMessage(String message);

    /** Sends a binary message. */
    void sendBinaryMessage(Uri uri, String mime, boolean media,
        Class<? extends MessageComponent<?>> klass);

    /**
     * Sends a typing notification.
     * @return true if the notification was sent
     */
    boolean sendTyping();

    /** Asks the parent to stop all sounds. */
    void stopAllSounds();

}

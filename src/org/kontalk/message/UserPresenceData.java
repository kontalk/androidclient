package org.kontalk.message;


/**
 * Internal class used by {@link UserPresenceMessage}.
 * @author Daniele Ricci
 */
public class UserPresenceData {
    public final int event;
    public final String statusMessage;
    // TODO timestamp?

    public UserPresenceData(int event, String statusMessage) {
        this.event = event;
        this.statusMessage = statusMessage;
    }
}

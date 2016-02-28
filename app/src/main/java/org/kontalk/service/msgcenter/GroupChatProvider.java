package org.kontalk.service.msgcenter;


import org.jivesoftware.smack.packet.Stanza;

/**
 * Interface for group chat XMPP implementation modules for the message center.
 * @author Daniele Ricci
 */
public interface GroupChatProvider {

    /** Transforms the given stanza for group chat (before encryption). */
    void transform(String groupJid, String[] to, Stanza message);

    /** Transforms the given stanza for group chat (after encryption). */
    void transformEncrypted(String groupJid, String[] to, Stanza message);

    /** Returns the group JID for the given message, if any. */
    String getGroupJid(Stanza message);

}

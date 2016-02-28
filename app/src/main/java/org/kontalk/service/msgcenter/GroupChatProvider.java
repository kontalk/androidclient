package org.kontalk.service.msgcenter;

import org.jivesoftware.smack.packet.Stanza;


/**
 * Interface for group chat XMPP implementation modules for the message center.
 * @author Daniele Ricci
 */
public interface GroupChatProvider {

    /** Transforms the given stanza for group chat (before encryption). */
    void transform(String groupJid, String[] to, Stanza message, boolean creating);

    /** Transforms the given stanza for group chat (after encryption). */
    void transformEncrypted(String groupJid, String[] to, Stanza message);

    /** Returns the group JID for the given message, if any. */
    String getGroupJid(Stanza message);

    /** Returns the group subject for the given message, if any. */
    String getGroupSubject(Stanza message, String from);

    /** Returns the group members for the given message, if any. */
    String[] getGroupMembers(Stanza message, String from);

}

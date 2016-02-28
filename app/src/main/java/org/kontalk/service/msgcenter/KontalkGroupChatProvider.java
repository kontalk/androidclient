package org.kontalk.service.msgcenter;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.address.packet.MultipleAddresses;
import org.jxmpp.util.XmppStringUtils;

import org.kontalk.client.GroupExtension;


/**
 * Implements Kontalk group chat protocol.
 * @author Daniele Ricci
 */
public class KontalkGroupChatProvider implements GroupChatProvider {

    @Override
    public void transform(String groupJid, String[] to, Stanza message) {
        String groupId = XmppStringUtils.parseLocalpart(groupJid);
        String groupOwner = XmppStringUtils.parseDomain(groupJid);
        GroupExtension g = new GroupExtension(groupId, groupOwner);
        message.addExtension(g);
    }

    @Override
    public void transformEncrypted(String groupJid, String[] to, Stanza message) {
        MultipleAddresses p = new MultipleAddresses();
        for (String rcpt : to)
            p.addAddress(MultipleAddresses.Type.to, rcpt, null, null, false, null);
        message.addExtension(p);
    }

    @Override
    public String getGroupJid(Stanza message) {
        if (message instanceof Message) {
            ExtensionElement _group = message.getExtension(GroupExtension.ELEMENT_NAME, GroupExtension.NAMESPACE);
            if (_group instanceof GroupExtension)
                return ((GroupExtension) _group).getJID();
        }
        return null;
    }

}

package org.kontalk.service.msgcenter;

import java.util.List;

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
    public void transform(String groupJid, String[] to, Stanza message, boolean creating) {
        String groupId = XmppStringUtils.parseLocalpart(groupJid);
        String groupOwner = XmppStringUtils.parseDomain(groupJid);
        GroupExtension g = new GroupExtension(groupId, groupOwner);
        if (creating) {
            for (String jid : to)
                g.addMember(jid);
        }
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
        GroupExtension group = getGroupExtension(message);
        return group != null ? group.getJID() : null;
    }

    @Override
    public String getGroupSubject(Stanza message, String from) {
        GroupExtension group = getGroupExtensionIfOwner(message, from);
        return (group != null) ? group.getSubject() : null;
    }

    @Override
    public String[] getGroupMembers(Stanza message, String from) {
        GroupExtension group = getGroupExtensionIfOwner(message, from);
        if (group != null) {
            List<GroupExtension.Member> members = group.getMembers();
            String[] users = new String[members.size()+1];
            // the owner is also a member
            users[0] = group.getOwner();
            for (int i = 1; i < users.length; i++) {
                users[i] = members.get(i-1).jid;
            }
            return users;
        }
        return null;
    }

    private GroupExtension getGroupExtensionIfOwner(Stanza message, String from) {
        GroupExtension group = getGroupExtension(message);
        if (group != null) {
            if (from == null)
                from = XmppStringUtils.parseBareJid(message.getFrom());
            return from.equalsIgnoreCase(group.getOwner()) ? group : null;
        }
        return null;
    }

    private GroupExtension getGroupExtension(Stanza message) {
        if (message instanceof Message) {
            ExtensionElement _group = message.getExtension(GroupExtension.ELEMENT_NAME, GroupExtension.NAMESPACE);
            if (_group instanceof GroupExtension)
                return (GroupExtension) _group;
        }
        return null;
    }

}

package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.PacketExtension;


/** Dummy extension for marking a message as encrypted. */
public class MessageEncrypted implements PacketExtension {

    public static final String NAMESPACE = "http://www.kontalk.org/extensions/message#encrypted";
    public static final String ELEMENT_NAME = "c";

    private static final String XML = "<" + ELEMENT_NAME + " xmlns='" + NAMESPACE + "'/>";

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String toXML() {
        return XML;
    }

}

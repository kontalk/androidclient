package org.kontalk.client;

import org.jivesoftware.smack.packet.PacketExtension;


public class ServerReceiptRequest implements PacketExtension {
    public static final String NAMESPACE = "urn:xmpp:server-receipts";
    public static final String ELEMENT_NAME = "request";

    private static final String XML = String.format("<%s xmlns='%s'/>", ELEMENT_NAME, NAMESPACE);

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

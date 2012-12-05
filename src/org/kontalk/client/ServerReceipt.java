package org.kontalk.client;

import java.util.Locale;

import org.jivesoftware.smack.packet.PacketExtension;


public abstract class ServerReceipt implements PacketExtension {
    public static final String NAMESPACE = "urn:xmpp:server-receipts";

    private static final String XML = "<%s xmlns='" + NAMESPACE + "' id='%s'/>";

    private String id;
    private String type;

    public ServerReceipt(String type) {
        this(type, null);
    }

    public ServerReceipt(String type, String id) {
        this.type = type;
        this.id = id;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String toXML() {
        return String.format(Locale.US, XML, type, id);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}

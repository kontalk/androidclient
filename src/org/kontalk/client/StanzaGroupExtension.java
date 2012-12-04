package org.kontalk.client;

import java.util.Locale;

import org.jivesoftware.smack.packet.PacketExtension;


public class StanzaGroupExtension implements PacketExtension {
    public static final String NAMESPACE = "urn:xmpp:stanza-group";
    public static final String ELEMENT_NAME = "group";

    private static final String XML = "<group xmlns='" + NAMESPACE + "' id='%s' count='%d'/>";

    private String id;
    private int count;

    public StanzaGroupExtension() {
    }

    public StanzaGroupExtension(String id, int count) {
        this.id = id;
        this.count = count;
    }

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
        return String.format(Locale.US, XML, id, count);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

}

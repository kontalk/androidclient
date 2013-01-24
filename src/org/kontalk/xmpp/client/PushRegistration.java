package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.PacketExtension;


/** Capability extension for registering to push notifications. */
public class PushRegistration implements PacketExtension {
    public static final String NAMESPACE = "http://www.kontalk.org/extensions/presence#push";
    public static final String ELEMENT_NAME = "c";

    private final String mRegId;

    public PushRegistration(String regId) {
        mRegId = regId;
    }

    private static final String XML = "<" + ELEMENT_NAME + " xmlns='" + NAMESPACE + "' provider='gcm'>%s</" + ELEMENT_NAME + ">";

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
        return String.format(XML, mRegId);
    }

}

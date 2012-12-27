package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.Packet;


/** A wrapper to a predefined XML string. */
public final class RawPacket extends Packet {
    private final String _xml;

    public RawPacket(String xml) {
        _xml = xml;
    }

    @Override
    public String toXML() {
        return _xml;
    }

}

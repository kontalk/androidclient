package org.kontalk.client;

import org.jivesoftware.smack.packet.Packet;


/** A presence stanza with type "probe". */
public class ProbePresence extends Packet {
    private static final String XML = "<presence id=\"%s\" type=\"probe\" to=\"%s\"/>";

    private CharSequence to;
    private String _xml;

    public ProbePresence(CharSequence to) {
        super();
        this.to = to;
    }

    @Override
    public String toXML() {
        // cache XML for future use
        if (_xml == null)
            _xml = String.format(XML, getPacketID(), to);
        return _xml;
    }

    public static String quickXML(String id, String to) {
        return String.format(XML, id, to);
    }

}

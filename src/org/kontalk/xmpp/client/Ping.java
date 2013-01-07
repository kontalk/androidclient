package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.XmlPullParser;


/**
 * XEP-0199: XMPP Ping
 * http://xmpp.org/extensions/xep-0199.html
 * @author Daniele Ricci
 */
public class Ping extends IQ {
    public static final String ELEMENT_NAME = "ping";
    public static final String NAMESPACE  = "urn:xmpp:ping";

    private static final String XML = String.format("<%s xmlns='%s'/>", ELEMENT_NAME, NAMESPACE);

    @Override
    public String getChildElementXML() {
        return XML;
    }

    public static final class Provider implements IQProvider {
        @Override
        public IQ parseIQ(XmlPullParser parser) throws Exception {
            return new Ping();
        }
    }

}

package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.PacketExtension;


/**
 * XEP-0066: Out of Band Data
 * http://xmpp.org/extensions/xep-0066.html
 */
public class OutOfBandData implements PacketExtension {

    public static final String NAMESPACE = "jabber:x:oob";
    public static final String ELEMENT_NAME = "x";

    private final String mUrl;
    private final String mMime;

    public OutOfBandData(String url) {
        this(url, null);
    }

    public OutOfBandData(String url, String mime) {
        mUrl = url;
        mMime = mime;
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
        /*
  <x xmlns='jabber:x:oob'>
    <url type='image/png'>http://prime.kontalk.net/media/filename_or_hash</url>
  </x>
         */
        StringBuilder xml = new StringBuilder();
        xml.append(String.format("<%s xmlns='%s'><url", ELEMENT_NAME, NAMESPACE));
        if (mMime != null)
            xml.append(String.format(" type='%s'", mMime));

        xml
            .append(">")
            // TODO should we escape this?
            .append(mUrl)
            .append(String.format("</url></%s>", ELEMENT_NAME));
        return xml.toString();
    }

}

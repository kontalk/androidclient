package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;


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

    public String getUrl() {
        return mUrl;
    }

    public String getMime() {
        return mMime;
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

    public static final class Provider implements PacketExtensionProvider {

        @Override
        public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
            String url = null, mime = null;
            boolean in_url = false, done = false;

            while (!done)
            {
                int eventType = parser.next();

                if (eventType == XmlPullParser.START_TAG)
                {
                    if ("url".equals(parser.getName())) {
                        in_url = true;
                        mime = parser.getAttributeValue(null, "type");
                    }

                }
                else if (eventType == XmlPullParser.END_TAG)
                {
                    if ("url".equals(parser.getName())) {
                        done = true;
                    }
                }
                else if (eventType == XmlPullParser.TEXT && in_url) {
                    url = parser.getText();
                }
            }

            if (url != null)
                return new OutOfBandData(url, mime);
            else
                return null;
        }

    }

}

package org.kontalk.client;

import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;


public class StanzaGroupExtensionProvider extends EmbeddedExtensionProvider {

    /*
    @Override
    public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
        StanzaGroupExtension ext = new StanzaGroupExtension();
        boolean done = false;
        while (!done) {
            int eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("id")) {
                    ext.setId(parser.nextText());
                }
                if (parser.getName().equals("count")) {
                    int count;
                    try {
                        count = Integer.parseInt(parser.nextText());
                    }
                    catch (Exception e) {
                        count = 1;
                    }

                    ext.setCount(count);
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.getName().equals(StanzaGroupExtension.ELEMENT_NAME)) {
                    done = true;
                }
            }
        }

        return ext;
    }
    */

    @Override
    protected PacketExtension createReturnExtension(String currentElement, String currentNamespace,
        Map<String, String> attributeMap, List<? extends PacketExtension> content) {
        int count;
        try {
            count = Integer.parseInt(attributeMap.get("count"));
        }
        catch (Exception e) {
            count = 1;
        }
        return new StanzaGroupExtension(attributeMap.get("id"), count);
    }


}

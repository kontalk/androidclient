/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;


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

    public static class Provider extends EmbeddedExtensionProvider {

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

}

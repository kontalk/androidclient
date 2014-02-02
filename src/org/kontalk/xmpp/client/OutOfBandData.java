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
    private final long mLength;

    public OutOfBandData(String url) {
        this(url, null, -1);
    }

    public OutOfBandData(String url, String mime, long length) {
        mUrl = url;
        mMime = mime;
        mLength = length;
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

    public long getLength() {
        return mLength;
    }

    @Override
    public String toXML() {
        /*
  <x xmlns='jabber:x:oob'>
    <url type='image/png' length='2034782'>http://prime.kontalk.net/media/filename_or_hash</url>
  </x>
         */
        StringBuilder xml = new StringBuilder();
        xml.append(String.format("<%s xmlns='%s'><url", ELEMENT_NAME, NAMESPACE));
        if (mMime != null)
            xml.append(String.format(" type='%s'", mMime));

        if (mLength >= 0)
            xml.append(String.format(" length='%d'", mLength));

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
            long length = -1;
            boolean in_url = false, done = false;

            while (!done)
            {
                int eventType = parser.next();

                if (eventType == XmlPullParser.START_TAG)
                {
                    if ("url".equals(parser.getName())) {
                        in_url = true;
                        mime = parser.getAttributeValue(null, "type");
                        String _length = parser.getAttributeValue(null, "length");
                        try {
                            length = Long.parseLong(_length);
                        }
                        catch (Exception e) {
                            // ignored
                        }
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
                return new OutOfBandData(url, mime, length);
            else
                return null;
        }

    }

}

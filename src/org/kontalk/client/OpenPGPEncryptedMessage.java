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

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;

import android.util.Base64;


/**
 * Minimal implementation of XEP-0027.
 * http://xmpp.org/extensions/xep-0027.html#encrypting
 * @author Daniele Ricci
 */
public class OpenPGPEncryptedMessage implements PacketExtension {

    public static final String ELEMENT_NAME = "x";
    public static final String NAMESPACE = "jabber:x:encrypted";

    private byte[] mData;

    public OpenPGPEncryptedMessage(byte[] data) {
        mData = data;
    }

    public byte[] getData() {
        return mData;
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
        return new StringBuilder()
            .append('<')
            .append(ELEMENT_NAME)
            .append(" xmlns='")
            .append(NAMESPACE)
            .append("'><![CDATA[")
            .append(Base64.encodeToString(mData, Base64.NO_WRAP))
            .append("]]></")
            .append(ELEMENT_NAME)
            .append('>')
            .toString();
    }

    public static final class Provider implements PacketExtensionProvider {

        @Override
        public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
            String contents = null;
            boolean done = false;

            while (!done)
            {
                int eventType = parser.next();

                if (eventType == XmlPullParser.END_TAG)
                {
                    if (ELEMENT_NAME.equals(parser.getName())) {
                        done = true;
                    }
                }
                else if (eventType == XmlPullParser.TEXT) {
                    contents = parser.getText();
                }
            }

            if (contents != null)
                return new OpenPGPEncryptedMessage(Base64.decode(contents, Base64.DEFAULT));
            else
                return null;
        }

    }

}

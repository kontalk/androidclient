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
 * Very basic e2e (RFC 3923) extension.
 * @author Daniele Ricci
 * @see http://tools.ietf.org/html/rfc3923
 */
public class E2EEncryption implements PacketExtension {
	public static final String ELEMENT_NAME = "e2e";
	public static final String NAMESPACE = "urn:ietf:params:xml:ns:xmpp-e2e";

	private byte[] mData;
	private String mEncoded;

	public E2EEncryption(byte[] data) {
		mData = data;
	}

	public E2EEncryption(String encoded) {
		mEncoded = encoded;
		mData = Base64.decode(encoded, Base64.DEFAULT);
	}

	@Override
	public String getElementName() {
		return ELEMENT_NAME;
	}

	@Override
	public String getNamespace() {
		return NAMESPACE;
	}

	public byte[] getData() {
		return mData;
	}

	@Override
	public String toXML() {
		if (mEncoded == null)
			mEncoded = Base64.encodeToString(mData, Base64.NO_WRAP);

		return new StringBuilder()
			.append('<')
			.append(ELEMENT_NAME)
			.append(" xmlns='")
			.append(NAMESPACE)
			.append("'><![CDATA[")
			.append(mEncoded)
			.append("]]></")
			.append(ELEMENT_NAME)
			.append('>')
			.toString();
	}

	public static class Provider implements PacketExtensionProvider {

		@Override
		public PacketExtension parseExtension(XmlPullParser parser) throws Exception {
			boolean done = false;
			String data = null;

			while (!done) {
				int eventType = parser.next();

				if (eventType == XmlPullParser.TEXT) {
					data = parser.getText();
				}
				else if (eventType == XmlPullParser.END_TAG) {
					if (ELEMENT_NAME.equals(parser.getName()))
						done = true;
				}
			}

			if (data != null)
				return new E2EEncryption(data);
			else
				return null;
		}

	}

}

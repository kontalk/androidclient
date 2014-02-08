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

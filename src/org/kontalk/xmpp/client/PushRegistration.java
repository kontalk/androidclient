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


/** Capability extension for registering to push notifications. */
public class PushRegistration implements PacketExtension {
    public static final String NAMESPACE = "http://kontalk.org/extensions/presence#push";
    public static final String ELEMENT_NAME = "c";

    private final String mRegId;

    public PushRegistration(String regId) {
        mRegId = regId;
    }

    private static final String XML = "<" + ELEMENT_NAME + " xmlns='" + NAMESPACE + "' provider='gcm'>%s</" + ELEMENT_NAME + ">";

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
        return String.format(XML, mRegId);
    }

}

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

import java.util.Locale;

import org.jivesoftware.smack.packet.PacketExtension;


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

}

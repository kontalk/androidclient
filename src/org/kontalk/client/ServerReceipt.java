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

import java.util.Locale;

import org.jivesoftware.smack.packet.PacketExtension;


public abstract class ServerReceipt implements PacketExtension {
    public static final String NAMESPACE = "urn:xmpp:server-receipts";

    private static final String XML = "<%s xmlns='" + NAMESPACE + "' id='%s'/>";

    private String id;
    private String type;

    public ServerReceipt(String type) {
        this(type, null);
    }

    public ServerReceipt(String type, String id) {
        this.type = type;
        this.id = id;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String toXML() {
        return String.format(Locale.US, XML, type, id);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}

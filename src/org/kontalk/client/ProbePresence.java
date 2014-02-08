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

import org.jivesoftware.smack.packet.Packet;


/** A presence stanza with type "probe". */
public class ProbePresence extends Packet {
    private static final String XML = "<presence id=\"%s\" type=\"probe\" to=\"%s\"/>";

    private CharSequence to;
    private String _xml;

    public ProbePresence(CharSequence to) {
        super();
        this.to = to;
    }

    @Override
    public String toXML() {
        // cache XML for future use
        if (_xml == null)
            _xml = String.format(XML, getPacketID(), to);
        return _xml;
    }

    public static String quickXML(String id, String to) {
        return String.format(XML, id, to);
    }

}

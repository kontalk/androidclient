/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.service.msgcenter;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqversion.packet.Version;

import org.kontalk.service.msgcenter.event.VersionEvent;


/**
 * Packet listener for version information stanzas.
 * @author Daniele Ricci
 */
class VersionListener implements StanzaListener {

    @Override
    public void processStanza(Stanza packet) {
        Version p = (Version) packet;
        MessageCenterService.bus()
            .post(new VersionEvent(p.getFrom(), p.getName(), p.getVersion(), p.getStanzaId()));
    }

}

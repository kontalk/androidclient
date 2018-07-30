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

import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqversion.packet.Version;

import android.content.Intent;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_VERSION;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_VERSION_NAME;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_VERSION_NUMBER;


/**
 * Packet listener for version information stanzas.
 * @author Daniele Ricci
 */
class VersionListener extends MessageCenterPacketListener {

    public VersionListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        Version p = (Version) packet;
        Intent i = new Intent(ACTION_VERSION);
        i.putExtra(EXTRA_PACKET_ID, p.getStanzaId());

        i.putExtra(EXTRA_FROM, p.getFrom().toString());
        i.putExtra(EXTRA_TO, p.getTo().toString());

        i.putExtra(EXTRA_VERSION_NAME, p.getName());
        i.putExtra(EXTRA_VERSION_NUMBER, p.getVersion());

        sendBroadcast(i);
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_LAST_ACTIVITY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_SECONDS;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TYPE;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_ERROR_CONDITION;

import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;

import android.content.Intent;

import org.kontalk.util.XMPPUtils;


/**
 * Packet listener for last activity iq.
 * @author Daniele Ricci
 */
class LastActivityListener extends MessageCenterPacketListener {

    public LastActivityListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        LastActivity p = (LastActivity) packet;
        Intent i = new Intent(ACTION_LAST_ACTIVITY);
        i.putExtra(EXTRA_PACKET_ID, p.getStanzaId());

        i.putExtra(EXTRA_TYPE, p.getType().toString());
        i.putExtra(EXTRA_FROM, p.getFrom().toString());
        i.putExtra(EXTRA_TO, p.getTo().toString());
        i.putExtra(EXTRA_SECONDS, p.getIdleTime());

        XMPPError.Condition errCondition = XMPPUtils.getErrorCondition(packet);
        if (errCondition != null)
            i.putExtra(EXTRA_ERROR_CONDITION, errCondition.toString());

        sendBroadcast(i);
    }
}

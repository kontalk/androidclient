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

import java.util.List;

import org.jivesoftware.smack.ExceptionCallback;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

import org.kontalk.client.RosterMatch;
import org.kontalk.service.msgcenter.event.RosterMatchEvent;


/**
 * Packet listener for roster match iq stanzas.
 * @author Daniele Ricci
 */
class RosterMatchListener implements StanzaListener, ExceptionCallback {

    private final IQ mRequest;

    public RosterMatchListener(IQ request) {
        mRequest = request;
    }

    @Override
    public void processStanza(Stanza packet) {
        RosterMatch p = (RosterMatch) packet;

        List<String> items = p.getItems();
        Jid[] list;
        if (items != null) {
            list = new Jid[items.size()];
            for (int i = 0; i < items.size(); i++) {
                list[i] = JidCreate.fromOrThrowUnchecked(items.get(i));
            }
        }
        else {
            list = new Jid[0];
        }

        MessageCenterService.bus()
            .post(new RosterMatchEvent(list, packet.getStanzaId()));
    }

    @Override
    public void processException(Exception exception) {
        MessageCenterService.bus()
            .post(new RosterMatchEvent(exception, mRequest.getStanzaId()));
    }
}

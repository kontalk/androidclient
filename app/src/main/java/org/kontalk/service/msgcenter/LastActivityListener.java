/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import org.jivesoftware.smack.ExceptionCallback;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jxmpp.jid.Jid;

import org.kontalk.service.msgcenter.event.LastActivityEvent;


/**
 * Packet listener for last activity iq.
 * @author Daniele Ricci
 */
class LastActivityListener implements StanzaListener, ExceptionCallback {

    @Override
    public void processStanza(Stanza packet) {
        LastActivity p = (LastActivity) packet;
        MessageCenterService.bus()
            .post(new LastActivityEvent(p.getFrom(), p.getIdleTime(), p.getStanzaId()));
    }

    @Override
    public void processException(Exception exception) {
        if (exception instanceof XMPPException.XMPPErrorException) {
            String id = ((XMPPException.XMPPErrorException) exception)
                .getStanzaError().getStanza().getStanzaId();
            Jid jid = ((XMPPException.XMPPErrorException) exception)
                .getStanzaError().getStanza().getFrom();
            MessageCenterService.bus()
                .post(new LastActivityEvent(exception, jid, id));
        }
        // we currently don't handle reply timeouts
    }
}

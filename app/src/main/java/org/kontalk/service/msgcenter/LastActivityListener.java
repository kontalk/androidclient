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

import android.content.Intent;

import org.jivesoftware.smack.ExceptionCallback;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_LAST_ACTIVITY;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_ERROR_EXCEPTION;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_SECONDS;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TYPE;


/**
 * Packet listener for last activity iq.
 * @author Daniele Ricci
 */
class LastActivityListener extends MessageCenterPacketListener implements ExceptionCallback {

    public LastActivityListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processStanza(Stanza packet) {
        Intent i = prepareIntent(packet, ACTION_LAST_ACTIVITY);

        LastActivity p = (LastActivity) packet;
        i.putExtra(EXTRA_SECONDS, p.getIdleTime());
        i.putExtra(EXTRA_TYPE, p.getType().toString());

        sendBroadcast(i);
    }

    @Override
    public void processException(Exception exception) {
        if (exception instanceof XMPPException.XMPPErrorException) {
            Intent i = prepareIntent(((XMPPException.XMPPErrorException) exception)
                .getXMPPError().getStanza(), ACTION_LAST_ACTIVITY);
            i.putExtra(EXTRA_TYPE, IQ.Type.error.toString());
            i.putExtra(EXTRA_ERROR_EXCEPTION, exception);
            sendBroadcast(i);
        }
        // we currently don't handle reply timeouts
    }
}

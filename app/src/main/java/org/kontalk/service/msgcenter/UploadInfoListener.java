/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import org.kontalk.client.UploadInfo;

import android.util.Log;


/**
 * Packet listener for upload info responses.
 * @author Daniele Ricci
 */
class UploadInfoListener extends MessageCenterPacketListener {

    public UploadInfoListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Stanza packet) {
        // we don't need this listener anymore
        getConnection().removeAsyncStanzaListener(this);

        UploadInfo info = (UploadInfo) packet;
        String node = info.getNode();
        setUploadService(node, info.getUri());
        Log.v(MessageCenterService.TAG, "upload info received, node = " +
            node + ", uri = " + info.getUri());

        // resend pending messages
        resendPendingMessages(true, false);
    }
}


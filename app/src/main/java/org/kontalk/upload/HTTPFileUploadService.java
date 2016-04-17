/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.upload;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Stanza;

import org.kontalk.client.HTTPFileUpload;
import org.kontalk.service.msgcenter.IUploadService;


/**
 * Implements XEP-0363: HTTP File Upload.
 * @author Daniele Ricci
 */
public class HTTPFileUploadService implements IUploadService {

    private final XMPPConnection mConnection;
    private final String mService;

    public HTTPFileUploadService(XMPPConnection connection, String service) {
        mConnection = connection;
        mService = service;
    }

    @Override
    public boolean requiresCertificate() {
        return false;
    }

    @Override
    public void getPostUrl(String filename, long size, String mime, final UrlCallback callback) {
        HTTPFileUpload.Request request = new HTTPFileUpload.Request(filename, size, mime);
        request.setTo(mService);
        try {
            mConnection.sendIqWithResponseCallback(request, new StanzaListener() {
                @Override
                public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
                    if (packet instanceof HTTPFileUpload.Slot) {
                        HTTPFileUpload.Slot slot = (HTTPFileUpload.Slot) packet;
                        callback.callback(slot.getPutUrl(), slot.getGetUrl());
                    }
                }
            });
        }
        catch (SmackException.NotConnectedException e) {
            // ignored
        }
    }

}

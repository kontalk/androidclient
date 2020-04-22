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

package org.kontalk.upload;

import java.lang.ref.WeakReference;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.util.SuccessCallback;
import org.jxmpp.jid.BareJid;

import org.kontalk.client.HTTPFileUpload;
import org.kontalk.service.msgcenter.IUploadService;


/**
 * Implements XEP-0363: HTTP File Upload.
 * @author Daniele Ricci
 */
public class HTTPFileUploadService implements IUploadService {

    private final WeakReference<XMPPConnection> mConnection;
    private final BareJid mService;

    public HTTPFileUploadService(XMPPConnection connection, BareJid service) {
        mConnection = new WeakReference<>(connection);
        mService = service;
    }

    protected final XMPPConnection connection() {
        return mConnection.get();
    }

    @Override
    public boolean requiresCertificate() {
        return false;
    }

    @Override
    public void getPostUrl(String filename, long size, String mime, final UrlCallback callback) {
        HTTPFileUpload.Request request = new HTTPFileUpload.Request(filename, size, mime);
        request.setTo(mService);
        connection().sendIqRequestAsync(request)
            .onSuccess(new SuccessCallback<IQ>() {
                @Override
                public void onSuccess(IQ result) {
                    if (result instanceof HTTPFileUpload.Slot) {
                        HTTPFileUpload.Slot slot = (HTTPFileUpload.Slot) result;
                        callback.callback(slot.getPutUrl(), slot.getGetUrl());
                    }
                }
            });
    }

}

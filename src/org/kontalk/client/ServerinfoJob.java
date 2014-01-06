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

import java.io.IOException;

import org.kontalk.Kontalk;
import org.kontalk.client.Protocol.ServerInfoRequest;
import org.kontalk.service.ClientThread;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;

import android.content.Context;


/**
 * A {@link RequestJob} for requesting information about the server.
 * @author Daniele Ricci
 */
public class ServerinfoJob extends RequestJob {

    @Override
    public String execute(ClientThread client, RequestListener listener,
            Context context) throws IOException {
        ServerInfoRequest.Builder b = ServerInfoRequest.newBuilder();
        b.setClientProtocol(Kontalk.CLIENT_PROTOCOL);
        // TODO set other version fields :)
        return client.getConnection().send(b.build());
    }

}

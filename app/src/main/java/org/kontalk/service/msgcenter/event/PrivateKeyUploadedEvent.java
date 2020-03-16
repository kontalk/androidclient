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

package org.kontalk.service.msgcenter.event;

import org.jivesoftware.smack.packet.StanzaError;


/**
 * Private key uploaded event, in reply to {@link UploadPrivateKeyRequest}
 * @author Daniele Ricci
 */
public class PrivateKeyUploadedEvent {

    public final String token;
    public final String server;
    public final StanzaError.Condition error;

    public PrivateKeyUploadedEvent(String token, String server) {
        this.token = token;
        this.server = server;
        this.error = null;
    }

    public PrivateKeyUploadedEvent(StanzaError.Condition error) {
        this.error = error;
        this.token = null;
        this.server = null;
    }

}

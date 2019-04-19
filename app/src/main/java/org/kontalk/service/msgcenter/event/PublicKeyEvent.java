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

package org.kontalk.service.msgcenter.event;

import org.jxmpp.jid.Jid;


/**
 * Public key event, in reply to {@link PublicKeyRequest}
 * @author Daniele Ricci
 */
public class PublicKeyEvent extends ResponseEvent {

    public final Jid jid;
    public final byte[] publicKey;

    public PublicKeyEvent(Jid jid, byte[] publicKey, String id) {
        super(id);
        this.jid = jid;
        this.publicKey = publicKey;
    }

    public PublicKeyEvent(Exception error, Jid jid, String id) {
        super(id, error);
        this.jid = jid;
        this.publicKey = null;
    }

}

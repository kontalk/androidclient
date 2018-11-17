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

import org.jivesoftware.smack.packet.id.StanzaIdUtil;
import org.jxmpp.jid.Jid;


/**
 * Public key request event.
 * @author Daniele Ricci
 */
public class PublicKeyRequest extends RequestEvent {

    public final Jid jid;

    public PublicKeyRequest(Jid jid) {
        this(StanzaIdUtil.newStanzaId(), jid);
    }

    /** Use null jid to request public keys for the whole roster. */
    public PublicKeyRequest(String id, Jid jid) {
        super(id);
        this.jid = jid;
    }

}

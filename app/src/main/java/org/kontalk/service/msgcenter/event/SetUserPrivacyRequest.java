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

import org.jxmpp.jid.BareJid;

import org.kontalk.service.msgcenter.PrivacyCommand;


/**
 * Set user privacy request event.
 * Used to accept a presence subscription as well as block/unblock someone.
 * @author Daniele Ricci
 */
public class SetUserPrivacyRequest {

    public final BareJid jid;
    public final PrivacyCommand command;

    public SetUserPrivacyRequest(BareJid jid, PrivacyCommand command) {
        this.jid = jid;
        this.command = command;
    }

}

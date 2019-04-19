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
 * Blocklist event, in reply to {@link BlocklistRequest}
 * @author Daniele Ricci
 */
public class BlocklistEvent extends ResponseEvent {

    public final Jid[] jids;

    public BlocklistEvent(Jid[] jids, String id) {
        super(id);
        this.jids = jids;
    }

    public BlocklistEvent(Exception error, String id) {
        super(id, error);
        this.jids = null;
    }

}

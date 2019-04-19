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
 * Roster status event, in reply to {@link RosterStatusRequest}
 * @author Daniele Ricci
 */
public class RosterStatusEvent extends ResponseEvent {

    public final Jid jid;

    public final String rosterName;

    public final boolean subscribedFrom;
    public final boolean subscribedTo;

    public RosterStatusEvent(Jid jid, String rosterName,
            boolean subscribedFrom, boolean subscribedTo,
            String id) {
        super(id);
        this.jid = jid;
        this.rosterName = rosterName;
        this.subscribedFrom = subscribedFrom;
        this.subscribedTo = subscribedTo;
    }

}

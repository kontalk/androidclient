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

import java.util.Date;

import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.jid.Jid;


/**
 * Presence event.
 * @author Daniele Ricci
 */
public class PresenceEvent extends ReplyEvent {

    public final Jid jid;

    public final Presence.Type type;
    public final Presence.Mode mode;
    public final int priority;
    public final String status;

    public final Date delay;

    public final String rosterName;

    public final boolean subscribedFrom;
    public final boolean subscribedTo;

    public final String fingerprint;

    public PresenceEvent(Jid jid, Presence.Type type, Presence.Mode mode, int priority,
            String status, Date delay, String rosterName,
            boolean subscribedFrom, boolean subscribedTo,
            String fingerprint, String id) {
        super(id);
        this.jid = jid;
        this.type = type;
        this.mode = mode;
        this.priority = priority;
        this.status = status;
        this.delay = delay;
        this.rosterName = rosterName;
        this.subscribedFrom = subscribedFrom;
        this.subscribedTo = subscribedTo;
        this.fingerprint = fingerprint;
    }

}

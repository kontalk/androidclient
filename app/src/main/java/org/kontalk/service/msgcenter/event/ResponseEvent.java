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


import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.StanzaError;

/**
 * Base event for responses.
 * Includes an ID to keep track of request-response pairs.
 * @author Daniele Ricci
 */
public abstract class ResponseEvent {

    public final String id;
    public final Exception error;

    protected ResponseEvent(String id) {
        this(id, null);
    }

    protected ResponseEvent(String id, Exception error) {
        this.id = id;
        this.error = error;
    }

    public StanzaError.Condition getStanzaErrorCondition() {
        if (error instanceof XMPPException.XMPPErrorException &&
            ((XMPPException.XMPPErrorException) error).getStanzaError() != null) {
            return ((XMPPException.XMPPErrorException) error).getStanzaError()
                .getCondition();
        }
        return null;
    }

}

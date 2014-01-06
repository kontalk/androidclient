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

import com.google.protobuf.MessageLite;


/**
 * Listener interface for client transactions.
 * @author Daniele Ricci
 */
public interface TxListener {

    /**
     * Called when a pack has been received from the server.
     * @return true to prevent the listener from being called the next time.
     */
    public boolean tx(ClientConnection connection, String txId, MessageLite pack);

}

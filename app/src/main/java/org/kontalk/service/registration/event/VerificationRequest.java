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

package org.kontalk.service.registration.event;


import org.kontalk.client.EndpointServer;

/**
 * Phone number verification request event.
 * @author Daniele Ricci
 */
public class VerificationRequest {

    public final String phoneNumber;
    public final String displayName;
    public final EndpointServer.EndpointServerProvider serverProvider;

    public VerificationRequest(String phoneNumber, String displayName,
            EndpointServer.EndpointServerProvider serverProvider) {
        this.phoneNumber = phoneNumber;
        this.displayName = displayName;
        this.serverProvider = serverProvider;
    }

}

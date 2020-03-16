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

package org.kontalk.service.registration.event;

import java.io.InputStream;

import androidx.annotation.Nullable;

import org.kontalk.client.EndpointServer;
import org.kontalk.service.registration.RegistrationService;


/**
 * Post this to request to the registration service to import a keypack from
 * a zip file.
 * @author Daniele Ricci
 */
public class ImportKeyRequest {

    /** Will be null if we are to auto-detect it from the key. */
    public final EndpointServer server;

    /** An input stream from the personal key pack file. */
    public final InputStream in;

    /** The passphrase protecting the personal key. */
    public final String passphrase;

    /** If the server rejects our key, set this to true to proceed to verification. */
    public final boolean fallbackVerification;

    /** Will be needed if we'll fallback to normal verification. */
    @RegistrationService.BrandImageSize
    public final int brandImageSize;

    public ImportKeyRequest(@Nullable EndpointServer server, InputStream in, String passphrase,
            boolean fallbackVerification, @RegistrationService.BrandImageSize int brandImageSize) {
        this.server = server;
        this.in = in;
        this.passphrase = passphrase;
        this.fallbackVerification = fallbackVerification;
        this.brandImageSize = brandImageSize;
    }

}

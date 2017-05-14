/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.message;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Generic component used for unknown MIME types.
 * @author Daniele Ricci
 */

public class DefaultAttachmentComponent extends AttachmentComponent {

    public DefaultAttachmentComponent(String mime, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
        super(mime, null, localUri, fetchUrl, length, encrypted, securityFlags);
    }

    @Override
    protected void populateFromCursor(Context context, Cursor c) {
        // Nothing to do here
    }

}

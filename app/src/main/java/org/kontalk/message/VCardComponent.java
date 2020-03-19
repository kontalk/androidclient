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

package org.kontalk.message;

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;


/**
 * vCard component.
 * @author Daniele Ricci
 */
public class VCardComponent extends AttachmentComponent {

    // actually the second one is not standard...
    public static final String[] MIME_TYPES = { "text/x-vcard", "text/vcard" };
    public static final String MIME_TYPE = MIME_TYPES[0];

    public VCardComponent(File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
        super(MIME_TYPES[0], previewFile, localUri, fetchUrl, length, encrypted, securityFlags);
    }

    @Override
    protected void populateFromCursor(Context context, Cursor cursor) {
        // TODO
    }

    public static boolean supportsMimeType(String mime) {
        for (String mimeType : MIME_TYPES) {
            if (mimeType.equalsIgnoreCase(mime))
                return true;
        }

        return false;
    }

    public static String buildMediaFilename(String id, String mime) {
        return "vcard" + id.substring(id.length() - 5) + ".vcf";
    }

}

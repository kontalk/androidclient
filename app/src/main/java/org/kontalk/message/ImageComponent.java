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

package org.kontalk.message;

import java.io.File;

import org.jivesoftware.smack.util.StringUtils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.kontalk.util.MediaStorage;


/**
 * Image component.
 * @author Daniele Ricci
 */
public class ImageComponent extends AttachmentComponent {

    private static final String[][] MIME_TYPES = {
        { "image/png", "png" },
        { "image/jpeg", "jpg" },
        { "image/gif", "gif" },
        // non-standard
        { "image/jpg", "jpg" }
    };

    public ImageComponent(String mime, File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
        super(mime, previewFile, localUri, fetchUrl, length, encrypted, securityFlags);
    }

    public static boolean supportsMimeType(String mime) {
        for (String[] MIME_TYPE : MIME_TYPES)
            if (MIME_TYPE[0].equalsIgnoreCase(mime))
                return true;

        return false;
    }

    /** FIXME not used yet */
    public boolean isValidMedia(Context context) {
        Uri localUri = mContent.getLocalUri();
        if (localUri != null) {
            try {
                return (MediaStorage.getLength(context, localUri) == mLength);
            }
            catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    @Override
    protected void populateFromCursor(Context context, Cursor c) {
    }

    public static String buildMediaFilename(String mime) {
        return "image" + StringUtils.randomString(5) + "." + getFileExtension(mime);
    }

    /** Returns the file extension from the mime type. */
    public static String getFileExtension(String mime) {
        for (String[] MIME_TYPE : MIME_TYPES)
            if (MIME_TYPE[0].equalsIgnoreCase(mime))
                return MIME_TYPE[1];

        return null;
    }

}

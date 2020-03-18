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

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import org.kontalk.util.MediaStorage;

/**
 * Audio component.
 * @author Andrea Cappelli
 */

public class AudioComponent extends AttachmentComponent {

    static Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("audio/3gpp", "3gp");
        MIME_TYPES.put("audio/mpeg", "mp3");
        MIME_TYPES.put("audio/mp4", "mp4");
        MIME_TYPES.put("audio/wav", "wav");
        MIME_TYPES.put("audio/aac", "aac");
        MIME_TYPES.put("audio/flac", "flac");
    }

    public AudioComponent(String mime, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
        super(mime, null, localUri, fetchUrl, length, encrypted, securityFlags);
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPES.containsKey(mime);
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
        // Nothing to do here
    }

    public static String buildMediaFilename(String id, String mime) {
        return "audio" + id.substring(id.length() - 5) + "." + getFileExtension(mime);
    }

    /** Returns the file extension from the mime type. */
    public static String getFileExtension(String mime) {
        return MIME_TYPES.get(mime);
    }
}

package org.kontalk.message;

import java.io.File;

import org.kontalk.util.MediaStorage;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Audio component.
 * @author Andrea Cappelli
 */

public class AudioComponent extends AttachmentComponent {

    private static final String[][] MIME_TYPES = {
        { "audio/3gpp", "3gp" },
        { "audio/mpeg", "mp3" },
        { "audio/mp4", "mp4" },
        { "audio/wav", "wav" },
        { "audio/aac", "aac" },
        { "audio/flac", "flac" },

    };

    public AudioComponent(String mime, File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
        super(mime, previewFile, localUri, fetchUrl, length, encrypted, securityFlags);
    }

    public static boolean supportsMimeType(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
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
        //TODO
    }

    public static String buildMediaFilename(String id, String mime) {
        return "audio" + id.substring(id.length() - 5) + "." + getFileExtension(mime);
    }

    /** Returns the file extension from the mime type. */
    private static String getFileExtension(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
                return MIME_TYPES[i][1];

        return null;
    }

}

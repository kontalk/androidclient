package org.kontalk.message;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.kontalk.util.MediaStorage;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Audio component.
 * @author Andrea Cappelli
 */

public class AudioComponent extends AttachmentComponent {

    static Map<String, String> MIME_TYPES = new HashMap<String, String>();
    static {
        MIME_TYPES.put("audio/3gpp", "3gp");
        MIME_TYPES.put("audio/mpeg", "mp3");
        MIME_TYPES.put("audio/mp4", "mp4");
        MIME_TYPES.put("audio/wav", "wav");
        MIME_TYPES.put("audio/aac", "aac");
        MIME_TYPES.put("audio/flac", "flac");
    }

    public AudioComponent(String mime, File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
        super(mime, previewFile, localUri, fetchUrl, length, encrypted, securityFlags);
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
        //TODO
    }

    public static String buildMediaFilename(String id, String mime) {
        return "audio" + id.substring(id.length() - 5) + "." + getFileExtension(mime);
    }

    /** Returns the file extension from the mime type. */
    public static String getFileExtension(String mime) {
        return MIME_TYPES.get(mime);
    }
}

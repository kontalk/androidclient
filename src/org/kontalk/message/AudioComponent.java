package org.kontalk.message;

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Audio component.
 * @author Andrea Cappelli
 */

public class AudioComponent extends AttachmentComponent {

    private static final String[][] MIME_TYPES = {
        { "audio/3gp", "3gp" },
        { "audio/mp3", "mp3" },
        { "audio/mp4", "mp4" },
        { "audio/wav", "wav" },
        { "audio/aac", "aac" },
        { "audio/flac", "flac" },

    };

    public AudioComponent(String mime, File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
        super(mime, previewFile, localUri, fetchUrl, length, encrypted, securityFlags);
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void populateFromCursor(Context context, Cursor cursor) {
        // TODO Auto-generated method stub

    }

}

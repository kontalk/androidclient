package org.kontalk.xmpp.message;

import java.io.File;

import android.net.Uri;


/**
 * Image component.
 * @author Daniele Ricci
 */
public class ImageComponent extends MessageComponent<Attachment> {

    private static final String[][] MIME_TYPES = {
        { "image/png", "png" },
        { "image/jpeg", "jpg" },
        { "image/gif", "gif" },
        // non-standard
        { "image/jpg", "jpg" }
    };

	public ImageComponent(File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
		super(new Attachment(previewFile, localUri, fetchUrl), length, encrypted, securityFlags);
	}

    public static boolean supportsMimeType(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
                return true;

        return false;
    }

}

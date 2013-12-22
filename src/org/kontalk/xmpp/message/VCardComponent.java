package org.kontalk.xmpp.message;

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
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i].equalsIgnoreCase(mime))
                return true;

        return false;
    }

    public static String buildMediaFilename(String id, String mime) {
        return "vcard" + id.substring(id.length() - 5) + ".vcf";
    }

}

package org.kontalk.xmpp.message;

import java.io.File;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;


/**
 * Attachment component.
 * @author Daniele Ricci
 */
public abstract class AttachmentComponent extends MessageComponent<Attachment> {

	public AttachmentComponent(String mime, File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
		super(new Attachment(mime, previewFile, localUri, fetchUrl), length, encrypted, securityFlags);
	}

    public String getMime() {
    	return mContent.getMime();
    }

    public File getPreviewFile() {
    	return mContent.getPreviewFile();
    }

    public Uri getLocalUri() {
    	return mContent.getLocalUri();
    }

    public String getFetchUrl() {
    	return mContent.getFetchUrl();
    }

    protected abstract void populateFromCursor(Context context, Cursor cursor);

}

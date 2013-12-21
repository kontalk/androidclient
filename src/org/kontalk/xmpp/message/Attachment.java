package org.kontalk.xmpp.message;

import java.io.File;

import android.net.Uri;


/**
 * Attachment metadata.
 * @author Daniele Ricci
 */
public class Attachment {

	private File mPreviewFile;
	private Uri mLocalUri;
	private String mFetchUrl;

	public Attachment(File previewFile, Uri localUri, String fetchUrl) {
		mPreviewFile = previewFile;
		mLocalUri = localUri;
		mFetchUrl = fetchUrl;
	}

	public Attachment(String previewFile, String localUri, String fetchUrl) {
		this(new File(previewFile), Uri.parse(localUri), fetchUrl);
	}

	public File getPreviewFile() {
		return mPreviewFile;
	}

	public Uri getLocalUri() {
		return mLocalUri;
	}

	public String getFetchUrl() {
		return mFetchUrl;
	}

}

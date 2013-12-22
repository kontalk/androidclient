package org.kontalk.xmpp.message;

import java.io.File;

import android.net.Uri;


/**
 * Attachment metadata.
 * @author Daniele Ricci
 */
public class Attachment {

	private String mMime;
	private File mPreviewFile;
	private Uri mLocalUri;
	private String mFetchUrl;

	public Attachment(String mime, File previewFile, Uri localUri, String fetchUrl) {
		mMime = mime;
		mPreviewFile = previewFile;
		mLocalUri = localUri;
		mFetchUrl = fetchUrl;
	}

	public Attachment(String mime, String previewFile, String localUri, String fetchUrl) {
		this(mime, new File(previewFile), Uri.parse(localUri), fetchUrl);
	}

	public String getMime() {
		return mMime;
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

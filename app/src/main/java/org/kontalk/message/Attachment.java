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

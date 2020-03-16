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

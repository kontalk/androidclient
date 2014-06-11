/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.kontalk.Kontalk;
import org.kontalk.util.MediaStorage;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;


/**
 * Image component.
 * @author Daniele Ricci
 */
public class ImageComponent extends AttachmentComponent {

    private static final String[][] MIME_TYPES = {
        { "image/png", "png" },
        { "image/jpeg", "jpg" },
        { "image/gif", "gif" },
        // non-standard
        { "image/jpg", "jpg" }
    };

    private Bitmap mBitmap;

	public ImageComponent(String mime, File previewFile, Uri localUri, String fetchUrl, long length, boolean encrypted, int securityFlags) {
		super(mime, previewFile, localUri, fetchUrl, length, encrypted, securityFlags);
	}

    public static boolean supportsMimeType(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
                return true;

        return false;
    }

    private BitmapFactory.Options bitmapOptions() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    private void loadPreview(File previewFile) throws IOException {
        InputStream in = new FileInputStream(previewFile);
        BitmapFactory.Options options = bitmapOptions();
        mBitmap = BitmapFactory.decodeStream(in, null, options);
        in.close();
    }

    public Bitmap getBitmap() {
		return mBitmap;
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

        /*
         * local_uri is used for referencing the original media.
         * preview_uri is used to load the media thumbnail.
         * If preview_uri is null or cannot be found, a thumbnail is
         * generated on the fly from local_uri - if possible.
         */

    	File previewFile = mContent.getPreviewFile();
    	Uri localUri = mContent.getLocalUri();
        try {
            // preview path
            if (previewFile != null) {
                // load from file - we know it's a file uri
                loadPreview(previewFile);
            }
        }
        catch (Exception e) {
            Log.w(Kontalk.TAG, "unable to load thumbnail, generating one");

            try {
                /*
                 * unable to load preview - generate thumbnail
                 * Of course a thumbnail can be generated only if the image has
                 * already been downloaded.
                 */
                if (previewFile != null && localUri != null) {
                    MediaStorage.cacheThumbnail(context, localUri, previewFile);
                    loadPreview(previewFile);
                }
            }
            catch (Exception e1) {
                Log.e(Kontalk.TAG, "unable to generate thumbnail", e1);
            }
        }
    }

    public static String buildMediaFilename(String id, String mime) {
        return "image" + id.substring(id.length() - 5) + "." + getFileExtension(mime);
    }

    /** Returns the file extension from the mime type. */
    private static String getFileExtension(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
                return MIME_TYPES[i][1];

        return null;
    }

}

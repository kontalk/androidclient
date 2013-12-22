/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmpp.message;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.crypto.Coder;
import org.kontalk.xmpp.util.MediaStorage;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;


/**
 * A generic image message.
 * @author Daniele Ricci
 * @version 1.0
 */
public class LegacyImageMessage extends LegacyAbstractMessage<Bitmap> {
    private static final String TAG = LegacyImageMessage.class.getSimpleName();

    private static final String[][] MIME_TYPES = {
        { "image/png", "png" },
        { "image/jpeg", "jpg" },
        { "image/gif", "gif" },
        // non-standard
        { "image/jpg", "jpg" }
    };

    /** Used only for transporting thumbnail data from polling to storage. */
    private byte[] decodedContent;

    protected LegacyImageMessage(Context context) {
        super(context, null, 0, null, false, null);
    }

    public LegacyImageMessage(Context context, String mime, String id, long timestamp, String sender, byte[] content, boolean encrypted) {
        this(context, mime, id, timestamp, sender, null, encrypted, null);
    }

    public LegacyImageMessage(Context context, String mime, String id, long timestamp, String sender, byte[] content, boolean encrypted, List<String> group) {
        super(context, id, timestamp, sender, encrypted, group);
        decodedContent = content;
    }

    private BitmapFactory.Options bitmapOptions() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    private void loadPreview(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        BitmapFactory.Options options = bitmapOptions();
        //content = BitmapFactory.decodeStream(in, null, options);
        in.close();
    }

    public static boolean supportsMimeType(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
                return true;

        return false;
    }

    @Override
    public String getTextContent() {
        String s = "Image: " + "ciao";
        if (encrypted)
            s += " " + mContext.getResources().getString(R.string.text_encrypted);
        return s;
    }

    @Override
    public byte[] getBinaryContent() {
        return decodedContent;
    }

    @Override
    public void decrypt(Coder coder) throws GeneralSecurityException {
        // TODO
    }

    /** FIXME not used yet */
    public boolean isValidMedia() {
        if (localUri != null) {
            try {
                Log.d(TAG, "file size is " + MediaStorage.getLength(mContext, localUri));
                return (MediaStorage.getLength(mContext, localUri) == this.length);
            }
            catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);

        /*
         * local_uri is used for referencing the original media.
         * preview_uri is used to load the media thumbnail.
         * If preview_uri is null or cannot be found, a thumbnail is
         * generated on the fly from local_uri - if possible.
         */

        String _previewPath = null; //c.getString(COLUMN_PREVIEW_PATH);
        try {
            // preview path
            if (_previewPath != null && _previewPath.length() > 0) {
                // load from file - we know it's a file uri
                previewFile = new File(_previewPath);
                loadPreview(previewFile);
            }
        }
        catch (Exception e) {
            Log.w(TAG, "unable to load thumbnail, generating one");

            try {
                /*
                 * unable to load preview - generate thumbnail
                 * Of course a thumbnail can be generated only if the image has
                 * already been downloaded.
                 */
                if (previewFile != null && localUri != null) {
                    MediaStorage.cacheThumbnail(mContext, localUri, previewFile);
                    loadPreview(previewFile);
                }
            }
            catch (Exception e1) {
                Log.e(TAG, "unable to generate thumbnail", e1);
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

    @Override
    public void recycle() {
        // TODO
    }

}

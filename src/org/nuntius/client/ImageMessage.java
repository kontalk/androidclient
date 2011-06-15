package org.nuntius.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.nuntius.data.MediaStorage;
import org.nuntius.provider.MyMessages.Messages;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.util.Base64;
import android.util.Log;


/**
 * A generic image message (base64-encoded).
 * @author Daniele Ricci
 * @version 1.0
 */
public class ImageMessage extends AbstractMessage<Bitmap> {
    private static final String[][] MIME_TYPES = {
        { "image/png", "png" },
        { "image/jpeg", "jpg" },
        { "image/gif", "gif" }
    };

    private static final String TAG = ImageMessage.class.getSimpleName();
    private static final int THUMBNAIL_WIDTH = 80;
    private static final int THUMBNAIL_HEIGHT = 80;

    private byte[] mData;
    private String mediaFilename;

    protected ImageMessage() {
        super(null, null, null, null);
    }

    public ImageMessage(String mime, String id, String sender, String content) {
        this(mime, id, sender, null, null);
    }

    public ImageMessage(String mime, String id, String sender, String content, List<String> group) {
        super(id, sender, mime, null, group);

        // prepare file name
        mediaFilename = buildMediaFilename(id, mime);
        // process content
        decodeBitmap(content);
    }

    public void decodeBitmap(String encodedContent) {
        mData = Base64.decode(encodedContent, Base64.DEFAULT);
        createBitmap();
    }

    public void decodeBitmap(byte[] data) {
        mData = data;
        createBitmap();
    }

    /** Creates a thumbnail from the bitmap data. */
    private void createBitmap() {
        Bitmap original = BitmapFactory.decodeByteArray(mData, 0, mData.length);
        content = ThumbnailUtils
            .extractThumbnail(original, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
    }

    public static boolean supportsMimeType(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
                return true;

        return false;
    }

    public String getMediaFilename() {
        return mediaFilename;
    }

    @Override
    public String getTextContent() {
        return "[IMAGE]";
    }

    public byte[] getBinaryContent() {
        return mData;
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        String mediaFile = c.getString(c.getColumnIndex(Messages.CONTENT));
        try {
            InputStream fin = MediaStorage.readMedia(mediaFile);
            byte[] buf = new byte[2048];
            ByteArrayOutputStream bio = new ByteArrayOutputStream();
            while (fin.read(buf) >= 0)
                bio.write(buf);
            fin.close();

            decodeBitmap(bio.toByteArray());
            bio.close();

            if (mediaFile.startsWith(MediaStorage.URI_SCHEME))
                mediaFilename = mediaFile.substring(MediaStorage.URI_SCHEME.length());
            else
                mediaFilename = mediaFile;
        }
        catch (IOException e) {
            Log.e(TAG, "unable to load image from cursor");
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

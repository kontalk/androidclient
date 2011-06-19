package org.nuntius.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.nuntius.data.MediaStorage;
import org.nuntius.provider.MyMessages.Messages;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.util.Log;


/**
 * A generic image message (base64-encoded).
 * @author Daniele Ricci
 * @version 1.0
 */
public class ImageMessage extends AbstractMessage<Bitmap> {
    private static final String TAG = ImageMessage.class.getSimpleName();

    private static final String[][] MIME_TYPES = {
        { "image/png", "png" },
        { "image/jpeg", "jpg" },
        { "image/gif", "gif" }
    };

    private static final int THUMBNAIL_WIDTH = 80;
    private static final int THUMBNAIL_HEIGHT = 80;

    private byte[] decodedContent;
    private String mediaFilename;

    protected ImageMessage() {
        super(null, null, null, null);
    }

    public ImageMessage(String mime, String id, String sender, byte[] content) {
        this(mime, id, sender, null, null);
    }

    public ImageMessage(String mime, String id, String sender, byte[] content, List<String> group) {
        super(id, sender, mime, null, group);

        // prepare file name
        mediaFilename = buildMediaFilename(id, mime);
        // process content
        try {
            decodedContent = content;
            createThumbnail(decodedContent);
        }
        catch (Exception e) {
            Log.e(TAG, "error decoding image data", e);
        }
    }

    private BitmapFactory.Options processOptions(BitmapFactory.Options options) {
        int w = options.outWidth;
        int h = options.outHeight;
        // error :(
        if (w < 0 || h < 0) return null;

        if (w > THUMBNAIL_WIDTH)
            options.inSampleSize = (w / THUMBNAIL_WIDTH);
        else if (h > THUMBNAIL_HEIGHT)
            options.inSampleSize = (h / THUMBNAIL_HEIGHT);

        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        return options;
    }

    /** Generates {@link BitmapFactory.Options} for the given image data. */
    private BitmapFactory.Options preloadBitmap(byte[] data) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        return processOptions(options);
    }

    /** Generates {@link BitmapFactory.Options} for the given {@link InputStream}. */
    private BitmapFactory.Options preloadBitmap(InputStream in) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(in, null, options);

        return processOptions(options);
    }

    /** Creates a thumbnail from the bitmap data. */
    private void createThumbnail(byte[] data) {
        BitmapFactory.Options options = preloadBitmap(data);
        createThumbnail(BitmapFactory.decodeByteArray(data, 0, data.length, options));
    }

    private void createThumbnail(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        BitmapFactory.Options options = preloadBitmap(in);
        in.close();

        // open again
        in = new FileInputStream(file);
        createThumbnail(BitmapFactory.decodeStream(in, null, options));
        in.close();
    }

    private void createThumbnail(Bitmap bitmap) {
        content = ThumbnailUtils
            .extractThumbnail(bitmap, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
    }

    public static boolean supportsMimeType(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i][0].equalsIgnoreCase(mime))
                return true;

        return false;
    }

    /** If the image has not been loaded, this returns null. */
    public String getMediaFilename() {
        return mediaFilename;
    }

    @Override
    public String getTextContent() {
        return "[IMAGE]";
    }

    public byte[] getDecodedContent() {
        return decodedContent;
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        String mediaFile = c.getString(c.getColumnIndex(Messages.CONTENT));
        try {
            createThumbnail(MediaStorage.getMediaFile(mediaFile));

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

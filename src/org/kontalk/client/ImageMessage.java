package org.kontalk.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.List;

import org.kontalk.crypto.Coder;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.util.ThumbnailUtils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;


/**
 * A generic image message.
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

    protected ImageMessage(Context context) {
        super(context, null, null, null, null, false);
    }

    public ImageMessage(Context context, String mime, String id, String sender, byte[] content, boolean encrypted) {
        this(context, mime, id, sender, null, encrypted, null);
    }

    public ImageMessage(Context context, String mime, String id, String sender, byte[] content, boolean encrypted, List<String> group) {
        super(context, id, sender, mime, null, encrypted, group);

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

    /** Creates a thumbnail from a {@link File}. */
    private void createThumbnail(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        BitmapFactory.Options options = preloadBitmap(in);
        in.close();

        // open again
        in = new FileInputStream(file);
        createThumbnail(BitmapFactory.decodeStream(in, null, options));
        in.close();
    }

    /** Creates a thumbnail from a {@link Uri}. */
    private void createThumbnail(Context context, Uri uri) throws IOException {
        ContentResolver cr = context.getContentResolver();
        InputStream in = cr.openInputStream(uri);
        BitmapFactory.Options options = preloadBitmap(in);
        in.close();

        // open again
        in = cr.openInputStream(uri);
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

    @Override
    public String getTextContent() {
        return "Image: " + mime;
    }

    @Override
    public byte[] getBinaryContent() {
        return decodedContent;
    }

    /** FIXME this should have be done better... */
    public static String getSampleTextContent(String mime) {
        return "Image: " + mime;
    }

    @Override
    public void decrypt(Coder coder) throws GeneralSecurityException {
        // TODO
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        String mediaUri = c.getString(c.getColumnIndex(Messages.LOCAL_URI));
        try {
            Uri u = Uri.parse(mediaUri);

            // load from file
            if ("file".equals(u.getScheme())) {
                createThumbnail(new File(u.getPath()));
            }
            // load from media uri
            else {
                createThumbnail(mContext, u);
            }

            localUri = u;
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

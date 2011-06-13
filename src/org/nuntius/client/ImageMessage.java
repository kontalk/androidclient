package org.nuntius.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.nuntius.data.MediaStorage;
import org.nuntius.provider.MyMessages.Messages;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;


/**
 * A generic image message (base64-encoded).
 * @author Daniele Ricci
 * @version 1.0
 */
public class ImageMessage extends AbstractMessage<Bitmap> {
    private static final String[] MIME_TYPES = {
        "image/png",
        "image/jpeg",
        "image/gif"
    };

    private static final String TAG = ImageMessage.class.getSimpleName();

    private String encodedContent;

    protected ImageMessage() {
        super(null, null, null, null);
    }

    public ImageMessage(String id, String sender, String content) {
        this(id, sender, null, null);
    }

    public ImageMessage(String id, String sender, String content, List<String> group) {
        super(id, sender, null, null, group);

        encodedContent = content;
        // FIXME should be passed from the outside
        mime = "image/png";

        // process content
        decodeBitmap();
    }

    public void decodeBitmap() {
        byte[] data = Base64.decode(encodedContent, Base64.DEFAULT);
        content = BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    public static boolean supportsMimeType(String mime) {
        for (int i = 0; i < MIME_TYPES.length; i++)
            if (MIME_TYPES[i].equalsIgnoreCase(mime))
                return true;

        return false;
    }

    @Override
    public String getTextContent() {
        return encodedContent;
    }

    @Override
    public int getMediaType() {
        return MEDIA_TYPE_IMAGE;
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        String mediaFile = c.getString(c.getColumnIndex(Messages.CONTENT));
        // FIXME should we usi some uri or media storage provider?
        try {
            if (mediaFile.startsWith("media:")) {
                mediaFile = mediaFile.substring("media:".length());
                InputStream fin = MediaStorage.readMedia(mediaFile);
                BufferedReader buf = new BufferedReader(new InputStreamReader(fin));
                while (buf.ready()) {
                    encodedContent = buf.readLine();
                }

                decodeBitmap();
            }
        }
        catch (IOException e) {
            Log.e(TAG, "unable to load image from cursor");
        }
    }

}

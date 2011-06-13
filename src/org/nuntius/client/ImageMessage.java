package org.nuntius.client;

import java.util.List;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;


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

    private String encodedContent;

    public ImageMessage(String id, String sender, String content) {
        this(id, sender, null, null);
    }

    public ImageMessage(String id, String sender, String content, List<String> group) {
        super(id, sender, null, null, group);

        encodedContent = content;

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

}

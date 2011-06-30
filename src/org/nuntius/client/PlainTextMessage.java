package org.nuntius.client;

import java.util.List;

import org.nuntius.provider.MyMessages.Messages;

import android.content.Context;
import android.database.Cursor;


/**
 * A plain text message.
 * Mime type: text/plain
 * @author Daniele Ricci
 * @version 1.0
 */
public class PlainTextMessage extends AbstractMessage<String> {
    //private static final String TAG = PlainTextMessage.class.getSimpleName();

    public static final String MIME_TYPE = "text/plain";

    protected PlainTextMessage(Context context) {
        super(context, null, null, null, null);
    }

    public PlainTextMessage(Context context, String id, String sender, byte[] content) {
        this(context, id, sender, content, null);
    }

    public PlainTextMessage(Context context, String id, String sender, byte[] content, List<String> group) {
        super(context, id, sender, MIME_TYPE, new String(content), group);
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        content = c.getString(c.getColumnIndex(Messages.CONTENT));
    }

    @Override
    public String getTextContent() {
        return content;
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

}

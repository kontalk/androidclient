package org.kontalk.client;

import java.security.GeneralSecurityException;
import java.util.List;

import org.kontalk.crypto.Coder;
import org.kontalk.provider.MyMessages.Messages;

import com.google.protobuf.ByteString;

import android.content.Context;
import android.database.Cursor;


/**
 * A plain text message.
 * Mime type: text/plain
 * @author Daniele Ricci
 * @version 1.0
 */
public class PlainTextMessage extends AbstractMessage<ByteString> {
    //private static final String TAG = PlainTextMessage.class.getSimpleName();

    public static final String MIME_TYPE = "text/plain";

    protected PlainTextMessage(Context context) {
        super(context, null, null, null, null, false);
    }

    public PlainTextMessage(Context context, String id, String sender, byte[] content, boolean encrypted) {
        this(context, id, sender, content, encrypted, null);
    }

    public PlainTextMessage(Context context, String id, String sender, byte[] content, boolean encrypted, List<String> group) {
        super(context, id, sender, MIME_TYPE, ByteString.copyFrom(content), encrypted, group);
    }

    @Override
    protected void populateFromCursor(Cursor c) {
        super.populateFromCursor(c);
        content = ByteString.copyFrom(c.getBlob(c.getColumnIndex(Messages.CONTENT)));
    }

    @Override
    public String getTextContent() {
        if (encrypted)
            // TODO i18n
            return "(encrypted)";
        return content.toStringUtf8();
    }

    @Override
    public byte[] getBinaryContent() {
        return content.toByteArray();
    }

    @Override
    public void decrypt(Coder coder) throws GeneralSecurityException {
        if (isEncrypted()) {
            byte[] buf = coder.decrypt(content.toByteArray());
            content = ByteString.copyFrom(buf);
        }
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

}

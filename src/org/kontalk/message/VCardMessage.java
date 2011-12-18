package org.kontalk.message;

import java.util.List;

import android.content.Context;

public class VCardMessage extends PlainTextMessage {

    public static final String MIME_TYPE = "text/vcard";

    public VCardMessage(Context context) {
        super(context);
    }

    public VCardMessage(Context context, String id, String sender, byte[] content, boolean encrypted) {
        super(context, id, sender, content, encrypted);
    }

    public VCardMessage(Context context, String id, String sender, byte[] content, boolean encrypted, List<String> group) {
        super(context, id, sender, content, encrypted, group);
    }

    @Override
    public String getTextContent() {
        return MIME_TYPE;
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

}

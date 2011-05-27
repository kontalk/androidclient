package org.nuntius.android.client;

import java.util.List;

public class PlainTextMessage extends AbstractMessage {

    public static final String MIME_TYPE = "text/plain";

    public PlainTextMessage(String id, String sender, String content) {
        super(id, sender, MIME_TYPE, content);
    }

    public PlainTextMessage(String id, String sender, String content, List<String> group) {
        super(id, sender, MIME_TYPE, content, group);
    }

    @Override
    public Object getContent() {
        return content;
    }

}

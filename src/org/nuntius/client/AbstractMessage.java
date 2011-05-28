package org.nuntius.client;

import java.util.*;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;


/**
 * An abstract message.
 * TODO: should be a {@link Parcelable}
 * @author Daniele Ricci
 * @version 1.0
 */
public abstract class AbstractMessage<T> {

    public static final String MSG_ID = "org.nuntius.message.id";
    public static final String MSG_SENDER = "org.nuntius.message.sender";
    public static final String MSG_MIME = "org.nuntius.message.mime";
    public static final String MSG_CONTENT = "org.nuntius.message.content";
    public static final String MSG_RECIPIENTS = "org.nuntius.message.recipients";
    public static final String MSG_GROUP = "org.nuntius.message.group";

    protected boolean incoming;
    protected String id;
    protected String sender;
    protected String mime;
    protected T content;

    /**
     * Recipients (outgoing) - will contain one element for incoming
     */
    protected List<String> recipients;

    /**
     * Recipients (incoming) - will be null for outgoing
     */
    protected List<String> group;

    public AbstractMessage(String id, String sender, String mime, T content, List<String> group) {
        this(id, sender, mime, content);
        this.group = group;
    }

    public AbstractMessage(String id, String sender, String mime, T content) {
        this.id = id;
        this.sender = sender;
        this.mime = mime;
        this.content = content;
        this.recipients = new ArrayList<String>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public void addRecipient(String userId) {
        recipients.add(userId);
    }

    public List<String> getGroup() {
        return group;
    }

    public String getMime() {
        return mime;
    }

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    public Date getTimestamp() {
        // TODO retrieves timestamp from message id
        return new Date();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": id=" + id;
    }

    /**
     * Returns a rapid text representation of the message.
     * The returned value is useful for notification tickers.
     * @return the text that represent this message
     */
    public abstract String getTextContent();

    /**
     * Constructs a bundle from this message.
     * @return the newly created bundle
     */
    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putString(MSG_ID, id);
        b.putString(MSG_SENDER, sender);
        b.putString(MSG_MIME, mime);
        // content is type-dependant
        if (recipients != null)
            b.putStringArray(MSG_RECIPIENTS, recipients.toArray(new String[]{}));
        if (group != null)
            b.putStringArray(MSG_GROUP, group.toArray(new String[]{}));
        return b;
    }

    protected void populateFromBundle(Bundle b) {
        id = b.getString(MSG_ID);
        sender = b.getString(MSG_SENDER);
        mime = b.getString(MSG_MIME);
        String[] rcpts = b.getStringArray(MSG_RECIPIENTS);
        if (rcpts != null)
            recipients = Arrays.asList(rcpts);

        String[] grp = b.getStringArray(MSG_GROUP);
        if (grp != null)
            group = Arrays.asList(grp);
    }

    public static AbstractMessage<?> fromBundle(Bundle b) {
        Log.w("AbstractMessage/fromBundle", "mime=" + b.getString(MSG_MIME));
        if (PlainTextMessage.MIME_TYPE.equals(b.getString(MSG_MIME))) {
            Log.w("AbstractMessage/fromBundle", "content=" + b.getString(MSG_CONTENT));
            PlainTextMessage msg = new PlainTextMessage();
            msg.populateFromBundle(b);
            return msg;
        }

        return null;
    }
}

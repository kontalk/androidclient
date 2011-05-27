package org.nuntius.android.client;

import java.util.*;

public abstract class AbstractMessage {

    protected boolean incoming;
    protected String id;
    protected String sender;
    protected String mime;
    protected String content;

    /**
     * Recipients (outgoing) - will contain one element for incoming
     */
    protected List<String> recipients;

    /**
     * Recipients (incoming) - will be null for outgoing
     */
    protected List<String> group;

    public AbstractMessage(String id, String sender, String mime, String content, List<String> group) {
        this(id, sender, mime, content);
        this.group = group;
    }

    public AbstractMessage(String id, String sender, String mime, String content) {
        this.id = id;
        this.sender = sender;
        this.mime = mime;
        this.content = content;
        this.recipients = new ArrayList<String>();
        this.group = new ArrayList<String>();
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

    public abstract Object getContent();

    public String getRawContent() {
        return content;
    }

    public void setContent(String content) {
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
}

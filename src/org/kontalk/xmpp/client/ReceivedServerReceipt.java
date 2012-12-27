package org.kontalk.xmpp.client;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;
import org.jivesoftware.smack.util.StringUtils;


public class ReceivedServerReceipt extends ServerReceipt {
    public static final String ELEMENT_NAME = "received";

    private Date timestamp;

    public ReceivedServerReceipt(String id) {
        this(id, null);
    }

    public ReceivedServerReceipt(String id, Date timestamp) {
        super(ELEMENT_NAME, id);
        this.timestamp = timestamp;
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    public static final class Provider extends EmbeddedExtensionProvider {
        @Override
        protected PacketExtension createReturnExtension(String currentElement, String currentNamespace,
            Map<String, String> attributeMap, List<? extends PacketExtension> content) {
            ReceivedServerReceipt p = new ReceivedServerReceipt(attributeMap.get("id"));
            try {
                p.setTimestamp(StringUtils.parseXEP0082Date(attributeMap.get("stamp")));
            }
            catch (Exception e) {
                // ignored
            }

            return p;
        }
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Date getTimestamp() {
        return timestamp;
    }

}

package org.kontalk.xmpp.client;

import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;


public class ReceivedServerReceipt extends ServerReceipt {
    public static final String ELEMENT_NAME = "received";

    public ReceivedServerReceipt(String id) {
        super(ELEMENT_NAME, id);
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    public static final class Provider extends EmbeddedExtensionProvider {
        @Override
        protected PacketExtension createReturnExtension(String currentElement, String currentNamespace,
            Map<String, String> attributeMap, List<? extends PacketExtension> content) {
            return new ReceivedServerReceipt(attributeMap.get("id"));
        }
    }

}

package org.kontalk.xmpp.client;

import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.EmbeddedExtensionProvider;


public class ServerReceiptRequest implements PacketExtension {
    public static final String NAMESPACE = "urn:xmpp:server-receipts";
    public static final String ELEMENT_NAME = "request";

    // we are using tag without id here since only server-generated stanzas comes with id
    private static final String XML = String.format("<%s xmlns='%s'/>", ELEMENT_NAME, NAMESPACE);

    private String id;

    public ServerReceiptRequest(String id) {
        this.id = id;
    }

    public ServerReceiptRequest() {
    }

    @Override
    public String getElementName() {
        return ELEMENT_NAME;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toXML() {
        return XML;
    }

    public static final class Provider extends EmbeddedExtensionProvider {
        @Override
        protected PacketExtension createReturnExtension(String currentElement, String currentNamespace,
            Map<String, String> attributeMap, List<? extends PacketExtension> content) {
            return new ServerReceiptRequest(attributeMap.get("id"));
        }
    }

}

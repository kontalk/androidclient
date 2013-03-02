package org.kontalk.xmpp.client;

import org.jivesoftware.smack.packet.IQ;


/**
 * Requests upload info.
 * @author Daniele Ricci
 */
public class UploadInfo extends IQ {
    public static final String ELEMENT_NAME = "upload";
    public static final String NAMESPACE  = UploadExtension.NAMESPACE;

    private final String mNode;
    private final String mMime;

    public UploadInfo(String node) {
        this(node, null);
    }

    public UploadInfo(String node, String mime) {
        mNode = node;
        mMime = mime;
    }

    @Override
    public String getChildElementXML() {
        StringBuilder xml = new StringBuilder();
        xml.append(String.format("<%s xmlns='%s' node='%s'", ELEMENT_NAME, NAMESPACE, mNode));
        if (mMime != null)
            xml.append(String.format("><media type='%s'></%s>", mMime, ELEMENT_NAME));
        else
            xml.append("/>");

        return xml.toString();
    }

    // TODO IQ provider

}

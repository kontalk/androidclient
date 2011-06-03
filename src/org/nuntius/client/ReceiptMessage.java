package org.nuntius.client;

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.os.Bundle;
import android.util.Log;


/**
 * A receipt messages. Wraps a DOM representation of the receipt message in XML
 * format.
 * @author Daniele Ricci
 * @version 1.0
 */
public class ReceiptMessage extends AbstractMessage<Document> {
    private static final String TAG = ReceiptMessage.class.getSimpleName();

    /** A special mime type for a special type of message. */
    public static final String MIME_TYPE = "r";

    private String xmlContent;

    protected int mStatus;
    protected String mMessageId;

    protected ReceiptMessage() {
        super(null, null, null, null);
    }

    public ReceiptMessage(String id, String sender, String content) {
        this(id, sender, MIME_TYPE, null);
    }

    public ReceiptMessage(String id, String sender, String content, List<String> group) {
        super(id, sender, MIME_TYPE, null, group);

        xmlContent = content;
        parseXML();
    }

    private void parseXML() {
        try {
            Log.i(TAG, "parsing XML:\n" + xmlContent);
            // parse XML content
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder;
            builder = factory.newDocumentBuilder();
            StringReader reader = new StringReader(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xmlContent);
            InputSource inputSource = new InputSource(reader);

            this.content = builder.parse(inputSource);
            reader.close();

            Element r = this.content.getDocumentElement();
            if ("r".equals(r.getNodeName())) {
                NodeList children = r.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node node = (Node) children.item(i);
                    if ("i".equals(node.getNodeName()))
                        mMessageId = node.getFirstChild().getNodeValue();
                    else if ("e".equals(node.getNodeName()))
                        mStatus = Integer.parseInt(node.getFirstChild().getNodeValue());
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "cannot parse receipt", e);
            this.content = null;
            this.mStatus = 0;
            this.mMessageId = null;
        }
    }

    public int getStatus() {
        return mStatus;
    }

    public String getMessageId() {
        return mMessageId;
    }

    @Override
    public Bundle toBundle() {
        Bundle b = super.toBundle();
        b.putString(MSG_CONTENT, xmlContent);
        return b;
    }

    @Override
    protected void populateFromBundle(Bundle b) {
        super.populateFromBundle(b);
        xmlContent = b.getString(MSG_CONTENT);
        parseXML();
    }

    @Override
    public String getTextContent() {
        return content.toString();
    }

}

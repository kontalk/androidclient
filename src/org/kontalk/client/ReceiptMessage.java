/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.client;

import java.io.StringReader;
import java.security.GeneralSecurityException;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.kontalk.crypto.Coder;
import org.kontalk.message.AbstractMessage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import android.content.Context;
import android.util.Log;


/**
 * A receipt messages. Wraps a DOM representation of the receipt message in XML
 * format.
 * @author Daniele Ricci
 * @version 1.0
 */
public final class ReceiptMessage extends AbstractMessage<Document> {
    private static final String TAG = ReceiptMessage.class.getSimpleName();

    /** A special mime type for a special type of message. */
    private static final String MIME_TYPE = "r";

    private String xmlContent;

    private int mStatus;
    private String mMessageId;

    protected ReceiptMessage(Context context) {
        super(context, null, null, null, null, false);
    }

    public ReceiptMessage(Context context, String id, String sender, byte[] content) {
        this(context, id, sender, content, null);
    }

    public ReceiptMessage(Context context, String id, String sender, byte[] content, List<String> group) {
        // receipts are not encrypted
        super(context, id, sender, MIME_TYPE, null, false, group);

        xmlContent = new String(content);
        parseXML();
    }

    private void parseXML() {
        try {
            //Log.v(TAG, "parsing XML:\n" + xmlContent);
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
    public String getTextContent() {
        return xmlContent;
    }

    @Override
    public byte[] getBinaryContent() {
        return xmlContent.getBytes();
    }

    @Override
    public void decrypt(Coder coder) throws GeneralSecurityException {
        // TODO
    }

    public static boolean supportsMimeType(String mime) {
        return MIME_TYPE.equalsIgnoreCase(mime);
    }

}

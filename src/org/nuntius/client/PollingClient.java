package org.nuntius.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * A client for the polling service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class PollingClient extends AbstractClient {

    public PollingClient(EndpointServer server, String token) {
        super(server, token);
    }

    /**
     * Polls the server for new messages.
     * @throws IOException
     */
    public List<AbstractMessage<?>> poll() throws IOException {

        List<AbstractMessage<?>> list = null;
        try {
            // http request!
            currentRequest = mServer.preparePolling(mAuthToken);
            HttpResponse response = mServer.execute(currentRequest);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            /*
            String xmlContent = EntityUtils.toString(response.getEntity());
            StringReader reader = new StringReader(xmlContent);
            InputSource inputSource = new InputSource(reader);
            */

            Document doc = builder.parse(response.getEntity().getContent());
            //reader.close();

            Element body = doc.getDocumentElement();
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = (Node) children.item(i);
                if ("m".equals(node.getNodeName())) {
                    String id = null;
                    String from = null;
                    String text = null;
                    String mime = null;
                    List<String> group = null;

                    // message!
                    NodeList msgChildren = node.getChildNodes();
                    for (int j = 0; j < msgChildren.getLength(); j++) {
                        Element n2 = (Element) msgChildren.item(j);
                        if ("i".equals(n2.getNodeName()))
                            id = n2.getFirstChild().getNodeValue();
                        else if ("s".equals(n2.getNodeName()))
                            from = n2.getFirstChild().getNodeValue();
                        else if ("c".equals(n2.getNodeName())) {
                            text = n2.getFirstChild().getNodeValue();
                            mime = n2.getAttribute("t");
                        }
                        else if ("g".equals(n2.getNodeName())) {
                            if (group == null)
                                group = new ArrayList<String>();
                            group.add(n2.getFirstChild().getNodeValue());
                        }
                    }

                    if (id != null && from != null && text != null && mime != null) {
                        // add the message to the list
                        AbstractMessage<?> msg = null;

                        // plain text message
                        if (mime == null || PlainTextMessage.supportsMimeType(mime)) {
                            msg = new PlainTextMessage(id, from, text, group);
                        }

                        // message receipt
                        else if (ReceiptMessage.supportsMimeType(mime)) {
                            msg = new ReceiptMessage(id, from, text, group);
                        }

                        // image message
                        else if (ImageMessage.supportsMimeType(mime)) {
                            // extra argument: mime (first parameter)
                            msg = new ImageMessage(mime, id, from, text, group);
                        }

                        // TODO else other mime types

                        if (msg != null) {
                            if (list == null)
                                list = new ArrayList<AbstractMessage<?>>();
                            list.add(msg);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            IOException ie = new IOException("parse error");
            ie.initCause(e);
            throw ie;
        }
        finally {
            currentRequest = null;
        }

        return list;
    }
}

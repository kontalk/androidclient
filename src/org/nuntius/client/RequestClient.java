package org.nuntius.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class RequestClient extends AbstractClient {


    public RequestClient(EndpointServer server, String token) {
        super(server, token);
    }

    /**
     * Polls the server for new messages.
     * @throws IOException
     */
    public List<StatusResponse> request(String cmd, List<NameValuePair> params,
            String content) throws IOException {

        List<StatusResponse> list = null;
        try {
            // http request!
            currentRequest = mServer.prepareRequest(cmd, params, mAuthToken, content);
            HttpResponse response = mServer.execute(currentRequest);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(response.getEntity().getContent());
            Element body = doc.getDocumentElement();
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = (Node) children.item(i);
                if ("s".equals(node.getNodeName())) {
                    String errcode = null;
                    Map<String,String> extra = null;
                    // status!
                    NodeList msgChildren = node.getChildNodes();
                    for (int j = 0; j < msgChildren.getLength(); j++) {
                        Element n2 = (Element) msgChildren.item(j);

                        // error code
                        if ("e".equals(n2.getNodeName())) {
                            errcode = n2.getFirstChild().getNodeValue();
                        }
                        // other data
                        else {
                            if (extra == null)
                                extra = new HashMap<String, String>();
                            extra.put(n2.getNodeName(), n2.getFirstChild().getNodeValue());
                        }
                    }

                    if (errcode != null) {
                        // add the status to the list
                        StatusResponse status = null;
                        try {
                            status = new StatusResponse(Integer.parseInt(errcode));
                            status.extra = extra;
                        }
                        catch (Exception e) {}

                        if (status != null) {
                            if (list == null)
                                list = new ArrayList<StatusResponse>();
                            list.add(status);
                        }
                    }
                }
            }
        }
        catch (ParserConfigurationException e) {
            throw innerException("parser configuration error", e);
        }
        catch (IllegalStateException e) {
            throw innerException("illegal state", e);
        }
        catch (SAXException e) {
            throw innerException("parse error", e);
        }
        finally {
            currentRequest = null;
        }

        return list;
    }

    private IOException innerException(String detail, Throwable cause) {
        IOException ie = new IOException(detail);
        ie.initCause(cause);
        return ie;
    }
}

package org.nuntius.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.nuntius.service.MessageRequestListener;
import org.nuntius.service.RequestListener;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;


/**
 * A generic request endpoint client.
 * @author Daniele Ricci
 * @version 1.0
 */
public class RequestClient extends AbstractClient {

    public RequestClient(EndpointServer server, String token) {
        super(server, token);
    }

    public List<StatusResponse> message(final String[] group, final String mime,
                final byte[] content, final RequestListener listener)
            throws IOException {

        try {
            // http request!
            currentRequest = mServer.prepareMessage(listener, mAuthToken, group, mime,
                new ByteArrayInputStream(content), content.length);
            return execute();
        }
        catch (Exception e) {
            throw innerException("post message error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    public List<StatusResponse> message(final String[] group, final String mime, final Uri uri,
            final Context context, final MessageRequestListener listener)
                throws IOException {

        try {
            AssetFileDescriptor stat = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            long length = stat.getLength();
            InputStream in = context.getContentResolver().openInputStream(uri);

            // http request!
            currentRequest = mServer.prepareMessage(listener, mAuthToken, group, mime, in, length);
            return execute();
        }
        catch (Exception e) {
            throw innerException("post message error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    /**
     * Sends a request to the server.
     * @throws IOException
     */
    public List<StatusResponse> request(final String cmd, final List<NameValuePair> params,
            final byte[] content) throws IOException {

        try {
            // http request!
            currentRequest = mServer.prepareRequest(cmd, params, mAuthToken, content);
            return execute();
        }
        catch (Exception e) {
            throw innerException("request error", e);
        }
        finally {
            currentRequest = null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<StatusResponse> execute() throws Exception {
        List<StatusResponse> list = null;
        try {
            // http request!
            HttpResponse response = mServer.execute(currentRequest);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            /*
            String xmlContent = EntityUtils.toString(response.getEntity());
            Log.e("AAAAHHH!!!", xmlContent);
            StringReader reader = new StringReader(xmlContent);
            InputSource inputSource = new InputSource(reader);

            Document doc = builder.parse(inputSource);
            reader.close();
            */

            Document doc = builder.parse(response.getEntity().getContent());
            Element body = doc.getDocumentElement();
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = (Node) children.item(i);
                if ("s".equals(node.getNodeName())) {
                    String errcode = null;
                    Map<String, Object> extra = null;
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
                            String key = n2.getNodeName();
                            String value = n2.getFirstChild().getNodeValue();
                            if (extra == null) {
                                extra = new HashMap<String, Object>(1);
                                extra.put(key, value);
                            }
                            else {
                                Object old = extra.get(key);
                                // no old value - single value
                                if (old == null) {
                                    extra.put(key, value);
                                }
                                // old single value - transform to array
                                else if (!(old instanceof List<?>)) {
                                    List<String> newObj = new ArrayList<String>(1);
                                    newObj.add(value);
                                    extra.put(key, newObj);
                                }
                                // old multiple values - add to list
                                else {
                                    List<String> newObj = (List<String>) old;
                                    newObj.add(value);
                                }
                            }
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

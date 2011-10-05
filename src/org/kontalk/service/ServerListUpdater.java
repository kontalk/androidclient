package org.kontalk.service;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Context;


/**
 * Worker thread for downloading and caching locally a server list.
 * This class doesn't need to be configured: it hides all the logic of picking
 * a random server, connecting to it, downloading the server list and saving it
 * in the application cache.
 * @author Daniele Ricci
 */
public class ServerListUpdater extends Thread {

    public static final int SUPPORTED_LIST_VERSION = 1;

    private final Context mContext;

    public ServerListUpdater(Context context) {
        mContext = context;
    }

    @Override
    public void run() {
        // TODO do it!
    }

    private static ServerList parseBuiltinList(Context context) throws IOException {
        InputStream in = context.getResources()
            .openRawResource(R.xml.serverlist);
        return parseList(in);
    }

    private static ServerList parseList(InputStream in) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(in);
            Element body = doc.getDocumentElement();
            if (!"servers".equals(body.getNodeName()))
                throw new IllegalArgumentException("invalid server list");

            String _version = body.getAttribute("version");
            int version = Integer.parseInt(_version);
            if (version != SUPPORTED_LIST_VERSION)
                throw new IllegalArgumentException("server list version mismatch");

            ServerList list = new ServerList();
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = (Node) children.item(i);
                if ("server".equals(node.getNodeName())) {
                    String addr = node.getFirstChild().getNodeValue();

                    if (addr != null)
                        list.add(new EndpointServer(addr.trim()));
                }
            }

            return list;
        }
        catch (Exception e) {
            IOException ie = new IOException("parse error");
            ie.initCause(e);
            throw ie;
        }
    }
}

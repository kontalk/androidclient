package org.kontalk.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.content.Context;
import android.util.Log;


/**
 * Worker thread for downloading and caching locally a server list.
 * This class doesn't need to be configured: it hides all the logic of picking
 * a random server, connecting to it, downloading the server list and saving it
 * in the application cache. Finally, it restarts the message center.
 * @author Daniele Ricci
 */
public class ServerListUpdater extends Thread {
    private static final String TAG = ServerListUpdater.class.getSimpleName();

    public static final int SUPPORTED_LIST_VERSION = 1;

    private static ServerList mCurrentList;

    private final Context mContext;
    private UpdaterListener mListener;
    private HttpRequestBase mRequest;

    public ServerListUpdater(Context context) {
        mContext = context;
    }

    public void setListener(UpdaterListener listener) {
        mListener = listener;
    }

    @Override
    public void run() {
        /**
         * If we started the thread, it means we have to update our server list.
         * First check if we already have a loaded one, if not just load one -
         * either the builtin one or the cached one.
         */

        ServerList current = null;
        Throwable exc = null;

        try {
            Log.d(TAG, "loading cached server list");
            current = parseCachedList(mContext);
        }
        catch (IOException e) {
            // invalid or no cached list :(
            Log.e(TAG, "invalid or no cached list", e);
            exc = e;

            try {
                Log.d(TAG, "loading builtin server list");
                current = parseBuiltinList(mContext);
            }
            catch (IOException be) {
                // WHAT?!?!? Error loading builtin list!???
                exc = be;
            }
        }

        /** no server list -- notify to user */
        if (current == null) {
            Log.i(TAG, "no list to pick a random server from - aborting");

            // notify to UI
            if (mListener != null)
                mListener.nodata(exc);

            return;
        }

        try {
            /**
             * We have a server list - either builtin or cached. Now pick a random
             * server from the list and contact it for the latest server list.
             */
            EndpointServer random = current.random();
            mRequest = random.prepareServerListRequest();
            HttpResponse res = random.execute(mRequest);
            // write down to cache
            OutputStream out = new FileOutputStream(getCachedListFile(mContext));
            res.getEntity().writeTo(out);
            out.close();
            // free resources
            res.getEntity().consumeContent();

            // parse cached list :)
            mCurrentList = parseCachedList(mContext);
            if (mListener != null)
                mListener.updated(mCurrentList);

            // restart message center
            MessageCenterService.stopMessageCenter(mContext.getApplicationContext());
            MessageCenterService.startMessageCenter(mContext.getApplicationContext());
        }
        catch (IOException e) {
            if (mListener != null)
                mListener.error(e);
        }
        finally {
            mRequest = null;
        }
    }

    public void cancel() {
        if (mRequest != null)
            mRequest.abort();
    }

    private static File getCachedListFile(Context context) {
        return new File(context.getCacheDir(), "serverlist.xml");
    }

    private static ServerList parseBuiltinList(Context context) throws IOException {
        InputStream in = context.getResources()
            .openRawResource(R.raw.serverlist);
        return parseList(in);
    }

    private static ServerList parseCachedList(Context context) throws IOException {
        InputStream in = new FileInputStream(getCachedListFile(context));
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

            String _date = body.getAttribute("timestamp");
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            Date date = fmt.parse(_date);

            ServerList list = new ServerList(date);
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

    /** Returns (and loads if necessary) the current server list. */
    public static ServerList getCurrentList(Context context) {
        if (mCurrentList != null)
            return mCurrentList;

        try {
            mCurrentList = parseCachedList(context);
        }
        catch (IOException e) {
            try {
                mCurrentList = parseBuiltinList(context);
            }
            catch (IOException be) {
                Log.w(TAG, "unable to load builtin server list", be);
            }
        }

        return mCurrentList;
    }

    public interface UpdaterListener {
        /** Called if either the cached list or the built-in list cannot be loaded.*/
        public void nodata(Throwable e);
        /** Called if an error occurs during update. */
        public void error(Throwable e);
        /** Called when list update has finished. */
        public void updated(ServerList list);
    }
}

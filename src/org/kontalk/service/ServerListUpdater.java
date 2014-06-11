/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.kontalk.R;
import org.kontalk.client.ClientHTTPConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.util.Preferences;

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

    private static ServerList sCurrentList;

    private final Context mContext;
    private UpdaterListener mListener;
    private ClientHTTPConnection mConnection;

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

        /**
         * We have a server list - either builtin or cached. Now pick a random
         * server from the list and contact it for the latest server list.
         */
        EndpointServer random = Preferences.getEndpointServer(mContext);

        /** no server found -- notify to user */
        if (random == null) {
            Log.i(TAG, "no list to pick a random server from - aborting");

            // notify to UI
            if (mListener != null)
                mListener.nodata();

            return;
        }

        try {
            // TODO
            /*
            mConnection = new ClientHTTPConnection(null, mContext, random, null);
            Protocol.ServerList data = mConnection.serverList();
            if (data != null) {
                // write down to cache
                OutputStream out = new FileOutputStream(getCachedListFile(mContext));
                data.writeTo(out);
                out.close();
            }

            // parse cached list :)
            sCurrentList = parseList(data);
            if (mListener != null)
                mListener.updated(sCurrentList);

            // restart message center
            MessageCenterService.restartMessageCenter(mContext.getApplicationContext());
            */
            throw new IOException();
        }
        catch (IOException e) {
            if (mListener != null)
                mListener.error(e);
        }
        finally {
            mConnection = null;
        }
    }

    public void cancel() {
        if (mConnection != null)
            mConnection.abort();
    }

    private static File getCachedListFile(Context context) {
        return new File(context.getCacheDir(), "serverlist.pb2");
    }

    private static ServerList parseBuiltinList(Context context) throws IOException {
        InputStream in = context.getResources()
            .openRawResource(R.raw.serverlist);
        Properties prop = new Properties();
        prop.load(in);
        in.close();

        try {
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
            Date date = format.parse(prop.getProperty("timestamp"));
            ServerList list = new ServerList(date);
            int i = 1;
            String server;
            while ((server = prop.getProperty("server" + i)) != null) {
                list.add(new EndpointServer(server));
                i++;
            }

            return list;
        }
        catch (Exception e) {
            IOException ie = new IOException("parse error");
            ie.initCause(e);
            throw ie;
        }
    }

    /*
    private static ServerList parseList(Protocol.ServerList pack) {
        Date date = new Date(pack.getTimestamp() * 1000);
        ServerList list = new ServerList(date);
        for (int i = 0; i < pack.getEntryCount(); i++) {
            Protocol.ServerList.Entry e = pack.getEntry(i);
            list.add(new EndpointServer(e.getAddress(), e.getPort(), e.getHttpPort()));
        }

        return list;
    }
    */

    private static ServerList parseCachedList(Context context) throws IOException {
        /*
        InputStream in = new FileInputStream(getCachedListFile(context));
        Protocol.ServerList pack = Protocol.ServerList.parseFrom(in);
        return parseList(pack);
        */
        throw new FileNotFoundException();
    }

    /** Returns (and loads if necessary) the current server list. */
    public static ServerList getCurrentList(Context context) {
        if (sCurrentList != null)
            return sCurrentList;

        try {
            sCurrentList = parseCachedList(context);
        }
        catch (IOException e) {
            try {
                sCurrentList = parseBuiltinList(context);
            }
            catch (IOException be) {
                Log.w(TAG, "unable to load builtin server list", be);
            }
        }

        return sCurrentList;
    }

    public interface UpdaterListener {
        /** Called if either the cached list or the built-in list cannot be loaded.*/
        public void nodata();
        /** Called if an error occurs during update. */
        public void error(Throwable e);
        /** Called when list update has finished. */
        public void updated(ServerList list);
    }
}

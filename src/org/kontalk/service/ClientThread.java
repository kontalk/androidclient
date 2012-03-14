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

package org.kontalk.service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.BoxProtocol.BoxContainer;
import org.kontalk.client.ClientConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.Protocol;
import org.kontalk.client.TxListener;
import org.kontalk.message.AbstractMessage;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;


/**
 * Kontalk client thread.
 * @author Daniele Ricci
 */
public class ClientThread extends Thread {
    private static final String TAG = ClientThread.class.getSimpleName();

    private final Context mContext;
    private final EndpointServer mServer;
    private final Map<String, TxListener> mTxListeners;
    private final Map<String, TxListener> mHandlers;
    private TxListener mDefaultTxListener;
    private MessageListener mMessageListener;
    private String mAuthToken;

    private boolean mInterrupted;

    private ClientConnection mClient;

    public ClientThread(Context context, EndpointServer server) {
        super(ClientThread.class.getSimpleName());
        mContext = context;
        mServer = server;
        mClient = new ClientConnection(mContext, mServer);
        mTxListeners = new HashMap<String, TxListener>();
        mHandlers = new HashMap<String, TxListener>();
    }

    public void setDefaultTxListener(TxListener listener) {
        mDefaultTxListener = listener;
    }

    /**
     * Sets a special listener for incoming {@link Protocol.NewMessage} packs.
     * Those packs will be automatically parsed into {@link AbstractMessage}s.
     */
    public void setMessageListener(MessageListener listener) {
        mMessageListener = listener;
    }

    /**
     * Sets a listener that will be called for a specific received pack type.
     * If a transaction listener matches the txId, it will be called instead.
     */
    public void setHandler(Class<? extends MessageLite> klass, TxListener listener) {
        mHandlers.put(klass.getSimpleName(), listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        mAuthToken = Authenticator.getDefaultAccountToken(mContext);
        if (mAuthToken == null) {
            Log.w(TAG, "invalid token - exiting");
            return;
        }

        // exposing sensitive data - Log.d(TAG, "using token: " + mAuthToken);

        Log.d(TAG, "using server " + mServer.toString());
        try {
            // connect
            mClient.connect();

            // authenticate
            Log.v(TAG, "connected. Authenticating...");
            mClient.authenticate(mAuthToken);

            // now start the main loop
            while (!mInterrupted) {
                BoxContainer box = mClient.recv();
                if (box == null) {
                    Log.d(TAG, "no data from server - shutting down.");
                    shutdown();
                    break;
                }

                String name = box.getName();
                MessageLite msg = null;
                try {
                    // damn java reflection :S
                    Class<? extends MessageLite> msgc = (Class<? extends MessageLite>) Thread.currentThread()
                            .getContextClassLoader().loadClass("org.kontalk.client.Protocol$" + name);
                    Method parseDelimitedFrom = msgc.getMethod("parseFrom", ByteString.class);
                    msg = (MessageLite) parseDelimitedFrom.invoke(null, box.getValue());
                }
                catch (Exception e) {
                    Log.e(TAG, "protocol error", e);
                    shutdown();
                    break;
                }

                if (msg != null) {
                    if (name.equals(Protocol.NewMessage.class.getSimpleName())) {
                        // TODO parse message into AbstractMessage
                    }

                    else {
                        // try to get the tx-specific listener
                        String txId = box.getTxId();
                        TxListener listener = mTxListeners.get(txId);

                        // try to get the pack-specific listener
                        if (listener == null)
                            listener = mHandlers.get(name);

                        // no registered listeners found - fallback to default
                        if (listener == null)
                            listener = mDefaultTxListener;

                        listener.tx(mClient, txId, msg);
                    }
                }
            }
        }

        catch (Exception e) {
            Log.e(TAG, e.toString(), e);
        }

        finally {
            try {
                mClient.close();
                mClient = null;
            }
            catch (Exception e) {
                // ignore exception
            }
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
        mInterrupted = true;
    }

    @Override
    public boolean isInterrupted() {
        return mInterrupted;
    }

    /** Shuts down this client thread gracefully. */
    public synchronized void shutdown() {
        Log.d(TAG, "shutting down");
        interrupt();

        Log.d(TAG, "aborting client");
        try {
            mClient.close();
        }
        catch (IOException e) {
            // ignore exception
        }
        // do not join - just discard the thread

        Log.d(TAG, "exiting");
        mClient = null;
    }
}

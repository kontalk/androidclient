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
import java.util.List;
import java.util.Map;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.BoxProtocol.BoxContainer;
import org.kontalk.client.ClientConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.Protocol;
import org.kontalk.client.Protocol.NewMessage;
import org.kontalk.client.TxListener;
import org.kontalk.crypto.Coder;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.PlainTextMessage;
import org.kontalk.message.ReceiptMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.ui.MessagingPreferences;

import android.accounts.Account;
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
    private String mMyUsername;

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
        Account acc = Authenticator.getDefaultAccount(mContext);
        mMyUsername = acc.name;

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
                MessageLite pack = null;
                try {
                    // damn java reflection :S
                    Class<? extends MessageLite> msgc = (Class<? extends MessageLite>) Thread.currentThread()
                            .getContextClassLoader().loadClass("org.kontalk.client.Protocol$" + name);
                    Method parseDelimitedFrom = msgc.getMethod("parseFrom", ByteString.class);
                    pack = (MessageLite) parseDelimitedFrom.invoke(null, box.getValue());
                }
                catch (Exception e) {
                    Log.e(TAG, "protocol error", e);
                    shutdown();
                    break;
                }

                if (pack != null) {
                    if (name.equals(NewMessage.class.getSimpleName())) {
                        // parse message into AbstractMessage
                        AbstractMessage<?> msg = parseNewMessage((NewMessage) pack);
                        mMessageListener.incoming(msg);
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

                        listener.tx(mClient, txId, pack);
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

    private AbstractMessage<?> parseNewMessage(NewMessage pack) {
        String id = pack.getMessageId();
        String origId = (pack.hasOriginalId()) ? pack.getOriginalId() : null;
        String mime = (pack.hasMime()) ? pack.getMime() : null;
        String from = pack.getSender();
        ByteString text = pack.getContent();
        String fetchUrl = (pack.hasUrl()) ? pack.getUrl() : null;
        List<String> group = pack.getGroupList();

        // flag for originally encrypted message
        boolean origEncrypted = false;

        for (int i = 0; i < pack.getFlagsCount(); i++) {
            if ("encrypted".equals(pack.getFlags(i)))
                origEncrypted = true;
        }

        // add the message to the list
        AbstractMessage<?> msg = null;
        String realId = null;

        // use the originating id as the message id to match with message in database
        if (origId != null) {
            realId = id;
            id = origId;
        }

        // content
        byte[] content = text.toByteArray();

        // flag for left encrypted message
        boolean encrypted = false;

        if (origEncrypted) {
            Coder coder = MessagingPreferences.getDecryptCoder(mContext, mMyUsername);
            try {
                content = coder.decrypt(content);
            }
            catch (Exception exc) {
                // pass over the message even if encrypted
                // UI will warn the user about that and wait
                // for user decisions
                Log.e(TAG, "decryption failed", exc);
                encrypted = true;
            }
        }

        // plain text message
        if (mime == null || PlainTextMessage.supportsMimeType(mime)) {
            msg = new PlainTextMessage(mContext, id, from, content, encrypted, group);
        }

        // message receipt
        else if (ReceiptMessage.supportsMimeType(mime)) {
            msg = new ReceiptMessage(mContext, id, from, content, group);
        }

        // image message
        else if (ImageMessage.supportsMimeType(mime)) {
            // extra argument: mime (first parameter)
            msg = new ImageMessage(mContext, mime, id, from, content, encrypted, group);
        }

        // vcard message
        else if (VCardMessage.supportsMimeType(mime)) {
            msg = new VCardMessage(mContext, id, from, content, encrypted, group);
        }

        // TODO else other mime types

        if (msg != null) {
            // set the real message id
            msg.setRealId(realId);

            // remember encryption! :)
            if (origEncrypted)
                msg.setWasEncrypted(true);

            // set the fetch url (if any)
            if (fetchUrl != null) {
                Log.d(TAG, "using fetch url: " + fetchUrl);
                msg.setFetchUrl(fetchUrl);
            }
        }

        // might be a null to notify that the mime type is not supported.
        return msg;
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
            if (mClient != null)
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

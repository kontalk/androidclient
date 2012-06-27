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
import org.kontalk.client.ClientHTTPConnection;
import org.kontalk.client.ClientListener;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.Protocol;
import org.kontalk.client.Protocol.NewMessage;
import org.kontalk.client.Protocol.Ping;
import org.kontalk.client.Protocol.Pong;
import org.kontalk.client.TxListener;
import org.kontalk.crypto.Coder;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.PlainTextMessage;
import org.kontalk.message.ReceiptMessage;
import org.kontalk.message.UserPresenceMessage;
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

    /** Max connection retry count if idle. */
    private static final int MAX_IDLE_BACKOFF = 10;

    private final Context mContext;
    private EndpointServer mServer;
    private final Map<String, TxListener> mTxListeners;
    private final Map<String, TxListener> mHandlers;
    private ClientListener mClientListener;
    private TxListener mDefaultTxListener;
    private MessageListener mMessageListener;
    private String mAuthToken;
    private String mMyUsername;

    private boolean mInterrupted;

    /** Connection retry count for exponential backoff. */
    private int mRetryCount;

    /** Connection is re-created on demand if necessary. */
    private ClientConnection mClient;

    /** HTTP connection to server. */
    protected ClientHTTPConnection mHttpConn;

    /** Parent thread to be notified. */
    private final ParentThread mParent;

    /**
     * The pack lock. This is used to block receiving packs to allow pack
     * senders to setup their own transaction listeners.
     */
    private final Object mPackLock = new Object();

    public ClientThread(Context context, ParentThread parent, EndpointServer server) {
        super(ClientThread.class.getSimpleName());
        mContext = context;
        mServer = server;
        mTxListeners = new HashMap<String, TxListener>();
        mHandlers = new HashMap<String, TxListener>();
        mParent = parent;
    }

    public void setClientListener(ClientListener listener) {
        mClientListener = listener;
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

    /** Sets a listener that will be called for a specific transaction id. */
    public void setTxListener(String txId, TxListener listener) {
        mTxListeners.put(txId, listener);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            mAuthToken = Authenticator.getDefaultAccountToken(mContext);
            if (mAuthToken == null) {
                Log.w(TAG, "invalid token - exiting");
                return;
            }

            // exposing sensitive data - Log.d(TAG, "using token: " + mAuthToken);
            Account acc = Authenticator.getDefaultAccount(mContext);
            mMyUsername = acc.name;

            // now start the main loop
            while (!mInterrupted) {
                Log.d(TAG, "using server " + mServer.toString());
                try {
                    if (mClient == null)
                        mClient = new ClientConnection(mContext, mServer);

                    // connect
                    mClient.connect();
                    if (mClientListener != null)
                        mClientListener.connected(this);

                    // authenticate
                    mClient.authenticate(mAuthToken);

                    // now start the main loop
                    while (!mInterrupted) {
                        // this should be the right moment
                        mRetryCount = 0;

                        /*
                         * This recv() will block until delimited data is
                         * received from the server. To allow the message
                         * center to timeout after a given amount of time, the
                         * request worker issues a idle message to be run after
                         * 60 seconds when no requests are received.
                         */
                        BoxContainer box = mClient.recv();

                        if (box == null) {
                            Log.d(TAG, "no data from server");
                            throw new IOException("connection lost");
                        }

                        // synchronize on pack lock
                        synchronized (mPackLock) {

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

                                else if (name.equals(Ping.class.getSimpleName())) {
                                    // send pong directly
                                    Pong.Builder b = Pong.newBuilder();
                                    b.setTimestamp(System.currentTimeMillis()*1000);
                                    mClient.send(b.build(), box.getTxId());
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
                }

                catch (IOException ie) {
                    // just disconnect here...
                    mClient.close();
                    mClient = null;

                    // uncontrolled interrupt - handle errors
                    if (!mInterrupted) {
                        Log.e(TAG, "connection error", ie);
                        try {
                            // max reconnections - idle message center
                            if (mRetryCount >= MAX_IDLE_BACKOFF) {
                                Log.d(TAG, "maximum number of reconnections - idling message center");
                                MessageCenterService.idleMessageCenter(mContext);
                            }

                            // notify parent we are respawning
                            mParent.childRespawning(0);

                            // exponential backoff :)
                            float time = (float) ((Math.pow(2, ++mRetryCount)) - 1) / 2;
                            Log.d(TAG, "retrying in " + time + " seconds (retry="+mRetryCount+")");
                            Thread.sleep((long) (time * 1000));
                            // this is to avoid the exponential backoff counter to be reset
                            continue;
                        }
                        catch (InterruptedException intexc) {
                            // interrupted - exit
                            break;
                        }
                    }
                }

                mRetryCount = 0;
            }
        }
        finally {
            // reason not used for now
            mParent.childTerminated(0);
        }
    }

    private AbstractMessage<?> parseNewMessage(NewMessage pack) {
        String id = pack.getMessageId();
        String origId = (pack.hasOriginalId()) ? pack.getOriginalId() : null;
        String mime = (pack.hasMime()) ? pack.getMime() : null;
        String from = pack.getSender();
        String timestamp = pack.getTimestamp();
        ByteString text = pack.getContent();
        String fetchUrl = (pack.hasUrl()) ? pack.getUrl() : null;
        List<String> group = pack.getGroupList();
        // use content length if no length has been passed through
        long length = (pack.hasLength()) ? pack.getLength() : text.size();

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
                length = content.length;
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
            // TODO convert to global pool
            msg = new PlainTextMessage(mContext, id, timestamp, from, content, encrypted, group);
        }

        // message receipt
        else if (ReceiptMessage.supportsMimeType(mime)) {
            msg = new ReceiptMessage(mContext, id, timestamp, from, content, group);
        }

        // presence message
        else if (UserPresenceMessage.supportsMimeType(mime)) {
            msg = new UserPresenceMessage(mContext, id, timestamp, from, content);
        }

        // image message
        else if (ImageMessage.supportsMimeType(mime)) {
            // extra argument: mime (first parameter)
            msg = new ImageMessage(mContext, mime, id, timestamp, from, content, encrypted, group);
        }

        // vcard message
        else if (VCardMessage.supportsMimeType(mime)) {
            msg = new VCardMessage(mContext, id, timestamp, from, content, encrypted, group);
        }

        // TODO else other mime types

        if (msg != null) {
            // set real message id
            msg.setRealId(realId);
            // set need ack flag
            msg.setNeedAck(pack.getNeedAck());
            // set length
            msg.setLength(length);

            // remember encryption! :)
            if (origEncrypted)
                msg.setWasEncrypted(true);

            // set the fetch url (if any)
            if (fetchUrl != null)
                msg.setFetchUrl(fetchUrl);
        }

        // might be a null to notify that the mime type is not supported.
        return msg;
    }

    public ClientConnection getConnection() {
        return mClient;
    }

    public ClientHTTPConnection getHttpConnection() {
        if (mHttpConn == null)
            mHttpConn = new ClientHTTPConnection(this, mContext, mServer, mAuthToken);
        return mHttpConn;
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

    public boolean isConnected() {
        return (mClient != null && mClient.isConnected());
    }

    /** Sets the server the next time we will connect to. */
    public void setServer(EndpointServer server) {
        mServer = server;
    }

    /** Shuts down this client thread gracefully. */
    public synchronized void shutdown() {
        interrupt();

        if (mClient != null)
            mClient.close();
        // do not join - just discard the thread
    }

    public Object getPackLock() {
        return mPackLock;
    }

}

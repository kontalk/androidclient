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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.kontalk.crypto.Coder;
import org.kontalk.message.AbstractMessage;
import org.kontalk.message.ImageMessage;
import org.kontalk.message.PlainTextMessage;
import org.kontalk.message.VCardMessage;
import org.kontalk.ui.MessagingPreferences;

import android.content.Context;
import android.util.Log;

import com.google.protobuf.ByteString;


/**
 * A client for the polling service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class PollingClient extends AbstractClient {
    private static final String TAG = PollingClient.class.getSimpleName();

    private final String mMyNumber;

    public PollingClient(Context context, EndpointServer server, String token, String myNumber) {
        super(context, server, token);
        mMyNumber = myNumber;
    }

    /**
     * Polls the server for new messages.
     * @throws IOException
     */
    public List<AbstractMessage<?>> poll()
            throws IOException {

        List<AbstractMessage<?>> list = null;
        try {
            // http request!
            currentRequest = mServer.preparePolling(mAuthToken);

            HttpResponse response = mServer.execute(currentRequest);

            Protocol.NewMessages data = Protocol.NewMessages
                .parseFrom(response.getEntity().getContent());

            if (data.getStatus() == Protocol.Status.STATUS_SUCCESS) {
                for (int i = 0; i < data.getMessageCount(); i++) {
                    Protocol.NewMessageEntry e = data.getMessage(i);

                    String id = e.getMessageId();
                    String origId = (e.hasOriginalId()) ? e.getOriginalId() : null;
                    String mime = (e.hasMime()) ? e.getMime() : null;
                    String from = e.getSender();
                    ByteString text = e.getContent();
                    String fetchUrl = (e.hasUrl()) ? e.getUrl() : null;
                    List<String> group = e.getGroupList();

                    // flag for originally encrypted message
                    boolean origEncrypted = e.getEncrypted();

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
                        Coder coder = MessagingPreferences.getDecryptCoder(mContext, mMyNumber);
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

                        if (list == null)
                            list = new ArrayList<AbstractMessage<?>>();
                    }

                    /*
                     * Add a null value anyway to notify to the polling thread
                     * that the mime type is not supported.
                     */
                    list.add(msg);
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

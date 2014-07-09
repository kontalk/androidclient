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
package org.kontalk.service.msgcenter;

import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_MESSAGE;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM_USERID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInfo;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.kontalk.client.AckServerReceipt;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.E2EEncryption;
import org.kontalk.client.OutOfBandData;
import org.kontalk.client.ReceivedServerReceipt;
import org.kontalk.client.SentServerReceipt;
import org.kontalk.client.ServerReceipt;
import org.kontalk.client.ServerReceiptRequest;
import org.kontalk.crypto.Coder;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.RawComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;


/**
 * Packet listener for message stanzas.
 * @author Daniele Ricci
 */
class MessageListener extends MessageCenterPacketListener {

    private static final String selectionOutgoing = Messages.DIRECTION + "=" + Messages.DIRECTION_OUT;
    private static final String selectionIncoming = Messages.DIRECTION + "=" + Messages.DIRECTION_IN;

    public MessageListener(MessageCenterService instance) {
        super(instance);
    }

    @Override
    public void processPacket(Packet packet) {
        Map<String, Long> waitingReceipt = getWaitingReceiptList();

        org.jivesoftware.smack.packet.Message m = (org.jivesoftware.smack.packet.Message) packet;

        if (m.getType() == org.jivesoftware.smack.packet.Message.Type.chat) {
            Intent i = new Intent(ACTION_MESSAGE);
            String from = m.getFrom();
            String network = StringUtils.parseServer(from);
            // our network - convert to userId
            if (network.equalsIgnoreCase(getServer().getNetwork())) {
                StringBuilder b = new StringBuilder();
                b.append(StringUtils.parseName(from));
                b.append(StringUtils.parseResource(from));
                i.putExtra(EXTRA_FROM_USERID, b.toString());
            }

            // check if there is a composing notification
            PacketExtension _chatstate = m.getExtension("http://jabber.org/protocol/chatstates");
            ChatStateExtension chatstate = null;
            if (_chatstate != null) {
                chatstate = (ChatStateExtension) _chatstate;
                i.putExtra("org.kontalk.message.chatState", chatstate.getElementName());

            }

            i.putExtra(EXTRA_FROM, from);
            i.putExtra(EXTRA_TO, m.getTo());
            sendBroadcast(i);

            // non-active notifications are not to be processed as messages
            if (chatstate != null && !chatstate.getElementName().equals(ChatState.active.name()))
                return;

            // delayed deliver extension is the first the be processed
            // because it's used also in delivery receipts
            PacketExtension _delay = m.getExtension("delay", "urn:xmpp:delay");
            if (_delay == null)
                _delay = m.getExtension("x", "jabber:x:delay");

            Date stamp = null;
            if (_delay != null) {
                if (_delay instanceof DelayInformation) {
                    stamp = ((DelayInformation) _delay).getStamp();
                }
                else if (_delay instanceof DelayInfo) {
                    stamp = ((DelayInfo) _delay).getStamp();
                }
            }

            long serverTimestamp = 0;
            if (stamp != null)
                serverTimestamp = stamp.getTime();
            else
                serverTimestamp = System.currentTimeMillis();

            PacketExtension _ext = m.getExtension(ServerReceipt.NAMESPACE);

            // delivery receipt
            if (_ext != null && !ServerReceiptRequest.ELEMENT_NAME.equals(_ext.getElementName())) {
                ServerReceipt ext = (ServerReceipt) _ext;

                synchronized (waitingReceipt) {
                    String id = m.getPacketID();
                    Long _msgId = waitingReceipt.get(id);
                    long msgId = (_msgId != null) ? _msgId : 0;
                    ContentResolver cr = getContext().getContentResolver();

                    // TODO compress this code
                    if (ext instanceof ReceivedServerReceipt) {

                        // message has been delivered: check if we have previously stored the server id
                        if (msgId > 0) {
                            ContentValues values = new ContentValues(3);
                            values.put(Messages.MESSAGE_ID, ext.getId());
                            values.put(Messages.STATUS, Messages.STATUS_RECEIVED);
                            values.put(Messages.STATUS_CHANGED, serverTimestamp);
                            cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                                values, selectionOutgoing, null);

                            waitingReceipt.remove(id);
                        }
                        else {
                            Uri msg = Messages.getUri(ext.getId());
                            ContentValues values = new ContentValues(2);
                            values.put(Messages.STATUS, Messages.STATUS_RECEIVED);
                            values.put(Messages.STATUS_CHANGED, serverTimestamp);
                            cr.update(msg, values, selectionOutgoing, null);
                        }

                        // send ack
                        AckServerReceipt receipt = new AckServerReceipt(id);
                        org.jivesoftware.smack.packet.Message ack = new org.jivesoftware.smack.packet.Message(m.getFrom(),
                            org.jivesoftware.smack.packet.Message.Type.chat);
                        ack.addExtension(receipt);

                        sendPacket(ack);
                    }

                    else if (ext instanceof SentServerReceipt) {
                        long now = System.currentTimeMillis();

                        if (msgId > 0) {
                            ContentValues values = new ContentValues(3);
                            values.put(Messages.MESSAGE_ID, ext.getId());
                            values.put(Messages.STATUS, Messages.STATUS_SENT);
                            values.put(Messages.STATUS_CHANGED, now);
                            values.put(Messages.SERVER_TIMESTAMP, now);
                            cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                                values, selectionOutgoing, null);

                            waitingReceipt.remove(id);

                            // we can now release the message center. Hopefully
                            // there will be one hold and one matching release.
                            getIdleHandler().release();
                        }
                        else {
                            Uri msg = Messages.getUri(ext.getId());
                            ContentValues values = new ContentValues(2);
                            values.put(Messages.STATUS, Messages.STATUS_SENT);
                            values.put(Messages.STATUS_CHANGED, now);
                            values.put(Messages.SERVER_TIMESTAMP, now);
                            cr.update(msg, values, selectionOutgoing, null);
                        }
                    }

                    // ack is received after sending a <received/> message
                    else if (ext instanceof AckServerReceipt) {
                        // mark message as confirmed
                        ContentValues values = new ContentValues(1);
                        values.put(Messages.STATUS, Messages.STATUS_CONFIRMED);
                        cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                            values, selectionIncoming, null);

                        waitingReceipt.remove(id);
                    }

                }
            }

            // incoming message
            else {
                String msgId = null;
                if (_ext != null) {
                    ServerReceiptRequest req = (ServerReceiptRequest) _ext;
                    // prepare for ack
                    msgId = req.getId();
                }

                if (msgId == null)
                    msgId = "incoming" + StringUtils.randomString(6);

                String sender = StringUtils.parseName(from);
                String body = m.getBody();

                // create message
                CompositeMessage msg = new CompositeMessage(
                        getContext(),
                        msgId,
                        serverTimestamp,
                        sender,
                        false,
                        Coder.SECURITY_CLEARTEXT
                    );

                PacketExtension _encrypted = m.getExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);

                if (_encrypted != null && _encrypted instanceof E2EEncryption) {
                    E2EEncryption mEnc = (E2EEncryption) _encrypted;
                    byte[] encryptedData = mEnc.getData();

                    // encrypted message
                    msg.setEncrypted(true);
                    msg.setSecurityFlags(Coder.SECURITY_BASIC);

                    if (encryptedData != null) {

                        // decrypt message
                        try {
                            MessageUtils.decryptMessage(getContext(),
                                    getServer(), msg, encryptedData);
                        }

                        catch (Exception exc) {
                            Log.e(MessageCenterService.TAG, "decryption failed", exc);

                            // raw component for encrypted data
                            // reuse security flags
                            msg.clearComponents();
                            msg.addComponent(new RawComponent(encryptedData, true, msg.getSecurityFlags()));
                        }

                    }
                }

                else {

                    // use message body
                    if (body != null)
                        msg.addComponent(new TextComponent(body));

                }

                // out of band data
                PacketExtension _media = m.getExtension(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE);
                if (_media != null && _media instanceof OutOfBandData) {
                    File previewFile = null;

                    OutOfBandData media = (OutOfBandData) _media;
                    String mime = media.getMime();
                    String fetchUrl = media.getUrl();
                    long length = media.getLength();

                    // bits-of-binary for preview
                    PacketExtension _preview = m.getExtension(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE);
                    if (_preview != null && _preview instanceof BitsOfBinary) {
                        BitsOfBinary preview = (BitsOfBinary) _preview;
                        String previewMime = preview.getType();
                        if (previewMime == null)
                            previewMime = MediaStorage.THUMBNAIL_MIME;

                        String filename = null;

                        if (ImageComponent.supportsMimeType(mime)) {
                            filename = ImageComponent.buildMediaFilename(msgId, previewMime);
                        }

                        else if (VCardComponent.supportsMimeType(mime)) {
                            filename = VCardComponent.buildMediaFilename(msgId, previewMime);
                        }

                        try {
                            if (filename != null) previewFile =
                                MediaStorage.writeInternalMedia(getContext(),
                                    filename, preview.getContents());
                        }
                        catch (IOException e) {
                            Log.w(MessageCenterService.TAG, "error storing thumbnail", e);
                        }
                    }

                    MessageComponent<?> attachment = null;

                    if (ImageComponent.supportsMimeType(mime)) {
                        // cleartext only for now
                        attachment = new ImageComponent(mime, previewFile, null, fetchUrl, length,
                                false, Coder.SECURITY_CLEARTEXT);
                    }

                    else if (VCardComponent.supportsMimeType(mime)) {
                        // cleartext only for now
                        attachment = new VCardComponent(previewFile, null, fetchUrl, length,
                                false, Coder.SECURITY_CLEARTEXT);
                    }

                    // TODO other types

                    if (attachment != null)
                        msg.addComponent(attachment);

                    // add a dummy body if none was found
                    /*
                    if (body == null) {
                        msg.addComponent(new TextComponent(CompositeMessage
                            .getSampleTextContent((Class<? extends MessageComponent<?>>)
                                attachment.getClass(), mime)));
                    }
                    */

                }

                if (msg != null) {

                    Uri msgUri = incoming(msg);
                    if (_ext != null) {
                        // send ack :)
                        ReceivedServerReceipt receipt = new ReceivedServerReceipt(msgId);
                        org.jivesoftware.smack.packet.Message ack =
                            new org.jivesoftware.smack.packet.Message(from,
                                org.jivesoftware.smack.packet.Message.Type.chat);
                        ack.addExtension(receipt);

                        if (msgUri != null) {
                            // will mark this message as confirmed
                            long storageId = ContentUris.parseId(msgUri);
                            waitingReceipt.put(ack.getPacketID(), storageId);
                        }
                        sendPacket(ack);
                    }
                }

            }
        }

        // error message
        else if (m.getType() == org.jivesoftware.smack.packet.Message.Type.error) {
            synchronized (waitingReceipt) {
                String id = m.getPacketID();
                Long _msgId = waitingReceipt.get(id);
                long msgId = (_msgId != null) ? _msgId : 0;
                ContentResolver cr = getContext().getContentResolver();

                // message has been rejected: mark as error
                if (msgId > 0) {
                    ContentValues values = new ContentValues(3);
                    values.put(Messages.STATUS, Messages.STATUS_NOTDELIVERED);
                    values.put(Messages.STATUS_CHANGED, System.currentTimeMillis());
                    cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                        values, selectionOutgoing, null);

                    waitingReceipt.remove(id);

                    // we can now release the message center. Hopefully
                    // there will be one hold and one matching release.
                    getIdleHandler().release();
                }
            }
        }
    }
}

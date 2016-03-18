/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.kontalk.Kontalk;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.E2EEncryption;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.GroupExtension;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.client.OutOfBandData;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.DecryptException;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.data.Contact;
import org.kontalk.data.GroupInfo;
import org.kontalk.message.AudioComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupComponent;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.RawComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.UsersProvider;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.XMPPUtils;

import static org.kontalk.crypto.DecryptException.DECRYPT_EXCEPTION_INVALID_TIMESTAMP;
import static org.kontalk.service.msgcenter.MessageCenterService.ACTION_MESSAGE;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;


/**
 * Packet listener for message stanzas.
 * @author Daniele Ricci
 */
class MessageListener extends MessageCenterPacketListener {

    private static final String selectionOutgoing = Messages.DIRECTION + "=" + Messages.DIRECTION_OUT;
    private static final String selectionIngoing = Messages.DIRECTION + "=" + Messages.DIRECTION_IN;

    public MessageListener(MessageCenterService instance) {
        super(instance);
    }

    public void processGroupMessage(KontalkGroupManager.KontalkGroup group, Stanza packet, CompositeMessage msg)
            throws SmackException.NotConnectedException {

        if (group.checkRequest(packet)) {
            GroupExtension ext = GroupExtension.from(packet);
            String groupJid = ext.getJID();
            String subject = ext.getSubject();
            String[] members = flattenMembers(ext);
            GroupInfo groupInfo = new GroupInfo(groupJid, subject, members);
            msg.addComponent(new GroupComponent(groupInfo));
        }
    }

    private String[] flattenMembers(GroupExtension group) {
        List<GroupExtension.Member> members = group.getMembers();
        String[] users = new String[members.size()+1];
        // the owner is also a member
        users[0] = group.getOwner();
        for (int i = 1; i < users.length; i++) {
            users[i] = members.get(i-1).jid;
        }
        return users;
    }

    @Override
    public void processPacket(Stanza packet) throws SmackException.NotConnectedException {
        Map<String, Long> waitingReceipt = getWaitingReceiptList();

        org.jivesoftware.smack.packet.Message m = (org.jivesoftware.smack.packet.Message) packet;

        if (m.getType() == org.jivesoftware.smack.packet.Message.Type.chat) {
            Intent i = new Intent(ACTION_MESSAGE);
            String from = m.getFrom();

            // check if there is a composing notification
            ExtensionElement _chatstate = m.getExtension("http://jabber.org/protocol/chatstates");
            ChatStateExtension chatstate = null;
            if (_chatstate != null) {
                chatstate = (ChatStateExtension) _chatstate;
                Contact.setTyping(from, chatstate.getChatState() == ChatState.composing);

                i.putExtra("org.kontalk.message.chatState", chatstate.getElementName());
            }

            i.putExtra(EXTRA_FROM, from);
            i.putExtra(EXTRA_TO, m.getTo());
            sendBroadcast(i);

            // non-active chat states are not to be processed as messages
            if (chatstate == null || chatstate.getElementName().equals(ChatState.active.name())) {

                // delayed deliver extension is the first the be processed
                // because it's used also in delivery receipts
                Date stamp = XMPPUtils.getStanzaDelay(m);

                long serverTimestamp;
                if (stamp != null)
                    serverTimestamp = stamp.getTime();
                else
                    serverTimestamp = System.currentTimeMillis();

                DeliveryReceipt deliveryReceipt = DeliveryReceipt.from(m);

                // delivery receipt
                if (deliveryReceipt != null) {
                    synchronized (waitingReceipt) {
                        String id = m.getStanzaId();
                        Long _msgId = waitingReceipt.get(id);
                        long msgId = (_msgId != null) ? _msgId : 0;
                        ContentResolver cr = getContext().getContentResolver();

                        // message has been delivered: check if we have previously stored the server id
                        if (msgId > 0) {
                            ContentValues values = new ContentValues(3);
                            values.put(Messages.MESSAGE_ID, deliveryReceipt.getId());
                            values.put(Messages.STATUS, Messages.STATUS_RECEIVED);
                            values.put(Messages.STATUS_CHANGED, serverTimestamp);
                            cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                                values, selectionOutgoing, null);

                            waitingReceipt.remove(id);
                        }
                        else {
                            // FIXME this could lead to fake delivery receipts because message IDs are client-generated
                            Uri msg = Messages.getUri(deliveryReceipt.getId());
                            ContentValues values = new ContentValues(2);
                            values.put(Messages.STATUS, Messages.STATUS_RECEIVED);
                            values.put(Messages.STATUS_CHANGED, serverTimestamp);
                            cr.update(msg, values, selectionOutgoing, null);
                        }
                    }
                }

                // incoming message
                else {
                    String msgId = m.getStanzaId();
                    if (msgId == null)
                        msgId = MessageUtils.messageId();

                    String body = m.getBody();

                    // create message
                    CompositeMessage msg = new CompositeMessage(
                        getContext(),
                        msgId,
                        serverTimestamp,
                        from,
                        false,
                        Coder.SECURITY_CLEARTEXT
                    );

                    ExtensionElement _encrypted = m.getExtension(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE);

                    if (_encrypted != null && _encrypted instanceof E2EEncryption) {
                        E2EEncryption mEnc = (E2EEncryption) _encrypted;
                        byte[] encryptedData = mEnc.getData();

                        // encrypted message
                        msg.setEncrypted(true);
                        msg.setSecurityFlags(Coder.SECURITY_BASIC);

                        if (encryptedData != null) {

                            // decrypt message
                            try {
                                Message innerStanza = decryptMessage(msg, encryptedData);
                                if (innerStanza != null) {
                                    // copy some attributes over
                                    innerStanza.setTo(m.getTo());
                                    innerStanza.setFrom(m.getFrom());
                                    innerStanza.setType(m.getType());
                                    m = innerStanza;
                                }
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
                    ExtensionElement _media = m.getExtension(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE);
                    if (_media instanceof OutOfBandData) {
                        File previewFile = null;

                        OutOfBandData media = (OutOfBandData) _media;
                        String mime = media.getMime();
                        String fetchUrl = media.getUrl();
                        long length = media.getLength();
                        boolean encrypted = media.isEncrypted();

                        // bits-of-binary for preview
                        ExtensionElement _preview = m.getExtension(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE);
                        if (_preview != null && _preview instanceof BitsOfBinary) {
                            BitsOfBinary preview = (BitsOfBinary) _preview;
                            String previewMime = preview.getType();
                            if (previewMime == null)
                                previewMime = MediaStorage.THUMBNAIL_MIME_NETWORK;

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
                                encrypted, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
                        }

                        else if (VCardComponent.supportsMimeType(mime)) {
                            // cleartext only for now
                            attachment = new VCardComponent(previewFile, null, fetchUrl, length,
                                encrypted, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
                        }

                        else if (AudioComponent.supportsMimeType(mime)) {
                            attachment = new AudioComponent(mime, null, fetchUrl, length,
                                encrypted, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
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

                    // group chat
                    KontalkGroupManager.KontalkGroup group = KontalkGroupManager
                        .getInstanceFor(getConnection()).getGroup(m);
                    if (group != null) {
                        processGroupMessage(group, m, msg);
                    }

                    Uri msgUri = incoming(msg);

                    if (m.hasExtension(DeliveryReceiptRequest.ELEMENT, DeliveryReceipt.NAMESPACE)) {
                        // send ack :)
                        sendReceipt(msgUri, msgId, from, waitingReceipt);
                    }

                }
            }
        }

        // error message
        else if (m.getType() == org.jivesoftware.smack.packet.Message.Type.error) {
            DeliveryReceipt deliveryReceipt = DeliveryReceipt.from(m);

            // delivery receipt error
            if (deliveryReceipt != null) {
                // mark indicated message as incoming and try again
                Uri msg = Messages.getUri(deliveryReceipt.getId());
                ContentValues values = new ContentValues(2);
                values.put(Messages.STATUS, Messages.STATUS_INCOMING);
                values.put(Messages.STATUS_CHANGED, System.currentTimeMillis());
                getContext().getContentResolver()
                    .update(msg, values, selectionIngoing, null);

                // send receipt again
                sendReceipt(null, deliveryReceipt.getId(), m.getFrom(), waitingReceipt);
            }

            synchronized (waitingReceipt) {
                String id = m.getStanzaId();
                Long _msgId = waitingReceipt.get(id);
                long msgId = (_msgId != null) ? _msgId : 0;
                ContentResolver cr = getContext().getContentResolver();

                // message has been rejected: mark as error
                if (msgId > 0) {
                    ContentValues values = new ContentValues(2);
                    values.put(Messages.STATUS, Messages.STATUS_NOTDELIVERED);
                    values.put(Messages.STATUS_CHANGED, System.currentTimeMillis());
                    cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                        values, selectionOutgoing, null);

                    waitingReceipt.remove(id);

                    // we can now release the message center. Hopefully
                    // there will be one hold and one matching release.
                    getIdleHandler().release();
                }
                else if (id != null) {
                    // FIXME this could lead to fake delivery receipts because message IDs are client-generated
                    Uri msg = Messages.getUri(id);
                    ContentValues values = new ContentValues(2);
                    values.put(Messages.STATUS, Messages.STATUS_NOTDELIVERED);
                    values.put(Messages.STATUS_CHANGED, System.currentTimeMillis());
                    cr.update(msg, values, selectionOutgoing, null);
                }
            }
        }

        // we saved the message, restore SM ack
        resumeSmAck();
    }

    private void sendReceipt(Uri msgUri, String msgId, String from, Map<String, Long> waitingReceipt) {
        DeliveryReceipt receipt = new DeliveryReceipt(msgId);
        org.jivesoftware.smack.packet.Message ack =
            new org.jivesoftware.smack.packet.Message(from,
                org.jivesoftware.smack.packet.Message.Type.chat);
        ack.addExtension(receipt);

        if (msgUri != null) {
            // hold on to message center
            getIdleHandler().hold(false);
            // will mark this message as confirmed
            long storageId = ContentUris.parseId(msgUri);
            waitingReceipt.put(ack.getStanzaId(), storageId);
        }
        sendPacket(ack);
    }

    private Message decryptMessage(CompositeMessage msg, byte[] encryptedData) throws Exception {
        // message stanza
        Message m = null;

        try {
            Context context = getContext();
            PersonalKey key = Kontalk.get(context).getPersonalKey();

            EndpointServer server = getServer();
            if (server == null)
                server = Preferences.getEndpointServer(context);

            Coder coder = UsersProvider.getDecryptCoder(context, server, key, msg.getSender(true));

            // decrypt
            Coder.DecryptOutput result = coder.decryptText(encryptedData, true);

            String contentText;

            if (XMPPUtils.XML_XMPP_TYPE.equalsIgnoreCase(result.mime)) {
                m = XMPPUtils.parseMessageStanza(result.cleartext);

                if (result.timestamp != null && !checkDriftedDelay(m, result.timestamp))
                    result.errors.add(new DecryptException(DECRYPT_EXCEPTION_INVALID_TIMESTAMP,
                        "Drifted timestamp"));

                contentText = m.getBody();
            }
            else {
                contentText = result.cleartext;
            }

            // clear componenets (we are adding new ones)
            msg.clearComponents();
            // decrypted text
            if (contentText != null)
                msg.addComponent(new TextComponent(contentText));

            if (result.errors.size() > 0) {

                int securityFlags = msg.getSecurityFlags();

                for (DecryptException err : result.errors) {

                    int code = err.getCode();
                    switch (code) {

                        case DecryptException.DECRYPT_EXCEPTION_INTEGRITY_CHECK:
                            securityFlags |= Coder.SECURITY_ERROR_INTEGRITY_CHECK;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_VERIFICATION_FAILED:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_SIGNATURE;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_DATA:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_DATA;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_SENDER:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_SENDER;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_RECIPIENT:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_RECIPIENT;
                            break;

                        case DecryptException.DECRYPT_EXCEPTION_INVALID_TIMESTAMP:
                            securityFlags |= Coder.SECURITY_ERROR_INVALID_TIMESTAMP;
                            break;

                    }

                }

                msg.setSecurityFlags(securityFlags);
            }

            msg.setEncrypted(false);

            return m;
        }
        catch (Exception exc) {
            // pass over the message even if encrypted
            // UI will warn the user about that and wait
            // for user decisions
            int securityFlags = msg.getSecurityFlags();

            if (exc instanceof DecryptException) {

                int code = ((DecryptException) exc).getCode();
                switch (code) {

                    case DecryptException.DECRYPT_EXCEPTION_DECRYPT_FAILED:
                    case DecryptException.DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND:
                        securityFlags |= Coder.SECURITY_ERROR_DECRYPT_FAILED;
                        break;

                    case DecryptException.DECRYPT_EXCEPTION_INTEGRITY_CHECK:
                        securityFlags |= Coder.SECURITY_ERROR_INTEGRITY_CHECK;
                        break;

                    case DecryptException.DECRYPT_EXCEPTION_INVALID_DATA:
                        securityFlags |= Coder.SECURITY_ERROR_INVALID_DATA;
                        break;

                }

                msg.setSecurityFlags(securityFlags);
            }

            throw exc;
        }
    }

    private static boolean checkDriftedDelay(Message m, Date expected) {
        Date stamp = XMPPUtils.getStanzaDelay(m);
        if (stamp != null) {
            long time = stamp.getTime();
            long now = expected.getTime();
            long diff = Math.abs(now - time);
            return (diff < Coder.TIMEDIFF_THRESHOLD);
        }

        // no timestamp found
        return true;
    }

}

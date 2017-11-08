/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.annotation.NonNull;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.stringprep.XmppStringprepException;
import org.kontalk.Kontalk;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.message.CompositeMessage;
import org.kontalk.service.msgcenter.MessageCenterService.IdleConnectionHandler;

import java.lang.ref.WeakReference;
import java.util.Map;

import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_FROM;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_PACKET_ID;
import static org.kontalk.service.msgcenter.MessageCenterService.EXTRA_TO;


/**
 * Packet listener for the Message Center.
 * @author Daniele Ricci
 */
abstract class MessageCenterPacketListener implements StanzaListener {
    protected static final String TAG = MessageCenterService.TAG;

    private WeakReference<MessageCenterService> mInstance;

    MessageCenterPacketListener(MessageCenterService instance) {
        mInstance = new WeakReference<>(instance);
    }

    protected MessageCenterService getInstance() {
        return mInstance.get();
    }

    protected Context getContext() {
        return mInstance.get();
    }

    protected Kontalk getApplication() {
        return (Kontalk) mInstance.get().getApplicationContext();
    }

    protected KontalkConnection getConnection() {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.mConnection : null;
    }

    protected EndpointServer getServer() {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.mServer : null;
    }

    protected String getMyUsername() {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.mMyUsername : null;
    }

    protected RosterEntry getRosterEntry(Jid jid) {
        return getRosterEntry(jid.asBareJid());
    }

    protected RosterEntry getRosterEntry(BareJid jid) {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.getRosterEntry(jid) : null;
    }

    @Deprecated
    protected RosterEntry getRosterEntry(String jid) throws XmppStringprepException {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.getRosterEntry(jid) : null;
    }

    protected void queueTask(Runnable task) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.queueTask(task);
    }

    protected void sendBroadcast(Intent intent) {
        MessageCenterService instance = mInstance.get();
        if (instance != null && instance.isStarted())
            instance.mLocalBroadcastManager.sendBroadcast(intent);
    }

    protected void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        MessageCenterService instance = mInstance.get();
        if (instance != null && instance.isStarted())
            instance.mLocalBroadcastManager.registerReceiver(receiver, filter);
    }

    protected void unregisterReceiver(BroadcastReceiver receiver) {
        MessageCenterService instance = mInstance.get();
        if (instance != null && instance.isStarted())
            instance.mLocalBroadcastManager.unregisterReceiver(receiver);
    }

    protected void sendPacket(Stanza packet) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.sendPacket(packet);
    }

    protected void sendPacket(Stanza packet, boolean bumpIdle) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.sendPacket(packet, bumpIdle);
    }

    protected void addUploadService(IUploadService service) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.addUploadService(service);
    }

    protected void addUploadService(IUploadService service, int priority) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.addUploadService(service, priority);
    }

    protected void resendPendingMessages(final boolean retrying, final boolean forcePending) {
        final MessageCenterService instance = mInstance.get();
        if (instance != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    instance.resendPendingMessages(retrying, forcePending);
                }
            });
        }
    }

    protected void resendPending(final boolean retrying, final boolean forcePending, final String to) {
        final MessageCenterService instance = mInstance.get();
        if (instance != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    instance.resendPendingMessages(retrying, forcePending, to);
                    instance.resendPendingReceipts();
                }
            });
        }
    }

    protected boolean isPushNotificationsEnabled() {
        MessageCenterService instance = mInstance.get();
        return instance != null && instance.mPushNotifications;
    }

    protected void setPushSenderId(String senderId) {
        MessageCenterService.sPushSenderId = senderId;
    }

    protected IPushListener getPushListener() {
        return MessageCenterService.sPushListener;
    }

    protected void startPushRegistrationCycle() {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.mPushRegistrationCycle = true;
    }

    protected void pushRegister() {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.pushRegister();
    }

    protected Map<String, Long> getWaitingReceiptList() {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.mWaitingReceipt : null;
    }

    protected Uri incoming(CompositeMessage msg) {
        Context context = getContext();
        return (context != null) ? Kontalk
            .getMessagesController(context).incoming(msg) : null;
    }

    protected IdleConnectionHandler getIdleHandler() {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.mIdleHandler: null;
    }

    protected void hold(boolean activate) {
        IdleConnectionHandler handler = getIdleHandler();
        if (handler != null)
            handler.hold(activate);
    }

    protected void release() {
        IdleConnectionHandler handler = getIdleHandler();
        if (handler != null)
            handler.release();
    }

    protected void runOnUiThread(Runnable action) {
        MessageCenterService instance = mInstance.get();
        if (instance != null && instance.mHandler != null)
            instance.mHandler.post(action);
    }

    protected void endKeyPairRegeneration() {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.endKeyPairRegeneration();
    }

    protected void endKeyPairImport() {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.endKeyPairImport();
    }

    /**
     * Prepare an intent with common stanza parameters.
     * @param packet the stanza
     * @return a prepared intent
     */
    protected Intent prepareIntent(@NonNull Stanza packet, @NonNull String action) {
        Intent i = new Intent(action);
        i.putExtra(EXTRA_PACKET_ID, packet.getStanzaId());
        if (packet.getFrom() != null)
            i.putExtra(EXTRA_FROM, packet.getFrom().toString());
        if (packet.getTo() != null)
            i.putExtra(EXTRA_TO, packet.getTo().toString());
        return i;
    }
}

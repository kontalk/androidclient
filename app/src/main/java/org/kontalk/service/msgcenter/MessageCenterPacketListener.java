/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.lang.ref.WeakReference;

import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.CheckResult;
import android.support.annotation.Nullable;

import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.service.msgcenter.MessageCenterService.IdleConnectionHandler;
import org.kontalk.util.WakefulHashSet;


/**
 * Packet listener for the Message Center.
 * @author Daniele Ricci
 */
public abstract class MessageCenterPacketListener implements StanzaListener {
    protected static final String TAG = MessageCenterService.TAG;

    private WeakReference<MessageCenterService> mInstance;
    private WeakReference<KontalkConnection> mConnection;

    MessageCenterPacketListener(MessageCenterService instance) {
        mInstance = new WeakReference<>(instance);
        mConnection = new WeakReference<>(instance.mConnection);
    }

    protected MessageCenterService getInstance() {
        return mInstance.get();
    }

    @Nullable
    protected Context getContext() {
        return mInstance.get();
    }

    protected KontalkConnection getConnection() {
        return mConnection.get();
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

    protected void queueTask(Runnable task) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.queueTask(task);
    }

    /** @deprecated Use service bus. */
    @Deprecated
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

    @CheckResult
    protected boolean sendPacket(Stanza packet) {
        MessageCenterService instance = mInstance.get();
        return instance != null && instance.sendPacket(packet);
    }

    @CheckResult
    protected boolean sendPacket(Stanza packet, boolean bumpIdle) {
        MessageCenterService instance = mInstance.get();
        return instance != null && instance.sendPacket(packet, bumpIdle);
    }

    protected boolean sendMessage(Message message, long databaseId) {
        MessageCenterService instance = mInstance.get();
        return instance != null && instance.sendMessage(message, databaseId);
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

    protected WakefulHashSet<Long> getWaitingReceiptList() {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.mWaitingReceipt : null;
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
}

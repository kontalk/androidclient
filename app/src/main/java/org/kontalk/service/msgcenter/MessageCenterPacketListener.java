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

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.RosterEntry;

import org.kontalk.Kontalk;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.message.CompositeMessage;
import org.kontalk.service.msgcenter.MessageCenterService.IdleConnectionHandler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;


/**
 * Packet listener for the Message Center.
 * @author Daniele Ricci
 */
abstract class MessageCenterPacketListener implements StanzaListener {

    private WeakReference<MessageCenterService> mInstance;

    MessageCenterPacketListener(MessageCenterService instance) {
        mInstance = new WeakReference<MessageCenterService>(instance);
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

    protected RosterEntry getRosterEntry(String jid) {
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

    protected void initUploadServices() {
        MessageCenterService instance = mInstance.get();
        if (instance != null) {
            if (instance.mUploadServices == null)
                instance.mUploadServices = new HashMap<String, String>();
            else
                instance.mUploadServices.clear();
        }
    }

    protected void setUploadService(String name, String url) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.mUploadServices.put(name, url);
    }

    protected void resendPendingMessages(boolean retrying, boolean forcePending) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            instance.resendPendingMessages(retrying, forcePending);
    }

    protected void resendPending(boolean retrying, boolean forcePending, String to) {
        MessageCenterService instance = mInstance.get();
        if (instance != null) {
            instance.resendPendingMessages(retrying, forcePending, to);
            instance.resendPendingReceipts();
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
        MessageCenterService instance = mInstance.get();
        if (instance != null)
            return instance.incoming(msg);

        return null;
    }

    protected IdleConnectionHandler getIdleHandler() {
        MessageCenterService instance = mInstance.get();
        return (instance != null) ? instance.mIdleHandler: null;
    }

    protected void runOnUiThread(Runnable action) {
        MessageCenterService instance = mInstance.get();
        if (instance != null)
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

    protected void resumeSmAck() {
        MessageCenterService instance = mInstance.get();
        if (instance != null && instance.mConnection != null) {
            try {
                instance.mConnection.resumeSmAck();
            }
            catch (SmackException ignored) {
                // we don't really care
            }
        }
    }

}

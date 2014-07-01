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
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.delay.packet.DelayInfo;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.kontalk.BuildConfig;
import org.kontalk.GCMIntentService;
import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.AckServerReceipt;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.BlockingCommand;
import org.kontalk.client.E2EEncryption;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.client.OutOfBandData;
import org.kontalk.client.Ping;
import org.kontalk.client.PushRegistration;
import org.kontalk.client.RawPacket;
import org.kontalk.client.ReceivedServerReceipt;
import org.kontalk.client.SentServerReceipt;
import org.kontalk.client.ServerReceipt;
import org.kontalk.client.ServerReceiptRequest;
import org.kontalk.client.StanzaGroupExtension;
import org.kontalk.client.SubscribePublicKey;
import org.kontalk.client.UploadExtension;
import org.kontalk.client.UploadInfo;
import org.kontalk.client.VCard4;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PGP.PGPKeyPairRing;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.MessageComponent;
import org.kontalk.message.RawComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Requests;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.KeyPairGeneratorService.KeyGeneratorReceiver;
import org.kontalk.service.KeyPairGeneratorService.PersonalKeyRunnable;
import org.kontalk.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue.IdleHandler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gcm.GCMRegistrar;


/**
 * The Message Center Service.
 * Use {@link Intent}s to deliver commands (via {@link Context#startService}).
 * Service will broadcast intents when certain events occur.
 * @author Daniele Ricci
 * @version 3.0
 */
public class MessageCenterService extends Service implements ConnectionHelperListener {
    private static final String TAG = MessageCenterService.class.getSimpleName();

    static {
        SmackConfiguration.DEBUG_ENABLED = BuildConfig.DEBUG;
    }

    public static final String ACTION_PACKET = "org.kontalk.action.PACKET";
    public static final String ACTION_HOLD = "org.kontalk.action.HOLD";
    public static final String ACTION_RELEASE = "org.kontalk.action.RELEASE";
    public static final String ACTION_RESTART = "org.kontalk.action.RESTART";
    public static final String ACTION_MESSAGE = "org.kontalk.action.MESSAGE";
    public static final String ACTION_PUSH_START = "org.kontalk.push.START";
    public static final String ACTION_PUSH_STOP = "org.kontalk.push.STOP";
    public static final String ACTION_PUSH_REGISTERED = "org.kontalk.push.REGISTERED";

    /** Request roster match. */
    public static final String ACTION_ROSTER = "org.kontalk.action.ROSTER";

    /**
     * Broadcasted when we are connected and authenticated to the server.
     * Send this intent to receive the same as a broadcast if connected. */
    public static final String ACTION_CONNECTED = "org.kontalk.action.CONNECTED";

    /**
     * Broadcasted when a presence stanza is received.
     * Send this intent to broadcast presence.
     */
    public static final String ACTION_PRESENCE = "org.kontalk.action.PRESENCE";

    /**
     * Broadcasted when a last activity iq is received.
     * Send this intent to request a last activity.
     */
    public static final String ACTION_LAST_ACTIVITY = "org.kontalk.action.LAST_ACTIVITY";

    /**
     * Commence key pair regeneration.
     * {@link KeyPairGeneratorService} service will be started to generate the
     * key pair. After that, we will send the public key to the server for
     * verification and signature. Once the server returns the signed public
     * key, it will be installed in the default account.
     */
    public static final String ACTION_REGENERATE_KEYPAIR = "org.kontalk.action.REGEN_KEYPAIR";

    /**
     * Broadcasted when a presence subscription has been accepted.
     * Send this intent to accept a presence subscription.
     */
    public static final String ACTION_SUBSCRIBED = "org.kontalk.action.SUBSCRIBED";

    /**
     * Broadcasted when receiving a vCard.
     * Send this intent to update your own vCard.
     */
    public static final String ACTION_VCARD = "org.kontalk.action.VCARD";

    /**
     * Send this intent to retry to send a pending-user-review message.
     */
    public static final String ACTION_RETRY = "org.kontalk.action.RETRY";

    /**
     * Broadcasted when the blocklist is received.
     * Send this intent to request the blocklist.
     */
    public static final String ACTION_BLOCKLIST = "org.kontalk.action.BLOCKLIST";

    /** Broadcasted when a block request has ben accepted by the server. */
    public static final String ACTION_BLOCKED = "org.kontalk.action.BLOCKED";

    /** Broadcasted when an unblock request has ben accepted by the server. */
    public static final String ACTION_UNBLOCKED = "org.kontalk.action.UNBLOCKED";

    // common parameters
    /** connect to custom server -- TODO not used yet */
    public static final String EXTRA_SERVER = "org.kontalk.server";
    public static final String EXTRA_PACKET_ID = "org.kontalk.packet.id";
    public static final String EXTRA_TYPE = "org.kontalk.packet.type";

    // use with org.kontalk.action.PACKET
    public static final String EXTRA_PACKET = "org.kontalk.packet";
    public static final String EXTRA_PACKET_GROUP = "org.kontalk.packet.group";
    public static final String EXTRA_STAMP = "org.kontalk.packet.delay";

    // use with org.kontalk.action.PRESENCE/SUBSCRIBED
    public static final String EXTRA_FROM = "org.kontalk.stanza.from";
    public static final String EXTRA_FROM_USERID = "org.kontalk.stanza.from.userId";
    public static final String EXTRA_TO = "org.kontalk.stanza.to";
    public static final String EXTRA_TO_USERID = "org.kontalk.stanza.to.userId";
    public static final String EXTRA_STATUS = "org.kontalk.presence.status";
    public static final String EXTRA_SHOW = "org.kontalk.presence.show";
    public static final String EXTRA_PRIORITY = "org.kontalk.presence.priority";
    public static final String EXTRA_GROUP_ID = "org.kontalk.presence.groupId";
    public static final String EXTRA_GROUP_COUNT = "org.kontalk.presence.groupCount";
    public static final String EXTRA_PUSH_REGID = "org.kontalk.presence.push.regId";
    public static final String EXTRA_PRIVACY = "org.kontalk.presence.privacy";

    // use with org.kontalk.action.ROSTER
    public static final String EXTRA_USERLIST = "org.kontalk.roster.userList";
    public static final String EXTRA_JIDLIST = "org.kontalk.roster.JIDList";

    // use with org.kontalk.action.LAST_ACTIVITY
    public static final String EXTRA_SECONDS = "org.kontalk.last.seconds";

    // use with org.kontalk.action.VCARD
    public static final String EXTRA_PUBLIC_KEY = "org.kontalk.vcard.publicKey";

    // used with org.kontalk.action.BLOCKLIST
    public static final String EXTRA_BLOCKLIST = "org.kontalk.blocklist";

    // used for org.kontalk.presence.privacy.action extra
    public static final int PRIVACY_ACCEPT = 0;
    public static final int PRIVACY_BLOCK = 1;
    public static final int PRIVACY_UNBLOCK = 2;

    /** Message URI. */
    public static final String EXTRA_MESSAGE = "org.kontalk.message";

    // other
    public static final String GCM_REGISTRATION_ID = "org.kontalk.GCM_REGISTRATION_ID";

    /** Idle signal. */
    private static final int MSG_IDLE = 1;

    /** How much time before a wakeup alarm triggers. */
    public final static int DEFAULT_WAKEUP_TIME = 900000;
    /** Minimal wakeup time. */
    public final static int MIN_WAKEUP_TIME = 300000;

    /** Push notifications enabled flag. */
    private boolean mPushNotifications;
    /** Server push sender id. This is static so {@link GCMIntentService} can see it. */
    private static String mPushSenderId;
    /** GCM registration id. */
    private String mPushRegistrationId;
    /** Flag marking a currently ongoing GCM registration cycle (unregister/register) */
    private boolean mPushRegistrationCycle;

    private WakeLock mWakeLock;	// created in onCreate
    private LocalBroadcastManager mLocalBroadcastManager;   // created in onCreate

    /** Cached last used server. */
    private EndpointServer mServer;
    /** The connection helper instance. */
    private XMPPConnectionHelper mHelper;
    /** The connection instance. */
    private KontalkConnection mConnection;
    /**
     * My username (account name).
     * @deprecated Remove this if not needed before converting package to org.kontalk
     */
    @Deprecated
    private String mMyUsername;

    /** Supported upload services. */
    private Map<String, String> mUploadServices;

    /** Service handler. */
    private Handler mHandler;

    /** Idle handler. */
    private IdleConnectionHandler mIdleHandler;

    /** Messages waiting for server receipt (packetId: internalStorageId). */
    private Map<String, Long> mWaitingReceipt = new HashMap<String, Long>();

    private RegenerateKeyPairListener mKeyPairRegenerator;

    private static final class IdleConnectionHandler extends Handler implements IdleHandler {
        /** How much time to wait to idle the message center. */
        private final static int DEFAULT_IDLE_TIME = 60000;

        /** A reference to the message center. */
        private WeakReference<MessageCenterService> s;
        /** Reference counter. */
        private int mRefCount;

        public IdleConnectionHandler(MessageCenterService service, Looper looper) {
            super(looper);
            s = new WeakReference<MessageCenterService>(service);

            // set idle handler for the first idle message
            Looper.myQueue().addIdleHandler(this);
        }

        /**
         * Queue idle callback. This gets called just one time to issue the
         * first idle message.
         */
        @Override
        public boolean queueIdle() {
            reset();
            return false;
        }

        @Override
        public void handleMessage(Message msg) {
            MessageCenterService service = s.get();
            boolean consumed = false;
            if (service != null)
                consumed = handleMessage(service, msg);

            if (!consumed)
                super.handleMessage(msg);
        }

        private boolean handleMessage(MessageCenterService service, Message msg) {
            if (msg.what == MSG_IDLE) {
            	// push notifications unavailable: set up an alarm for next time
                if (service.mPushRegistrationId == null) {
                	setWakeupAlarm(service);
                }

                Log.d(TAG, "shutting down message center due to inactivity");
                service.stopSelf();

                return true;
            }

            return false;
        }

        /** Resets the idle timer. */
        public void reset() {
            removeMessages(MSG_IDLE);

            if (mRefCount <= 0 && getLooper().getThread().isAlive()) {
                int time;
                MessageCenterService service = s.get();
                if (service != null)
                    time = Preferences.getIdleTimeMillis(service, 0, DEFAULT_IDLE_TIME);
                else
                    time = DEFAULT_IDLE_TIME;

                // zero means no idle (keep-alive forever)
                if (time > 0)
                	sendMessageDelayed(obtainMessage(MSG_IDLE), time);
            }
        }

        public void hold() {
            mRefCount++;
            post(new Runnable() {
                public void run() {
                    Looper.myQueue().removeIdleHandler(IdleConnectionHandler.this);
                    removeMessages(MSG_IDLE);
                }
            });
        }

        public void release() {
            mRefCount--;
            if (mRefCount <= 0) {
                mRefCount = 0;
                post(new Runnable() {
                    public void run() {
                        removeMessages(MSG_IDLE);
                        Looper.myQueue().addIdleHandler(IdleConnectionHandler.this);
                    }
                });
            }
        }

        public void quit() {
            Looper.myQueue().removeIdleHandler(IdleConnectionHandler.this);
            getLooper().quit();
        }
    }

    @Override
    public void onCreate() {
        SmackAndroid.init(getApplicationContext());
        configure();

        // create the global wake lock
        PowerManager pwr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pwr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Kontalk.TAG);
        mWakeLock.setReferenceCounted(false);

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        // create idle handler
        HandlerThread thread = new HandlerThread("IdleThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mIdleHandler = new IdleConnectionHandler(this, thread.getLooper());
        mHandler = new Handler();
    }

    private void sendPacket(Packet packet) {
        sendPacket(packet, true);
    }

    /**
     * Sends a packet to the connection if found.
     * @param bumpIdle true if the idle handler must be notified of this event
     */
    private void sendPacket(Packet packet, boolean bumpIdle) {
        // reset idler if requested
        if (bumpIdle) mIdleHandler.reset();

        if (mConnection != null) {
            try {
                mConnection.sendPacket(packet);
            }
            catch (NotConnectedException e) {
                // ignored
                Log.v(TAG, "not connected. Dropping packet " + packet);
            }
        }
    }

    private void configure() {
        ProviderManager.addIQProvider(Ping.ELEMENT_NAME, Ping.NAMESPACE, new Ping.Provider());
        ProviderManager.addIQProvider(UploadInfo.ELEMENT_NAME, UploadInfo.NAMESPACE, new UploadInfo.Provider());
        ProviderManager.addIQProvider(VCard4.ELEMENT_NAME, VCard4.NAMESPACE, new VCard4.Provider());
        ProviderManager.addIQProvider(BlockingCommand.BLOCKLIST, BlockingCommand.NAMESPACE, new BlockingCommand.Provider());
        ProviderManager.addExtensionProvider(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE, new StanzaGroupExtension.Provider());
        ProviderManager.addExtensionProvider(SentServerReceipt.ELEMENT_NAME, SentServerReceipt.NAMESPACE, new SentServerReceipt.Provider());
        ProviderManager.addExtensionProvider(ReceivedServerReceipt.ELEMENT_NAME, ReceivedServerReceipt.NAMESPACE, new ReceivedServerReceipt.Provider());
        ProviderManager.addExtensionProvider(ServerReceiptRequest.ELEMENT_NAME, ServerReceiptRequest.NAMESPACE, new ServerReceiptRequest.Provider());
        ProviderManager.addExtensionProvider(AckServerReceipt.ELEMENT_NAME, AckServerReceipt.NAMESPACE, new AckServerReceipt.Provider());
        ProviderManager.addExtensionProvider(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE, new OutOfBandData.Provider());
        ProviderManager.addExtensionProvider(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE, new BitsOfBinary.Provider());
        ProviderManager.addExtensionProvider(SubscribePublicKey.ELEMENT_NAME, SubscribePublicKey.NAMESPACE, new SubscribePublicKey.Provider());
        ProviderManager.addExtensionProvider(E2EEncryption.ELEMENT_NAME, E2EEncryption.NAMESPACE, new E2EEncryption.Provider());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Message Center starting - " + intent);

        handleIntent(intent);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroying message center");
        quit(false);
    }

    private synchronized void quit(boolean restarting) {
        // quit the idle handler
        if (!restarting) {
            mIdleHandler.quit();
            mIdleHandler = null;
        }

        // disable listeners
        if (mHelper != null)
            mHelper.setListener(null);
        if (mConnection != null)
            mConnection.removeConnectionListener(this);

        // abort connection helper (if any)
        if (mHelper != null) {
            // this is because of NetworkOnMainThreadException
            new AbortThread(mHelper).start();
            mHelper = null;
        }

        // disconnect from server (if any)
        if (mConnection != null) {
            // this is because of NetworkOnMainThreadException
            new DisconnectThread(mConnection).start();
            mConnection = null;
        }

        // stop any key pair regeneration service
        endKeyPairRegeneration();

        // release the wakelock
        mWakeLock.release();
    }

    private static final class AbortThread extends Thread {
        private final XMPPConnectionHelper mHelper;
        public AbortThread(XMPPConnectionHelper helper) {
            mHelper = helper;
        }

        @Override
        public void run() {
            try {
                mHelper.shutdown();
            }
            catch (Exception e) {
                // ignored
            }
        }
    }

    private static final class DisconnectThread extends Thread {
        private final XMPPConnection mConn;
        public DisconnectThread(XMPPConnection conn) {
            mConn = conn;
        }

        @Override
        public void run() {
            try {
                mConn.disconnect();
            }
            catch (Exception e) {
                // ignored
            }
        }
    }

    private void handleIntent(Intent intent) {
        boolean offlineMode = isOfflineMode(this);

        // stop immediately
        if (offlineMode)
            stopSelf();

        if (intent != null) {
            String action = intent.getAction();

            // proceed to start only if network is available
            boolean canConnect = isNetworkConnectionAvailable(this) && !offlineMode;
            boolean isConnected = mConnection != null && mConnection.isAuthenticated();
            boolean doConnect = false;

            if (ACTION_PACKET.equals(action)) {
                Object data;
                String[] group = intent.getStringArrayExtra(EXTRA_PACKET_GROUP);
                if (group != null)
                    data = group;
                else
                    data = intent.getStringExtra(EXTRA_PACKET);

                try {
                    for (String pack : group)
                        sendPacket(new RawPacket(pack));
                }
                catch (NullPointerException e) {
                    sendPacket(new RawPacket((String) data));
                }
            }

            else if (ACTION_HOLD.equals(action)) {
                mIdleHandler.hold();
                doConnect = true;
            }

            else if (ACTION_RELEASE.equals(action)) {
                mIdleHandler.release();
            }

            else if (ACTION_PUSH_START.equals(action)) {
                setPushNotifications(true);
            }

            else if (ACTION_PUSH_STOP.equals(action)) {
                setPushNotifications(false);
            }

            else if (ACTION_PUSH_REGISTERED.equals(action)) {
                String regId = intent.getStringExtra(GCM_REGISTRATION_ID);
                // registration cycle under way
                if (regId == null && mPushRegistrationCycle) {
                    mPushRegistrationCycle = false;
                    gcmRegister();
                }
                else
                    setPushRegistrationId(regId);
            }

            else if (ACTION_REGENERATE_KEYPAIR.equals(action)) {
                beginKeyPairRegeneration();
            }

            else if (ACTION_CONNECTED.equals(action)) {
                if (isConnected)
                    broadcast(ACTION_CONNECTED);
            }

            // restart
            else if (ACTION_RESTART.equals(action)) {
                quit(true);
                doConnect = true;
            }

            else if (ACTION_MESSAGE.equals(action)) {
                if (canConnect && isConnected)
                    sendMessage(intent.getExtras());
            }

            else if (ACTION_ROSTER.equals(action)) {
                if (canConnect && isConnected) {
                    String id = intent.getStringExtra(EXTRA_PACKET_ID);
                    String[] list = intent.getStringArrayExtra(EXTRA_USERLIST);
                    int c = list.length;
                    RosterPacket iq = new RosterPacket();
                    iq.setPacketID(id);
                    // iq default type is get

                    for (int i = 0; i < c; i++)
                        iq.addRosterItem(new RosterPacket.Item(list[i] + "@" + mServer.getNetwork(), null));

                    sendPacket(iq);
                }
            }

            else if (ACTION_PRESENCE.equals(action)) {
                if (canConnect && isConnected) {
                    String type = intent.getStringExtra(EXTRA_TYPE);
                    String id = intent.getStringExtra(EXTRA_PACKET_ID);

                    String to;
                    String toUserid = intent.getStringExtra(EXTRA_TO_USERID);
                    if (toUserid != null)
                        to = MessageUtils.toJID(toUserid, mServer.getNetwork());
                    else
                        to = intent.getStringExtra(EXTRA_TO);

                    Packet pack;
                    if ("probe".equals(type)) {
                        /*
                         * Smack doesn't support probe stanzas so we have to
                         * create it manually.
                         */
                        String probe = String.format("<presence type=\"probe\" to=\"%s\" id=\"%s\"/>", to, id);
                        pack = new RawPacket(probe);
                    }
                    else {
                        String show = intent.getStringExtra(EXTRA_SHOW);
                        Presence p = new Presence(type != null ? Presence.Type.valueOf(type) : Presence.Type.available);
                        p.setPacketID(id);
                        p.setTo(to);
                        if (intent.hasExtra(EXTRA_PRIORITY))
                            p.setPriority(intent.getIntExtra(EXTRA_PRIORITY, 0));
                        p.setStatus(intent.getStringExtra(EXTRA_STATUS));
                        if (show != null)
                            p.setMode(Presence.Mode.valueOf(show));

                        String regId = intent.getStringExtra(EXTRA_PUSH_REGID);
                        if (!TextUtils.isEmpty(regId))
                            p.addExtension(new PushRegistration(regId));

                        pack = p;
                    }

                    sendPacket(pack);
                }
            }

            else if (ACTION_LAST_ACTIVITY.equals(action)) {
                if (canConnect && isConnected) {
                    LastActivity p = new LastActivity();

                    String to;
                    String toUserid = intent.getStringExtra(EXTRA_TO_USERID);
                    if (toUserid != null)
                        to = MessageUtils.toJID(toUserid, mServer.getNetwork());
                    else
                        to = intent.getStringExtra(EXTRA_TO);

                    p.setPacketID(intent.getStringExtra(EXTRA_PACKET_ID));
                    p.setTo(to);

                    sendPacket(p);
                }
            }

            else if (ACTION_SUBSCRIBED.equals(action)) {
            	if (canConnect && isConnected) {

            		String to;
                    String toUserid = intent.getStringExtra(EXTRA_TO_USERID);
                    if (toUserid != null)
                        to = MessageUtils.toJID(toUserid, mServer.getNetwork());
                    else
                        to = intent.getStringExtra(EXTRA_TO);

                    // FIXME taking toUserid for granted
                    sendSubscriptionReply(toUserid,
                    		intent.getStringExtra(EXTRA_PACKET_ID),
                    		intent.getIntExtra(EXTRA_PRIVACY, PRIVACY_ACCEPT));
            	}
            }

            else if (ACTION_RETRY.equals(action)) {

            	Uri msgUri = intent.getParcelableExtra(EXTRA_MESSAGE);

            	boolean encrypted = Preferences.getEncryptionEnabled(this);

                ContentValues values = new ContentValues(2);
                values.put(Messages.STATUS, Messages.STATUS_SENDING);
                values.put(Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
                getContentResolver().update(msgUri, values, null, null);

                // FIXME shouldn't we resend just the above message?

            	// already connected: resend pending messages
            	if (isConnected)
            		resendPendingMessages(false);
            }

            else if (ACTION_BLOCKLIST.equals(action)) {
            	if (isConnected)
            		requestBlocklist();
            }

            else {
                // no command means normal service start, connect if not connected
                doConnect = true;
            }

            if (canConnect && doConnect)
                createConnection();
        }
        else {
            Log.v(TAG, "restarting after service crash");
            start(getApplicationContext());
        }
    }

    /** Creates a connection to server if needed. */
    private synchronized void createConnection() {
        if (mConnection == null && mHelper == null) {
            mConnection = null;

            // acquire the wakelock
            mWakeLock.acquire();

            // reset push notification variable
            mPushNotifications = Preferences.getPushNotificationsEnabled(this);
            // reset waiting messages
            mWaitingReceipt.clear();

            // retrieve account name
            Account acc = Authenticator.getDefaultAccount(this);
            mMyUsername = (acc != null) ? acc.name : null;

            // get server from preferences
            mServer = Preferences.getEndpointServer(this);

            mHelper = new XMPPConnectionHelper(this, mServer, false);
            mHelper.setListener(this);
            mHelper.start();
        }
    }

    @Override
    public void connectionClosed() {
        Log.v(TAG, "connection closed");
    }

    @Override
    public void connectionClosedOnError(Exception error) {
        Log.w(TAG, "connection closed with error", error);
        quit(true);
        createConnection();
    }

    @Override
    public void reconnectingIn(int seconds) {
        Log.v(TAG, "reconnecting in " + seconds + " seconds");
    }

    @Override
    public void reconnectionFailed(Exception error) {
        Log.w(TAG, "reconnection failed", error);
    }

    @Override
    public void reconnectionSuccessful() {
        // not used
    }

    @Override
    public void aborted(Exception e) {
        // unrecoverable error - exit
        stopSelf();
    }

    @Override
    public synchronized void created(XMPPConnection connection) {
        Log.v(TAG, "connection created.");
        mConnection = (KontalkConnection) connection;

        // we want to manually handle roster stuff
        mConnection.getRoster().setSubscriptionMode(SubscriptionMode.manual);

        PacketFilter filter;

        filter = new PacketTypeFilter(Ping.class);
        mConnection.addPacketListener(new PingListener(), filter);

        filter = new PacketTypeFilter(Presence.class);
        mConnection.addPacketListener(new PresenceListener(), filter);

        filter = new PacketTypeFilter(RosterPacket.class);
        mConnection.addPacketListener(new RosterListener(), filter);

        filter = new PacketTypeFilter(org.jivesoftware.smack.packet.Message.class);
        mConnection.addPacketListener(new MessageListener(), filter);

        filter = new PacketTypeFilter(LastActivity.class);
        mConnection.addPacketListener(new LastActivityListener(), filter);

        filter = new PacketTypeFilter(VCard4.class);
        mConnection.addPacketListener(new VCardListener(), filter);
    }

    @Override
    public void connected(XMPPConnection connection) {
        // not used.
    }

    @Override
    public void authenticated(XMPPConnection connection) {
        Log.v(TAG, "authenticated!");
        // discovery
        discovery();
        // send presence
        sendPresence();
        // resend failed and pending messages
        resendPendingMessages(false);
        // resend failed and pending received receipts
        resendPendingReceipts();
        // send pending subscription replies
        sendPendingSubscriptionReplies();

        // helper is not needed any more
        mHelper = null;

        broadcast(ACTION_CONNECTED);

        // we can now release any pending push notification
        Preferences.setLastPushNotification(this, -1);

        // release the wakelock
        mWakeLock.release();
    }

    private void broadcast(String action) {
        broadcast(action, null, null);
    }

    private void broadcast(String action, String extraName, String extraValue) {
        Intent i = new Intent(action);
        if (extraName != null)
        	i.putExtra(extraName, extraValue);

        mLocalBroadcastManager.sendBroadcast(i);
    }

    /** Discovers info and items. */
    private void discovery() {
        DiscoverInfo info = new DiscoverInfo();
        info.setTo(mServer.getNetwork());

        PacketFilter filter = new PacketIDFilter(info.getPacketID());
        mConnection.addPacketListener(new DiscoverInfoListener(), filter);
        sendPacket(info);
    }

    /** Sends our initial presence. */
    private void sendPresence() {
        String status = Preferences.getStatusMessage(this);
        Presence p = new Presence(Presence.Type.available);
        if (status != null)
            p.setStatus(status);

        sendPacket(p);
    }

    /**
     * Queries for pending messages and send them through.
     * @param retrying if true, we are retrying to send media messages after
     * receiving upload info (non-media messages will be filtered out)
     */
    private void resendPendingMessages(boolean retrying) {
        StringBuilder filter = new StringBuilder()
            .append(Messages.DIRECTION)
            .append('=')
            .append(Messages.DIRECTION_OUT)
            .append(" AND ")
            .append(Messages.STATUS)
            .append("<>")
            .append(Messages.STATUS_SENT)
            .append(" AND ")
            .append(Messages.STATUS)
            .append("<>")
            .append(Messages.STATUS_RECEIVED)
            .append(" AND ")
            .append(Messages.STATUS)
            .append("<>")
            .append(Messages.STATUS_NOTDELIVERED)
            .append(" AND ")
            .append(Messages.STATUS)
            .append("<>")
            .append(Messages.STATUS_PENDING);

        // filter out non-media non-uploaded messages
        if (retrying) filter
            .append(" AND ")
            .append(Messages.ATTACHMENT_FETCH_URL)
            .append(" IS NULL AND ")
            .append(Messages.ATTACHMENT_LOCAL_URI)
            .append(" IS NOT NULL");

        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
            new String[] {
                Messages._ID,
                Messages.PEER,
                Messages.BODY_CONTENT,
                Messages.SECURITY_FLAGS,
                Messages.ATTACHMENT_MIME,
                Messages.ATTACHMENT_LOCAL_URI,
                Messages.ATTACHMENT_FETCH_URL,
                Messages.ATTACHMENT_PREVIEW_PATH,
                Messages.ATTACHMENT_LENGTH,
                // TODO Messages.ATTACHMENT_SECURITY_FLAGS,
            },
            filter.toString(),
            null, Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String userId = c.getString(1);
            byte[] textContent = c.getBlob(2);
            int securityFlags = c.getInt(3);
            String attMime = c.getString(4);
            String attFileUri = c.getString(5);
            String attFetchUrl = c.getString(6);
            String attPreviewPath = c.getString(7);
            long attLength = c.getLong(8);
            // TODO int attSecurityFlags = c.getInt(9);

            // media message encountered and no upload service available - delay message
            if (attFileUri != null && attFetchUrl == null && getUploadService() == null && !retrying) {
                Log.w(TAG, "no upload info received yet, delaying media message");
                continue;
            }

            Bundle b = new Bundle();

            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.toUser", userId);
            // TODO shouldn't we pass security flags directly here??
            b.putBoolean("org.kontalk.message.encrypt", securityFlags != Coder.SECURITY_CLEARTEXT);

            if (textContent != null)
                b.putString("org.kontalk.message.body", new String(textContent));

            // message has already been uploaded - just send media
            if (attFetchUrl != null) {
                b.putString("org.kontalk.message.mime", attMime);
                b.putString("org.kontalk.message.fetch.url", attFetchUrl);
                b.putString("org.kontalk.message.preview.uri", attFileUri);
                b.putString("org.kontalk.message.preview.path", attPreviewPath);
            }
            // check if the message contains some large file to be sent
            else if (attFileUri != null) {
                b.putString("org.kontalk.message.mime", attMime);
                b.putString("org.kontalk.message.media.uri", attFileUri);
                b.putString("org.kontalk.message.preview.path", attPreviewPath);
                b.putLong("org.kontalk.message.length", attLength);
            }

            Log.v(TAG, "resending pending message " + id);
            sendMessage(b);
        }

        c.close();
    }

    private void resendPendingReceipts() {
        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
            new String[] {
                Messages._ID,
                Messages.MESSAGE_ID,
                Messages.PEER,
            },
            Messages.DIRECTION + " = " + Messages.DIRECTION_IN + " AND " +
            Messages.STATUS + " = " + Messages.STATUS_RECEIVED,
            null, Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String msgId = c.getString(1);
            String userId = c.getString(2);

            Bundle b = new Bundle();

            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.toUser", userId);
            b.putString("org.kontalk.message.ack", msgId);

            Log.v(TAG, "resending pending receipt for message " + id);
            sendMessage(b);
        }

        c.close();
    }

    private void sendPendingSubscriptionReplies() {
        Cursor c = getContentResolver().query(Threads.CONTENT_URI,
                new String[] {
                    Threads.PEER,
                    Threads.REQUEST_STATUS,
                },
                Threads.REQUEST_STATUS + "=" + Threads.REQUEST_REPLY_PENDING_ACCEPT + " OR " +
                Threads.REQUEST_STATUS + "=" + Threads.REQUEST_REPLY_PENDING_BLOCK,
                null, Threads._ID);

        while (c.moveToNext()) {
            String userId = c.getString(0);
            int reqStatus = c.getInt(1);

            int action;

            switch (reqStatus) {
                case Threads.REQUEST_REPLY_PENDING_ACCEPT:
                    action = PRIVACY_ACCEPT;
                    break;

                case Threads.REQUEST_REPLY_PENDING_BLOCK:
                    action = PRIVACY_BLOCK;
                    break;

                case Threads.REQUEST_REPLY_PENDING_UNBLOCK:
                    action = PRIVACY_UNBLOCK;
                    break;

                default:
                    // skip this one
                    continue;
            }

            sendSubscriptionReply(userId, null, action);
        }

        c.close();
    }

    private void sendSubscriptionReply(String userId, String packetId, int action) {

    	if (action == PRIVACY_ACCEPT) {
            String to = MessageUtils.toJID(userId, mServer.getNetwork());

    		// standard response: subscribed
			Presence p = new Presence(Presence.Type.subscribed);

	        p.setPacketID(packetId);
			p.setTo(to);

			// send the subscribed response
			sendPacket(p);

			// send a subscription request anyway
			p = new Presence(Presence.Type.subscribe);
			p.setTo(to);

			sendPacket(p);
    	}

    	else if (action == PRIVACY_BLOCK || action == PRIVACY_UNBLOCK) {
    	    sendPrivacyListCommand(userId, action);
    	}

		// clear the request status
		ContentValues values = new ContentValues(1);
		values.put(Threads.REQUEST_STATUS, Threads.REQUEST_NONE);

		getContentResolver().update(Requests.CONTENT_URI,
			values, CommonColumns.PEER + "=?", new String[] { userId });
    }

    private void sendPrivacyListCommand(final String userId, final int action) {
        String to = MessageUtils.toJID(userId, mServer.getNetwork());
        IQ p;

        if (action == PRIVACY_BLOCK) {
            // blocking command: block
            p = BlockingCommand.block(to);
        }

        else if (action == PRIVACY_UNBLOCK) {
            // blocking command: block
            p = BlockingCommand.unblock(to);
        }

        else {
            // unsupported action
            throw new IllegalArgumentException("unsupported action: " + action);
        }

        // setup packet filter for response
        PacketFilter filter = new PacketIDFilter(p.getPacketID());
        PacketListener listener = new PacketListener() {
            public void processPacket(Packet packet) {

                if (packet instanceof IQ && ((IQ) packet).getType() == IQ.Type.RESULT) {
                    UsersProvider.setBlockStatus(MessageCenterService.this,
                        userId, action == PRIVACY_BLOCK);

                    // invalidate cached contact
                    Contact.invalidate(userId);

                    // broadcast result
                    broadcast(action == PRIVACY_BLOCK ?
                    	ACTION_BLOCKED : ACTION_UNBLOCKED,
                    	EXTRA_FROM_USERID, userId);
                }

            }
        };
        mConnection.addPacketListener(listener, filter);

        // send IQ
        sendPacket(p);
    }

    private void requestBlocklist() {
    	Packet p = BlockingCommand.blocklist();
    	String packetId = p.getPacketID();

    	// listen for response (TODO cache the listener, it shouldn't change)
    	PacketFilter idFilter = new PacketIDFilter(packetId);
    	mConnection.addPacketListener(new PacketListener() {
			public void processPacket(Packet packet) {
				// we don't need this listener anymore
				mConnection.removePacketListener(this);

				if (packet instanceof BlockingCommand) {
					BlockingCommand blocklist = (BlockingCommand) packet;

					Intent i = new Intent(ACTION_BLOCKLIST);

					List<String> _list = blocklist.getItems();
					if (_list != null) {
						String[] list = new String[_list.size()];
						i.putExtra(EXTRA_BLOCKLIST, _list.toArray(list));
					}

					Log.v(TAG, "broadcasting blocklist: " + i);
					mLocalBroadcastManager.sendBroadcast(i);
				}

			}
		}, idFilter);

    	sendPacket(p);
    }

    private void sendMessage(Bundle data) {

        // check if message is already pending
        long msgId = data.getLong("org.kontalk.message.msgId");
        if (mWaitingReceipt.containsValue(msgId)) {
            Log.v(TAG, "message already queued and waiting - dropping");
            return;
        }

        String mime = data.getString("org.kontalk.message.mime");
        String _mediaUri = data.getString("org.kontalk.message.media.uri");
        if (_mediaUri != null) {
            // take the first available upload service :)
            String postUrl = getUploadService();
            if (postUrl != null) {
                // media message - start upload service
                Uri mediaUri = Uri.parse(_mediaUri);

                // preview file path (if any)
                String previewPath = data.getString("org.kontalk.message.preview.path");

                // start upload intent service
                Intent i = new Intent(this, UploadService.class);
                i.setData(mediaUri);
                i.setAction(UploadService.ACTION_UPLOAD);
                i.putExtra(UploadService.EXTRA_POST_URL, postUrl);
                i.putExtra(UploadService.EXTRA_MESSAGE_ID, msgId);
                i.putExtra(UploadService.EXTRA_MIME, mime);
                i.putExtra(UploadService.EXTRA_PREVIEW_PATH, previewPath);

                // TODO should support JIDs too
                String toUser = data.getString("org.kontalk.message.toUser");
                i.putExtra(UploadService.EXTRA_USER_ID, toUser);
                startService(i);
            }
            else {
                // TODO warn user about this problem
                Log.w(TAG, "no upload service - this shouldn't happen!");
            }
        }

        else {
            // hold on to message center while we send the message
            mIdleHandler.hold();

            // message stanza
            org.jivesoftware.smack.packet.Message m = new org.jivesoftware.smack.packet.Message();
            m.setType(org.jivesoftware.smack.packet.Message.Type.chat);
            String to = data.getString("org.kontalk.message.to");
            if (to == null) {
                to = data.getString("org.kontalk.message.toUser");
                to += '@' + mServer.getNetwork();
            }
            if (to != null) m.setTo(to);

            if (msgId > 0) {
                String id = m.getPacketID();
                mWaitingReceipt.put(id, msgId);
            }

            String body = data.getString("org.kontalk.message.body");
            if (body != null)
            	m.setBody(body);

            boolean encrypt = data.getBoolean("org.kontalk.message.encrypt");
            String fetchUrl = data.getString("org.kontalk.message.fetch.url");

            // generate preview if needed
            String _previewUri = data.getString("org.kontalk.message.preview.uri");
            String previewFilename = data.getString("org.kontalk.message.preview.path");
            if (_previewUri != null && previewFilename != null) {
                File previewPath = new File(previewFilename);
                if (!previewPath.isFile()) {
                    Uri previewUri = Uri.parse(_previewUri);
                    try {
                        MediaStorage.cacheThumbnail(this, previewUri, previewPath);
                    }
                    catch (IOException e) {
                        Log.w(TAG, "unable to generate preview for media", e);
                    }
                }

                m.addExtension(new BitsOfBinary(MediaStorage.THUMBNAIL_MIME, previewPath));
            }

            ChatState chatState;
            try {
                chatState = ChatState.valueOf(data.getString("org.kontalk.message.chatState"));
            }
            catch (Exception e) {
                chatState = null;
            }

            // add download url if present
            if (fetchUrl != null) {
                // in this case we will need the length too
                long length = data.getLong("org.kontalk.message.length");
                m.addExtension(new OutOfBandData(fetchUrl, mime, length));
            }

            if (encrypt) {
                byte[] toMessage = null;
                try {
                    PersonalKey key = ((Kontalk)getApplicationContext()).getPersonalKey();
                    Coder coder = UsersProvider.getEncryptCoder(this, mServer, key, new String[] { to });
                    if (coder != null) {

                    	// no extensions, create a simple text version to save space
                    	if (m.getExtensions().size() == 0) {
                            toMessage = coder.encryptText(body);
                    	}

                    	// some extension, encrypt whole stanza just to be sure
                    	else {
                    		toMessage = coder.encryptStanza(m.toXML());
                    	}

                    	org.jivesoftware.smack.packet.Message encMsg =
	                    		new org.jivesoftware.smack.packet.Message(m.getTo(),
	                    				m.getType());

                    	encMsg.setPacketID(m.getPacketID());
                    	encMsg.addExtension(new E2EEncryption(toMessage));

                    	m = encMsg;
                    }
                }

                // FIXME there is some very ugly code here
                // FIXME notify just once per session (store in Kontalk instance?)

                catch (PGPException pgpe) {
                	// warn user: message will be sent cleartext
                	if (to.equalsIgnoreCase(MessagingNotification.getPaused())) {
                		Toast.makeText(this, R.string.warn_no_personal_key,
                			Toast.LENGTH_LONG).show();
                	}
                }

                catch (IOException io) {
                	// warn user: message will be sent cleartext
                	if (to.equalsIgnoreCase(MessagingNotification.getPaused())) {
                		Toast.makeText(this, R.string.warn_no_personal_key,
                			Toast.LENGTH_LONG).show();
                	}
                }

                catch (IllegalArgumentException noPublicKey) {
                	// warn user: message will be sent cleartext
                	if (to.equalsIgnoreCase(MessagingNotification.getPaused())) {
                		Toast.makeText(this, R.string.warn_no_public_key,
                			Toast.LENGTH_LONG).show();
                	}
                }

                catch (GeneralSecurityException e) {
                	// warn user: message will be sent cleartext
                	if (to.equalsIgnoreCase(MessagingNotification.getPaused())) {
                		Toast.makeText(this, R.string.warn_encryption_failed,
                			Toast.LENGTH_LONG).show();
                	}
                }

                if (toMessage == null) {
                	// message was not encrypted for some reason, mark it pending user review
                    ContentValues values = new ContentValues(1);
                    values.put(Messages.STATUS, Messages.STATUS_PENDING);
                    getContentResolver().update(ContentUris.withAppendedId
                    		(Messages.CONTENT_URI, msgId), values, null, null);

                    // do not send the message
                    return;
                }
            }

            // message server id
            String serverId = data.getString("org.kontalk.message.ack");

            // received receipt
            if (serverId != null) {
                m.addExtension(new ReceivedServerReceipt(serverId));
            }
            else {
                // add chat state if message is not a received receipt
                if (chatState != null)
                	m.addExtension(new ChatStateExtension(chatState));

                // standalone message: no receipt
                if (!data.getBoolean("org.kontalk.message.standalone", false))
                    m.addExtension(new ServerReceiptRequest());
            }

            sendPacket(m);
        }
    }

    /** Process an incoming message. */
    private Uri incoming(CompositeMessage msg) {
        String sender = msg.getSender(true);

        // save to local storage
        ContentValues values = new ContentValues();
        values.put(Messages.MESSAGE_ID, msg.getId());
        values.put(Messages.PEER, sender);

        MessageUtils.fillContentValues(values, msg);

        values.put(Messages.UNREAD, true);
        values.put(Messages.DIRECTION, Messages.DIRECTION_IN);
        values.put(Messages.TIMESTAMP, System.currentTimeMillis());

        Uri msgUri = null;
        try {
            msgUri = getContentResolver().insert(Messages.CONTENT_URI, values);
        }
        catch (SQLiteConstraintException econstr) {
            // duplicated message, skip it
        }

        // mark sender as registered in the users database
        final String userId = msg.getSender(true);
        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                UsersProvider.markRegistered(context, userId);
            }
        }).start();

        if (!sender.equalsIgnoreCase(MessagingNotification.getPaused()))
            // update notifications (delayed)
            MessagingNotification.delayedUpdateMessagesNotification(getApplicationContext(), true);

        return msgUri;
    }

    /** Returns the first available upload service post URL. */
    private String getUploadService() {
        if (mUploadServices != null && mUploadServices.size() > 0) {
            Set<String> keys = mUploadServices.keySet();
            for (String key : keys) {
                String url = mUploadServices.get(key);
                if (url != null)
                    return url;
            }
        }

        return null;
    }

    private void beginKeyPairRegeneration() {
        if (mKeyPairRegenerator == null) {
        	try {
        		mKeyPairRegenerator = new RegenerateKeyPairListener();
        	}
        	catch (Exception e) {
        		Log.e(TAG, "unable to initiate keypair regeneration", e);
        		// TODO warn user
        	}
        }
    }

    private void endKeyPairRegeneration() {
        if (mKeyPairRegenerator != null) {
            mKeyPairRegenerator.abort();
            mKeyPairRegenerator = null;
        }
    }

    /** Checks for network availability. */
    public static boolean isNetworkConnectionAvailable(Context context) {
        final ConnectivityManager cm = (ConnectivityManager) context
            .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm.getBackgroundDataSetting()) {
            NetworkInfo info = cm.getActiveNetworkInfo();
            if (info != null && info.getState() == NetworkInfo.State.CONNECTED)
                return true;
        }

        return false;
    }

    private static boolean isOfflineMode(Context context) {
        return Preferences.getOfflineMode(context);
    }

    private static Intent getStartIntent(Context context) {
    	final Intent intent = new Intent(context, MessageCenterService.class);
        EndpointServer server = Preferences.getEndpointServer(context);
        intent.putExtra(EndpointServer.class.getName(), server.toString());
        return intent;
    }

    public static void start(Context context) {
        // check for offline mode
        if (isOfflineMode(context)) {
            Log.d(TAG, "offline mode enable - abort service start");
            return;
        }

        // check for network state
        if (isNetworkConnectionAvailable(context)) {
            Log.d(TAG, "starting message center");
            final Intent intent = getStartIntent(context);

            context.startService(intent);
        }
        else
            Log.d(TAG, "network not available or background data disabled - abort service start");
    }

    public static void stop(Context context) {
        Log.d(TAG, "shutting down message center");
        context.stopService(new Intent(context, MessageCenterService.class));
    }

    public static void restart(Context context) {
        Log.d(TAG, "restarting message center");
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_RESTART);
        // include server uri if server needs to be started
        EndpointServer server = Preferences.getEndpointServer(context);
        i.putExtra(EXTRA_SERVER, server.toString());
        context.startService(i);
    }

    /**
     * Tells the message center we are holding on to it, preventing shutdown for
     * inactivity.
     */
    public static void hold(final Context context) {
        // increment the application counter
        ((Kontalk) context.getApplicationContext()).hold();

        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_HOLD);
        // include server uri if server needs to be started
        EndpointServer server = Preferences.getEndpointServer(context);
        i.putExtra(EXTRA_SERVER, server.toString());
        context.startService(i);
    }

    /**
     * Tells the message center we are releasing it, allowing shutdown for
     * inactivity.
     */
    public static void release(final Context context) {
        // decrement the application counter
        ((Kontalk) context.getApplicationContext()).release();

        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_RELEASE);
        context.startService(i);
    }

    public static void updateStatus(final Context context) {
        updateStatus(context, GCMRegistrar.getRegistrationId(context));
    }

    /** Broadcasts our presence to the server. */
    public static void updateStatus(final Context context, String pushRegistrationId) {
        // FIXME this is what sendPresence already does
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PRESENCE);
        i.putExtra(EXTRA_STATUS, Preferences.getStatusMessage(context));
        i.putExtra(EXTRA_PUSH_REGID, pushRegistrationId);
        context.startService(i);
    }

    /** Sends a chat state message. */
    public static void sendChatState(final Context context, String userId, ChatState state) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.chatState", state.name());
        i.putExtra("org.kontalk.message.standalone", true);
        context.startService(i);
    }

    /** Sends a text message. */
    public static void sendTextMessage(final Context context, String userId, String text, boolean encrypt, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.mime", TextComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.body", text);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    /** Sends a binary message. */
    public static void sendBinaryMessage(final Context context, String userId, String mime, Uri localUri, long length, String previewPath, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.media.uri", localUri.toString());
        i.putExtra("org.kontalk.message.length", length);
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void sendUploadedMedia(final Context context, String userId,
            String mime, Uri localUri, long length, String previewPath, String fetchUrl, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.preview.uri", localUri.toString());
        i.putExtra("org.kontalk.message.length", length);
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.fetch.url", fetchUrl);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    /** Replies to a presence subscription request. */
    public static void replySubscription(final Context context, String userId, int action) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_SUBSCRIBED);
        i.putExtra(EXTRA_TO_USERID, userId);
        i.putExtra(EXTRA_PRIVACY, action);
        context.startService(i);
    }

    public static void regenerateKeyPair(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_REGENERATE_KEYPAIR);
        context.startService(i);
    }

    public static void requestConnectionStatus(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_CONNECTED);
        context.startService(i);
    }

    /** Starts the push notifications registration process. */
    public static void enablePushNotifications(Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PUSH_START);
        context.startService(i);
    }

    /** Starts the push notifications unregistration process. */
    public static void disablePushNotifications(Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PUSH_STOP);
        context.startService(i);
    }

    /** Caches the given registration Id for use with push notifications. */
    public static void registerPushNotifications(Context context, String registrationId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PUSH_REGISTERED);
        i.putExtra(GCM_REGISTRATION_ID, registrationId);
        context.startService(i);
    }

    public void setPushNotifications(boolean enabled) {
        mPushNotifications = enabled;
        if (mPushNotifications) {
            if (mPushRegistrationId == null)
                gcmRegister();
        }
        else {
            gcmUnregister();
        }
    }

    private void gcmRegister() {
        if (mPushSenderId != null) {
            try {
                GCMRegistrar.checkDevice(this);
                //GCMRegistrar.checkManifest(this);
                // senderId will be given by serverinfo if any
                mPushRegistrationId = GCMRegistrar.getRegistrationId(this);
                if (TextUtils.isEmpty(mPushRegistrationId))
                    // start registration
                    GCMRegistrar.register(this, mPushSenderId);
                else
                    // already registered - send registration id to server
                    setPushRegistrationId(mPushRegistrationId);
            }
            catch (UnsupportedOperationException unsupported) {
                // GCM not supported
            }
            catch (Exception e) {
                // this exception should be reported
                Log.w(TAG, "error setting up GCM", e);
            }

        }
    }

    private void gcmUnregister() {
        if (GCMRegistrar.isRegistered(this))
            // start unregistration
            GCMRegistrar.unregister(this);
        else
            // force unregistration
            setPushRegistrationId(null);
    }

    private void setPushRegistrationId(String regId) {
        mPushRegistrationId = regId;

        // notify the server about the change
        updateStatus(this, mPushRegistrationId);
        GCMRegistrar.setRegisteredOnServer(this, mPushRegistrationId != null);
    }

    public static String getPushSenderId() {
        return mPushSenderId;
    }

    public static void setWakeupAlarm(Context context) {
    	AlarmManager am = (AlarmManager) context
    			.getSystemService(Context.ALARM_SERVICE);

    	long delay = Preferences.getWakeupTimeMillis(context,
    		MIN_WAKEUP_TIME, DEFAULT_WAKEUP_TIME);

    	// start message center pending intent
    	PendingIntent pi = PendingIntent.getService(context
    			.getApplicationContext(), 0, getStartIntent(context),
    			PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);

    	am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
    			SystemClock.elapsedRealtime() + delay, pi);
    }

    private final class PingListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            sendPacket(IQ.createResultIQ((IQ) packet), false);
        }
    }

    private final class DiscoverInfoListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            // we don't need this listener anymore
            mConnection.removePacketListener(this);

            DiscoverInfo query = (DiscoverInfo) packet;
            List<DiscoverInfo.Feature> features = query.getFeatures();
            for (DiscoverInfo.Feature feat : features) {

                /*
                 * TODO do not request info about push if disabled by user.
                 * Of course if user enables push notification we should
                 * reissue this discovery again.
                 */
                if (PushRegistration.NAMESPACE.equals(feat.getVar())) {
                    // push notifications are enabled on this server
                    // request items to check if gcm is supported and obtain the server id
                    DiscoverItems items = new DiscoverItems();
                    items.setNode(PushRegistration.NAMESPACE);
                    items.setTo(mServer.getNetwork());

                    PacketFilter filter = new PacketIDFilter(items.getPacketID());
                    mConnection.addPacketListener(new PushDiscoverItemsListener(), filter);

                    sendPacket(items);
                }

                /*
                 * TODO upload info should be requested only when needed and
                 * cached. This discovery should of course be issued before any
                 * media message gets requeued.
                 * Actually, delay any message from being requeued if at least
                 * 1 media message is present; do the discovery first.
                 */
                else if (UploadExtension.NAMESPACE.equals(feat.getVar())) {
                    // media upload is available on this server
                    // request items to check what services are available
                    DiscoverItems items = new DiscoverItems();
                    items.setNode(UploadExtension.NAMESPACE);
                    items.setTo(mServer.getNetwork());

                    PacketFilter filter = new PacketIDFilter(items.getPacketID());
                    mConnection.addPacketListener(new UploadDiscoverItemsListener(), filter);

                    sendPacket(items);
                }
            }
        }
    }

    private final class UploadDiscoverItemsListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            // we don't need this listener anymore
            mConnection.removePacketListener(this);

            if (mUploadServices == null)
                mUploadServices = new HashMap<String, String>();
            else
                mUploadServices.clear();

            // store available services
            DiscoverItems query = (DiscoverItems) packet;
            List<DiscoverItems.Item> items = query.getItems();
            for (DiscoverItems.Item item : items) {
                String jid = item.getEntityID();
                if ((mServer.getNetwork()).equals(jid)) {
                    mUploadServices.put(item.getNode(), null);

                    // request upload url
                    UploadInfo iq = new UploadInfo(item.getNode());
                    iq.setType(IQ.Type.GET);
                    iq.setTo(mServer.getNetwork());

                    mConnection.addPacketListener(new UploadInfoListener(), new PacketIDFilter(iq.getPacketID()));
                    sendPacket(iq);
                }
            }
        }
    }

    private final class UploadInfoListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            // we don't need this listener anymore
            mConnection.removePacketListener(this);

            UploadInfo info = (UploadInfo) packet;
            String node = info.getNode();
            mUploadServices.put(node, info.getUri());
            Log.v(TAG, "upload info received, node = " + node + ", uri = " + info.getUri());

            // resend pending messages
            resendPendingMessages(true);
        }
    }

    private final class PushDiscoverItemsListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            // we don't need this listener anymore
            mConnection.removePacketListener(this);

            DiscoverItems query = (DiscoverItems) packet;
            List<DiscoverItems.Item> items = query.getItems();
            for (DiscoverItems.Item item : items) {
                String jid = item.getEntityID();
                // google push notifications
                if (("gcm.push." + mServer.getNetwork()).equals(jid)) {
                    mPushSenderId = item.getNode();

                    if (mPushNotifications) {
                        String oldSender = Preferences.getPushSenderId(MessageCenterService.this);

                        // store the new sender id
                        Preferences.setPushSenderId(MessageCenterService.this, mPushSenderId);

                        // begin a registration cycle if senderId is different
                        if (oldSender != null && !oldSender.equals(mPushSenderId)) {
                            GCMRegistrar.unregister(MessageCenterService.this);
                            // unregister will see this as an attempt to register again
                            mPushRegistrationCycle = true;
                        }
                        else {
                            // begin registration immediately
                            gcmRegister();
                        }
                    }
                }
            }
        }
    }

    /** Listener for last activity iq. */
    private final class LastActivityListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            LastActivity p = (LastActivity) packet;
            Intent i = new Intent(ACTION_LAST_ACTIVITY);
            i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

            String from = p.getFrom();
            String network = StringUtils.parseServer(from);
            // our network - convert to userId
            if (network.equalsIgnoreCase(mServer.getNetwork())) {
                StringBuilder b = new StringBuilder();
                b.append(StringUtils.parseName(from));
                b.append(StringUtils.parseResource(from));
                i.putExtra(EXTRA_FROM_USERID, b.toString());
            }

            i.putExtra(EXTRA_FROM, from);
            i.putExtra(EXTRA_TO, p.getTo());
            i.putExtra(EXTRA_SECONDS, p.lastActivity);

            // non-standard stanza group extension
            PacketExtension ext = p.getExtension(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE);
            if (ext != null && ext instanceof StanzaGroupExtension) {
                StanzaGroupExtension g = (StanzaGroupExtension) ext;
                i.putExtra(EXTRA_GROUP_ID, g.getId());
                i.putExtra(EXTRA_GROUP_COUNT, g.getCount());
            }

            Log.v(TAG, "broadcasting presence: " + i);
            mLocalBroadcastManager.sendBroadcast(i);
        }
    }

    /** Listener for roster iq stanzas. */
    private final class RosterListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            RosterPacket p = (RosterPacket) packet;
            Intent i = new Intent(ACTION_ROSTER);
            i.putExtra(EXTRA_FROM, p.getFrom());
            i.putExtra(EXTRA_TO, p.getTo());
            // here we are not using() because Type is a class, not an enum
            i.putExtra(EXTRA_TYPE, p.getType().toString());
            i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

            Collection<RosterPacket.Item> items = p.getRosterItems();
            String[] list = new String[items.size()];

            int index = 0;
            for (Iterator<RosterPacket.Item> iter = items.iterator(); iter.hasNext(); ) {
                RosterPacket.Item item = iter.next();
                list[index] = item.getUser();
                index++;
            }

            i.putExtra(EXTRA_JIDLIST, list);

            mLocalBroadcastManager.sendBroadcast(i);
        }
    }

    /** Listener for vCard4 iq stanzas. */
    private final class VCardListener implements PacketListener {

        @Override
        public void processPacket(Packet packet) {
            VCard4 p = (VCard4) packet;

            // will be true if it's our card
            boolean myCard = false;
            byte[] _publicKey = p.getPGPKey();

            // vcard was requested, store but do not broadcast
            if (p.getType() == IQ.Type.RESULT) {

                if (_publicKey != null) {

                    // FIXME always false LOL
    	            if (myCard) {
    	                byte[] bridgeCertData;
    	                try {
    	                    PersonalKey key = ((Kontalk)getApplicationContext()).getPersonalKey();

    	                    // TODO subjectAltName?
    	                    bridgeCertData = X509Bridge.createCertificate(_publicKey,
    	                        key.getSignKeyPair().getPrivateKey(), null).getEncoded();
    	                }
    	                catch (Exception e) {
    	                    Log.e(TAG, "error decoding key data", e);
    	                    bridgeCertData = null;
    	                }

    	                if (bridgeCertData != null) {
    	                    // store key data in AccountManager
    	                    Authenticator.setDefaultPersonalKey(MessageCenterService.this,
    	                        _publicKey, null, bridgeCertData);
    	                    // invalidate cached personal key
    	                    ((Kontalk)getApplicationContext()).invalidatePersonalKey();

    	                    Log.v(TAG, "personal key updated.");
    	                }
    	            }

    	            try {
	    	            String userId = StringUtils.parseName(p.getFrom());
	    	            String fingerprint = PGP.getFingerprint(_publicKey);
	    	            UsersProvider.setUserKey(MessageCenterService.this, userId,
	    	            	_publicKey, fingerprint);

                		// invalidate cache for this user
                		Contact.invalidate(userId);
    	            }
    	            catch (Exception e) {
    	            	// TODO warn user
    	            	Log.e(TAG, "unable to update user key", e);
    	            }
                }

            }

            // vcard coming from sync, send a broadcast but do not store
            else if (p.getType() == IQ.Type.SET) {

                Intent i = new Intent(ACTION_VCARD);
                i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

                String from = p.getFrom();
                String network = StringUtils.parseServer(from);
                // our network - convert to userId
                if (network.equalsIgnoreCase(mServer.getNetwork())) {
                    StringBuilder b = new StringBuilder();

                    // is this our vCard?
                    String userId = StringUtils.parseName(from);
                    String hash = MessageUtils.sha1(mMyUsername);
                    if (userId.equalsIgnoreCase(hash))
                    	myCard = true;

                    b.append(userId);
                    b.append(StringUtils.parseResource(from));
                    i.putExtra(EXTRA_FROM_USERID, b.toString());
                }

                i.putExtra(EXTRA_FROM, from);
                i.putExtra(EXTRA_TO, p.getTo());
                i.putExtra(EXTRA_PUBLIC_KEY, _publicKey);

                Log.v(TAG, "broadcasting vcard: " + i);
                mLocalBroadcastManager.sendBroadcast(i);

            }
        }
    }

    /** Listener for presence stanzas. */
    private final class PresenceListener implements PacketListener {

        private Packet subscribe(Presence p) {
            PacketExtension _pkey = p.getExtension(SubscribePublicKey.ELEMENT_NAME, SubscribePublicKey.NAMESPACE);

            try {

	            if (_pkey instanceof SubscribePublicKey) {
	                SubscribePublicKey pkey = (SubscribePublicKey) _pkey;

                    PGPPublicKeyRing pubRing = PGP.readPublicKeyring(pkey.getKey());
                    PGPPublicKey publicKey = PGP.getMasterKey(pubRing);
                    String fingerprint = MessageUtils.bytesToHex(publicKey.getFingerprint());

                    // store key to users table
                    String userId = StringUtils.parseName(p.getFrom());
                    UsersProvider.setUserKey(MessageCenterService.this, userId,
                    	pkey.getKey(), fingerprint);
	            }

                Presence p2 = new Presence(Presence.Type.subscribed);
                p2.setTo(p.getFrom());
                return p2;

            }
            catch (Exception e) {
                Log.w(TAG, "unable add user to whitelist", e);
                // TODO should we notify the user about this?
                // TODO throw new PGPException(...)
                return null;
            }
        }

        @Override
        public void processPacket(Packet packet) {
            try {
                Presence p = (Presence) packet;

                // presence subscription request
                if (p.getType() == Presence.Type.subscribe) {

                	// auto-accept subscription
                	if (Preferences.getAutoAcceptSubscriptions(MessageCenterService.this)) {

	                    Packet r = subscribe(p);
	                    if (r != null)
	                        mConnection.sendPacket(r);

                	}

                	// ask the user
                	else {

                		/*
                		 * Subscription procedure:
                		 * 1. update (or insert) users table with the public key just received
                		 * 2. update (or insert) threads table with a special subscription record
                		 * 3. user will either accept or refuse
                		 */

                		String from = StringUtils.parseName(p.getFrom());

                		// extract public key
                		String name = null, fingerprint = null;
                		byte[] publicKey = null;
                        PacketExtension _pkey = p.getExtension(SubscribePublicKey.ELEMENT_NAME, SubscribePublicKey.NAMESPACE);
                        if (_pkey instanceof SubscribePublicKey) {
                            SubscribePublicKey pkey = (SubscribePublicKey) _pkey;
                            byte[] _publicKey = pkey.getKey();
                            // extract the name from the uid
                            PGPPublicKeyRing ring = PGP.readPublicKeyring(_publicKey);
                            if (ring != null) {
                            	PGPPublicKey pk = PGP.getMasterKey(ring);
                            	if (pk != null) {
                            		// set all parameters
		                            name = PGP.getUserId(pk, mServer.getNetwork());
		                            fingerprint = PGP.getFingerprint(pk);
		                            publicKey = _publicKey;
                            	}
                            }
                        }

                		ContentResolver cr = getContentResolver();
                		ContentValues values = new ContentValues(4);

                		// insert public key into the users table
                		values.put(Users.HASH, from);
                		values.put(Users.PUBLIC_KEY, publicKey);
                		values.put(Users.FINGERPRINT, fingerprint);
                		values.put(Users.DISPLAY_NAME, name);
                		cr.insert(Users.CONTENT_URI.buildUpon()
                				.appendQueryParameter(Users.DISCARD_NAME, "true")
                				.build(), values);

                		// invalidate cache for this user
                		Contact.invalidate(from);

                		// insert request into the database
                		values.clear();
                		values.put(CommonColumns.PEER, from);
                		values.put(CommonColumns.TIMESTAMP, System.currentTimeMillis());
                		cr.insert(Requests.CONTENT_URI, values);

                		// fire up a notification
                		MessagingNotification.chatInvitation(MessageCenterService.this, from);
                	}

                }

                // presence subscription response
                else if (p.getType() == Presence.Type.subscribed) {

            		String from = StringUtils.parseName(p.getFrom());

                	if (UsersProvider.getPublicKey(MessageCenterService.this, from) == null) {
                		// public key not found
                		// assuming the user has allowed us, request it

                        VCard4 vcard = new VCard4();
                        vcard.setType(IQ.Type.GET);
                        vcard.setTo(StringUtils.parseBareAddress(p.getFrom()));

                        sendPacket(vcard);
                	}

                    // send a broadcast
                    Intent i = new Intent(ACTION_SUBSCRIBED);
                    i.putExtra(EXTRA_TYPE, Presence.Type.subscribed.name());
                    i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

                    from = p.getFrom();
                    String network = StringUtils.parseServer(from);
                    // our network - convert to userId
                    if (network.equalsIgnoreCase(mServer.getNetwork())) {
                        StringBuilder b = new StringBuilder();
                        b.append(StringUtils.parseName(from));
                        b.append(StringUtils.parseResource(from));
                        i.putExtra(EXTRA_FROM_USERID, b.toString());
                    }

                    i.putExtra(EXTRA_FROM, from);
                    i.putExtra(EXTRA_TO, p.getTo());

                    mLocalBroadcastManager.sendBroadcast(i);
                }

                /*
                else if (p.getType() == Presence.Type.unsubscribed) {
                    // TODO can this even happen?
                }
                */

                else {
                    Intent i = new Intent(ACTION_PRESENCE);
                    Presence.Type type = p.getType();
                    i.putExtra(EXTRA_TYPE, type != null ? type.name() : Presence.Type.available.name());
                    i.putExtra(EXTRA_PACKET_ID, p.getPacketID());

                    String from = p.getFrom();
                    String network = StringUtils.parseServer(from);
                    // our network - convert to userId
                    if (network.equalsIgnoreCase(mServer.getNetwork())) {
                        StringBuilder b = new StringBuilder();
                        b.append(StringUtils.parseName(from));
                        b.append(StringUtils.parseResource(from));
                        i.putExtra(EXTRA_FROM_USERID, b.toString());
                    }

                    i.putExtra(EXTRA_FROM, from);
                    i.putExtra(EXTRA_TO, p.getTo());
                    i.putExtra(EXTRA_STATUS, p.getStatus());
                    Presence.Mode mode = p.getMode();
                    i.putExtra(EXTRA_SHOW, mode != null ? mode.name() : Presence.Mode.available.name());
                    i.putExtra(EXTRA_PRIORITY, p.getPriority());

                    // getExtension doesn't work here
                    Iterator<PacketExtension> iter = p.getExtensions().iterator();
                    while (iter.hasNext()) {
                        PacketExtension _ext = iter.next();
                        if (_ext instanceof DelayInformation) {
                            DelayInformation delay = (DelayInformation) _ext;
                            i.putExtra(EXTRA_STAMP, delay.getStamp().getTime());
                            break;
                        }
                    }

                    // non-standard stanza group extension
                    PacketExtension ext = p.getExtension(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE);
                    if (ext != null && ext instanceof StanzaGroupExtension) {
                        StanzaGroupExtension g = (StanzaGroupExtension) ext;
                        i.putExtra(EXTRA_GROUP_ID, g.getId());
                        i.putExtra(EXTRA_GROUP_COUNT, g.getCount());
                    }

                    Log.v(TAG, "broadcasting presence: " + i);
                    mLocalBroadcastManager.sendBroadcast(i);
                }
            }
            catch (Exception e) {
                Log.e(TAG, "error parsing presence", e);
            }
        }
    }

    /** Listener for message stanzas. */
    private final class MessageListener implements PacketListener {
        private static final String selectionOutgoing = Messages.DIRECTION + "=" + Messages.DIRECTION_OUT;
        private static final String selectionIncoming = Messages.DIRECTION + "=" + Messages.DIRECTION_IN;

        @Override
        public void processPacket(Packet packet) {
            org.jivesoftware.smack.packet.Message m = (org.jivesoftware.smack.packet.Message) packet;
            if (m.getType() == org.jivesoftware.smack.packet.Message.Type.chat) {
                Intent i = new Intent(ACTION_MESSAGE);
                String from = m.getFrom();
                String network = StringUtils.parseServer(from);
                // our network - convert to userId
                if (network.equalsIgnoreCase(mServer.getNetwork())) {
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
                mLocalBroadcastManager.sendBroadcast(i);

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
                    synchronized (mWaitingReceipt) {
                        String id = m.getPacketID();
                        Long _msgId = mWaitingReceipt.get(id);
                        long msgId = (_msgId != null) ? _msgId : 0;
                        ContentResolver cr = getContentResolver();

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

                                mWaitingReceipt.remove(id);
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

                                mWaitingReceipt.remove(id);

                                // we can now release the message center. Hopefully
                                // there will be one hold and one matching release.
                                mIdleHandler.release();
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

                            mWaitingReceipt.remove(id);
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
                            MessageCenterService.this,
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
                                MessageUtils.decryptMessage(MessageCenterService.this,
                                        mServer, msg, encryptedData);
                            }

                            catch (Exception exc) {
                                Log.e(TAG, "decryption failed", exc);

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
                            		MediaStorage.writeInternalMedia(MessageCenterService.this,
                            			filename, preview.getContents());
                            }
                            catch (IOException e) {
                                Log.w(TAG, "error storing thumbnail", e);
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
                                mWaitingReceipt.put(ack.getPacketID(), storageId);
                            }
                            sendPacket(ack);
                        }
                    }

                }
            }

            // error message
            else if (m.getType() == org.jivesoftware.smack.packet.Message.Type.error) {
                synchronized (mWaitingReceipt) {
                    String id = m.getPacketID();
                    Long _msgId = mWaitingReceipt.get(id);
                    long msgId = (_msgId != null) ? _msgId : 0;
                    ContentResolver cr = getContentResolver();

                    // message has been rejected: mark as error
                    if (msgId > 0) {
                        ContentValues values = new ContentValues(3);
                        values.put(Messages.STATUS, Messages.STATUS_NOTDELIVERED);
                        values.put(Messages.STATUS_CHANGED, System.currentTimeMillis());
                        cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                            values, selectionOutgoing, null);

                        mWaitingReceipt.remove(id);

                        // we can now release the message center. Hopefully
                        // there will be one hold and one matching release.
                        mIdleHandler.release();
                    }
                }
            }
        }
    }

    /** Listener and manager for a key pair regeneration cycle. */
    private final class RegenerateKeyPairListener implements PacketListener {
        private BroadcastReceiver mKeyReceiver, mConnReceiver;
        private PGPKeyPairRing mKeyRing;
        private PGPPublicKey mRevoked;

        public RegenerateKeyPairListener()
        		throws CertificateException, SignatureException, PGPException, IOException {

            revokeCurrentKey();
            setupKeyPairReceiver();
            setupConnectedReceiver();

            Intent i = new Intent(getApplicationContext(), KeyPairGeneratorService.class);
            i.setAction(KeyPairGeneratorService.ACTION_GENERATE);
            i.putExtra(KeyPairGeneratorService.EXTRA_FOREGROUND, true);
            startService(i);
        }

        public void abort() {
            if (mKeyReceiver != null) {
                mLocalBroadcastManager.unregisterReceiver(mKeyReceiver);
                mKeyReceiver = null;
            }

            if (mConnReceiver != null) {
                mLocalBroadcastManager.unregisterReceiver(mConnReceiver);
                mConnReceiver = null;
            }
        }

        private Packet prepareKeyPacket() {
            if (mKeyRing != null) {
                try {
                    String publicKey = Base64.encodeToString(mKeyRing.publicKey.getEncoded(), Base64.NO_WRAP);

                    Registration iq = new Registration();
                    iq.setType(IQ.Type.SET);
                    iq.setTo(mConnection.getServiceName());
                    Form form = new Form(Form.TYPE_SUBMIT);

                    // form type: register#key
                    FormField type = new FormField("FORM_TYPE");
                    type.setType(FormField.TYPE_HIDDEN);
                    type.addValue("http://kontalk.org/protocol/register#key");
                    form.addField(type);

                    // new (to-be-signed) public key
                    FormField fieldKey = new FormField("publickey");
                    fieldKey.setLabel("Public key");
                    fieldKey.setType(FormField.TYPE_TEXT_SINGLE);
                    fieldKey.addValue(publicKey);
                    form.addField(fieldKey);

                    // old (revoked) public key
                    if (mRevoked != null) {
	                    String revokedKey = Base64.encodeToString(mRevoked.getEncoded(), Base64.NO_WRAP);

	                    FormField fieldRevoked = new FormField("revoked");
	                    fieldRevoked.setLabel("Revoked public key");
	                    fieldRevoked.setType(FormField.TYPE_TEXT_SINGLE);
	                    fieldRevoked.addValue(revokedKey);
	                    form.addField(fieldRevoked);
                    }

                    iq.addExtension(form.getDataFormToSend());
                    return iq;
                }
                catch (IOException e) {
                    Log.v(TAG, "error encoding key", e);
                }
            }

            return null;
        }

        private void setupKeyPairReceiver() {
            if (mKeyReceiver == null) {

                PersonalKeyRunnable action = new PersonalKeyRunnable() {
                    public void run(PersonalKey key) {
                        Log.d(TAG, "keypair generation complete.");
                        // unregister the broadcast receiver
                        mLocalBroadcastManager.unregisterReceiver(mKeyReceiver);
                        mKeyReceiver = null;

                        // store the key
                        try {
                        	AccountManager am = (AccountManager) getSystemService(Context.ACCOUNT_SERVICE);
                            Account acc = Authenticator.getDefaultAccount(am);
                            String name = am.getUserData(acc, Authenticator.DATA_NAME);

                            String userId = MessageUtils.sha1(acc.name);
                            mKeyRing = key.storeNetwork(userId, mServer.getNetwork(), name,
                                // TODO should we ask passphrase to the user?
                                ((Kontalk)getApplicationContext()).getCachedPassphrase());

                            // listen for connection events
                            setupConnectedReceiver();
                            // request connection status
                            requestConnectionStatus(MessageCenterService.this);

                            // CONNECTED listener will do the rest
                        }
                        catch (Exception e) {
                            // TODO notify user
                            Log.v(TAG, "error saving key", e);
                        }
                    }
                };

                mKeyReceiver = new KeyGeneratorReceiver(mIdleHandler, action);

                IntentFilter filter = new IntentFilter(KeyPairGeneratorService.ACTION_GENERATE);
                mLocalBroadcastManager.registerReceiver(mKeyReceiver, filter);
            }
        }

        private void setupConnectedReceiver() {
            if (mConnReceiver == null) {
                mConnReceiver = new BroadcastReceiver() {
                    public void onReceive(Context context, Intent intent) {
                        // unregister the broadcast receiver
                        mLocalBroadcastManager.unregisterReceiver(mConnReceiver);
                        mConnReceiver = null;

                        // prepare public key packet
                        Packet iq = prepareKeyPacket();

                        if (iq != null) {

                            // setup packet filter for response
                            PacketIDFilter filter = new PacketIDFilter(iq.getPacketID());
                            mConnection.addPacketListener(RegenerateKeyPairListener.this, filter);

                            // send the key out
                            sendPacket(iq);

                            // now wait for a response
                        }

                        // TODO else?
                    }
                };

                IntentFilter filter = new IntentFilter(ACTION_CONNECTED);
                mLocalBroadcastManager.registerReceiver(mConnReceiver, filter);
            }
        }

        /** We do this here so if something goes wrong the old key is still valid. */
        private void revokeCurrentKey()
        		throws CertificateException, PGPException, IOException, SignatureException {

        	PersonalKey oldKey = ((Kontalk) getApplicationContext()).getPersonalKey();
        	if (oldKey != null)
        		mRevoked = oldKey.revoke(false);
        }

        @Override
        public void processPacket(Packet packet) {
            IQ iq = (IQ) packet;
            if (iq.getType() == IQ.Type.RESULT) {
                DataForm response = (DataForm) iq.getExtension("x", "jabber:x:data");
                if (response != null) {
                    String publicKey = null;

                    // ok! message will be sent
                    List<FormField> fields = response.getFields();
                    for (FormField field : fields) {
                        if ("publickey".equals(field.getVariable())) {
                            publicKey = field.getValues().get(0);
                            break;
                        }
                    }

                    if (!TextUtils.isEmpty(publicKey)) {
                        byte[] publicKeyData;
                        byte[] privateKeyData;
                        byte[] bridgeCertData;
                        try {
                            publicKeyData = Base64.decode(publicKey, Base64.DEFAULT);
                            privateKeyData = mKeyRing.secretKey.getEncoded();

                            String passphrase = ((Kontalk) getApplicationContext()).getCachedPassphrase();
                            // TODO subjectAltName?
                            bridgeCertData = X509Bridge.createCertificate(publicKeyData,
                            	mKeyRing.secretKey.getSecretKey(), passphrase, null).getEncoded();
                        }
                        catch (Exception e) {
                            Log.e(TAG, "error decoding key data", e);
                            publicKeyData = null;
                            privateKeyData = null;
                            bridgeCertData = null;
                        }

                        if (publicKeyData != null && privateKeyData != null && bridgeCertData != null) {

                            // store key data in AccountManager
                            Authenticator.setDefaultPersonalKey(MessageCenterService.this,
                                publicKeyData, privateKeyData, bridgeCertData);
                            // invalidate cached personal key
                            ((Kontalk)getApplicationContext()).invalidatePersonalKey();

                            mHandler.post(new Runnable() {
                                public void run() {
                                    Toast.makeText(getApplicationContext(),
                                        R.string.msg_gen_keypair_complete,
                                        Toast.LENGTH_LONG).show();
                                }
                            });

                            // restart message center
                            restart(getApplicationContext());
                        }

                        // TODO else?
                    }
                }
            }

            // we are done here
            endKeyPairRegeneration();
        }
    }

}

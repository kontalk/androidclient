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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipInputStream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.ExceptionCallback;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.debugger.AbstractDebugger;
import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.debugger.SmackDebuggerFactory;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.sm.StreamManagementException;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.SuccessCallback;
import org.jivesoftware.smackx.caps.packet.CapsExtension;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.csi.ClientStateIndicationManager;
import org.jivesoftware.smackx.delay.packet.DelayInformation;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.forward.packet.Forwarded;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jivesoftware.smackx.iqversion.VersionManager;
import org.jivesoftware.smackx.iqversion.packet.Version;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Domainpart;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.spongycastle.openpgp.PGPException;

import android.accounts.Account;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
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
import androidx.annotation.Nullable;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.widget.Toast;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.BlockingCommand;
import org.kontalk.client.E2EEncryption;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.client.OutOfBandData;
import org.kontalk.client.PublicKeyPublish;
import org.kontalk.client.PushRegistration;
import org.kontalk.client.RosterMatch;
import org.kontalk.client.ServerlistCommand;
import org.kontalk.client.SmackInitializer;
import org.kontalk.client.UserLocation;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.data.GroupInfo;
import org.kontalk.message.AttachmentComponent;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.GroupComponent;
import org.kontalk.message.HiddenTextComponent;
import org.kontalk.message.InReplyToComponent;
import org.kontalk.message.LocationComponent;
import org.kontalk.message.ReferencedMessage;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MessagesProviderClient.MessageUpdater;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Requests;
import org.kontalk.provider.MyUsers;
import org.kontalk.provider.UsersProvider;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.service.UploadService;
import org.kontalk.service.XMPPConnectionHelper;
import org.kontalk.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.service.msgcenter.event.BlocklistEvent;
import org.kontalk.service.msgcenter.event.BlocklistRequest;
import org.kontalk.service.msgcenter.event.ConnectedEvent;
import org.kontalk.service.msgcenter.event.DisconnectedEvent;
import org.kontalk.service.msgcenter.event.LastActivityRequest;
import org.kontalk.service.msgcenter.event.NoPresenceEvent;
import org.kontalk.service.msgcenter.event.PresenceEvent;
import org.kontalk.service.msgcenter.event.PresenceRequest;
import org.kontalk.service.msgcenter.event.PublicKeyRequest;
import org.kontalk.service.msgcenter.event.RosterMatchRequest;
import org.kontalk.service.msgcenter.event.RosterStatusEvent;
import org.kontalk.service.msgcenter.event.RosterStatusRequest;
import org.kontalk.service.msgcenter.event.SendChatStateRequest;
import org.kontalk.service.msgcenter.event.SendDeliveryReceiptRequest;
import org.kontalk.service.msgcenter.event.SendMessageRequest;
import org.kontalk.service.msgcenter.event.ServerListEvent;
import org.kontalk.service.msgcenter.event.ServerListRequest;
import org.kontalk.service.msgcenter.event.SetUserPrivacyRequest;
import org.kontalk.service.msgcenter.event.SubscribeRequest;
import org.kontalk.service.msgcenter.event.UnsubscribeRequest;
import org.kontalk.service.msgcenter.event.UpdateStatusRequest;
import org.kontalk.service.msgcenter.event.UploadAttachmentRequest;
import org.kontalk.service.msgcenter.event.UploadPrivateKeyRequest;
import org.kontalk.service.msgcenter.event.UploadServiceFoundEvent;
import org.kontalk.service.msgcenter.event.UserBlockedEvent;
import org.kontalk.service.msgcenter.event.UserOnlineEvent;
import org.kontalk.service.msgcenter.event.UserUnblockedEvent;
import org.kontalk.service.msgcenter.event.VersionRequest;
import org.kontalk.service.msgcenter.group.AddRemoveMembersCommand;
import org.kontalk.service.msgcenter.group.CreateGroupCommand;
import org.kontalk.service.msgcenter.group.GroupCommand;
import org.kontalk.service.msgcenter.group.GroupController;
import org.kontalk.service.msgcenter.group.GroupControllerFactory;
import org.kontalk.service.msgcenter.group.PartCommand;
import org.kontalk.service.msgcenter.group.SetSubjectCommand;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.EventBusIndex;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.WakefulHashSet;
import org.kontalk.util.XMPPUtils;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_FOREGROUND;


/**
 * The Message Center Service.
 * Use {@link Intent}s to deliver commands (via {@link Context#startService}).
 * Service will broadcast intents when certain events occur.
 *
 * @author Daniele Ricci
 */
public class MessageCenterService extends Service implements ConnectionHelperListener {
    public static final String TAG = MessageCenterService.class.getSimpleName();

    private static final EventBus BUS;

    static {
        BUS = EventBus.builder()
            // TODO .logger(...)
            .addIndex(new EventBusIndex())
            .throwSubscriberException(BuildConfig.DEBUG)
            .logNoSubscriberMessages(BuildConfig.DEBUG)
            .build();
        SmackConfiguration.DEBUG = Log.isDebug();
        // we need our own debugger factory because of our internal logging system
        SmackConfiguration.setDefaultSmackDebuggerFactory(new SmackDebuggerFactory() {
            @Override
            public SmackDebugger create(XMPPConnection connection) throws IllegalArgumentException {
                return new AbstractDebugger(connection) {
                    @Override
                    protected void log(String logMessage) {
                        Log.d("SMACK", logMessage);
                    }

                    @Override
                    protected void log(String logMessage, Throwable throwable) {
                        Log.d("SMACK", logMessage, throwable);
                    }
                };
            }
        });
    }

    public static final String ACTION_HOLD = "org.kontalk.action.HOLD";
    public static final String ACTION_RELEASE = "org.kontalk.action.RELEASE";
    public static final String ACTION_RESTART = "org.kontalk.action.RESTART";
    public static final String ACTION_TEST = "org.kontalk.action.TEST";
    public static final String ACTION_PUSH_START = "org.kontalk.push.START";
    public static final String ACTION_PUSH_STOP = "org.kontalk.push.STOP";
    public static final String ACTION_PUSH_REGISTERED = "org.kontalk.push.REGISTERED";
    public static final String ACTION_IDLE = "org.kontalk.action.IDLE";
    public static final String ACTION_PING = "org.kontalk.action.PING";

    /**
     * Commence key pair regeneration.
     * {@link KeyPairGeneratorService} service will be started to generate the
     * key pair. After that, we will send the public key to the server for
     * verification and signature. Once the server returns the signed public
     * key, it will be installed in the default account.
     * Broadcasted when key pair regeneration has completed.
     */
    @Deprecated
    public static final String ACTION_REGENERATE_KEYPAIR = "org.kontalk.action.REGEN_KEYPAIR";

    /**
     * Commence key pair import.
     */
    @Deprecated
    public static final String ACTION_IMPORT_KEYPAIR = "org.kontalk.action.IMPORT_KEYPAIR";

    // common parameters
    public static final String EXTRA_FOREGROUND = "org.kontalk.foreground";

    // used with org.kontalk.action.IMPORT_KEYPAIR
    public static final String EXTRA_KEYPACK = "org.kontalk.keypack";
    public static final String EXTRA_PASSPHRASE = "org.kontalk.passphrase";

    // used with org.kontalk.action.TEST
    public static final String EXTRA_TEST_CHECK_NETWORK = "org.kontalk.test.check_network";

    // other
    public static final String PUSH_REGISTRATION_ID = "org.kontalk.PUSH_REGISTRATION_ID";
    private static final String DEFAULT_PUSH_PROVIDER = "gcm";

    /**
     * Minimal wakeup time.
     */
    public final static int MIN_WAKEUP_TIME = 300000;

    /**
     * Normal ping tester timeout.
     */
    private static final int SLOW_PING_TIMEOUT = 10000;

    /**
     * Fast ping tester timeout.
     */
    private static final int FAST_PING_TIMEOUT = 5000;

    /**
     * Minimal interval between connection tests (2 mins).
     */
    private static final int MIN_TEST_INTERVAL = 2 * 60 * 1000;

    /** How long to retain the wakelock to wait for incoming messages. */
    private static final int WAIT_FOR_MESSAGES_DELAY = 5000;

    static final IPushListener sPushListener = PushServiceManager.getDefaultListener();

    /**
     * Push service instance.
     */
    private IPushService mPushService;
    /**
     * Push notifications enabled flag.
     */
    boolean mPushNotifications;
    /**
     * Server push sender id. This is static so the {@link IPushListener} can see it.
     */
    static String sPushSenderId;
    /**
     * Push registration id.
     */
    String mPushRegistrationId;
    /**
     * Flag marking a currently ongoing push registration cycle (unregister/register)
     */
    boolean mPushRegistrationCycle;

    // created in onCreate
    private WakeLock mWakeLock;
    private WakeLock mPingLock;
    LocalBroadcastManager mLocalBroadcastManager;
    private AlarmManager mAlarmManager;

    private LastActivityListener mLastActivityListener;
    private PingFailedListener mPingFailedListener;

    /**
     * Cached last used server.
     */
    EndpointServer mServer;
    /**
     * The connection helper instance.
     */
    XMPPConnectionHelper mHelper;
    /**
     * The connection instance.
     */
    KontalkConnection mConnection;
    /**
     * My username (account name).
     */
    String mMyUsername;

    /** The current network identifier. */
    private String mCurrentNetwork;

    /**
     * Supported upload services.
     */
    List<IUploadService> mUploadServices;

    /**
     * Roster store.
     */
    private SQLiteRosterStore mRosterStore;

    /**
     * Service handler.
     */
    Handler mHandler;
    /**
     * Task execution pool. Generally used by packet listeners.
     */
    private ExecutorService mThreadPool;

    /**
     * Idle handler.
     */
    IdleConnectionHandler mIdleHandler;
    /**
     * Inactive state flag (for CSI).
     */
    private boolean mInactive;
    /**
     * Timestamp of last use of {@link #ACTION_TEST}.
     */
    private long mLastTest;
    /**
     * Pending intent for idle signaling.
     */
    private PendingIntent mIdleIntent;

    private boolean mFirstStart = true;

    /**
     * Messages waiting for server ack (type: internalStorageId).
     */
    WakefulHashSet<Long> mWaitingReceipt;

    private RegenerateKeyPairListener mKeyPairRegenerator;
    private ImportKeyPairListener mKeyPairImporter;

    static final class IdleConnectionHandler extends Handler implements IdleHandler {
        /**
         * Idle signal.
         */
        private static final int MSG_IDLE = 1;
        /**
         * Inactive signal (for CSI).
         */
        private static final int MSG_INACTIVE = 2;
        /**
         * Test signal.
         */
        private static final int MSG_TEST = 3;

        /**
         * How much time to wait to enter inactive state.
         */
        private final static int INACTIVE_TIME = 30000;

        /**
         * A reference to the message center.
         */
        WeakReference<MessageCenterService> s;
        /**
         * Reference counter.
         */
        int mRefCount;

        public IdleConnectionHandler(MessageCenterService service, int refCount, Looper looper) {
            super(looper);
            s = new WeakReference<>(service);
            mRefCount = refCount;

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
                if (service.isConnecting()) {
                    // try again next time
                    queueInactive();
                }

                // push notifications unavailable: set up an alarm for next time
                if (service.mPushRegistrationId == null) {
                    service.setWakeupAlarm();
                }

                Log.d(TAG, "shutting down message center due to inactivity");
                service.stopSelf();

                return true;
            }
            else if (msg.what == MSG_INACTIVE) {
                service.inactive();
                return true;
            }
            else if (msg.what == MSG_TEST) {
                long now = System.currentTimeMillis();
                if ((now - service.getLastReceivedStanza()) >= FAST_PING_TIMEOUT) {
                    if (!service.fastReply()) {
                        Log.v(TAG, "test ping failed");
                        XMPPConnection conn = service.mConnection;
                        if (conn != null) {
                            AndroidAdaptiveServerPingManager
                                .getInstanceFor(conn, service)
                                .pingFailed();
                        }
                        restart(service.getApplicationContext());
                    }
                    else {
                        XMPPConnection conn = service.mConnection;
                        if (conn != null) {
                            AndroidAdaptiveServerPingManager
                                .getInstanceFor(conn, service)
                                .pingSuccess();
                        }
                    }
                }
                return true;
            }

            return false;
        }

        /**
         * Resets the idle timer.
         */
        public void reset(int refCount) {
            mRefCount = refCount;
            reset();
        }

        /**
         * Resets the idle timer.
         */
        public void reset() {
            removeMessages(MSG_IDLE);
            removeMessages(MSG_INACTIVE);

            if (mRefCount <= 0 && getLooper().getThread().isAlive()) {
                // queue inactive message
                queueInactive();
            }
        }

        public void idle() {
            sendMessage(obtainMessage(MSG_IDLE));
        }

        public void hold(boolean activate) {
            mRefCount++;
            if (mRefCount > 0) {
                MessageCenterService service = s.get();
                if (service != null && service.isInactive() && service.isConnected()) {
                    service.active(activate);
                }
            }
            post(new Runnable() {
                public void run() {
                    abortIdle();
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
                        removeMessages(MSG_INACTIVE);
                        Looper.myQueue().addIdleHandler(IdleConnectionHandler.this);

                        // trigger inactive timer only if connected
                        // the authenticated callback will ensure it will trigger anyway
                        MessageCenterService service = s.get();
                        if (service != null) {
                            // restart in foreground if needed
                            // this is used to keep the service alive for the necessary time
                            startForegroundIfNeeded(service, getStartIntent(service));

                            if (!service.isInactive() && service.isConnected()) {
                                queueInactive();
                            }
                        }
                    }
                });
            }
        }

        public void quit() {
            abortIdle();
            abortTest();
            getLooper().quit();
        }

        /**
         * Aborts any idle message because we are using the service or quitting.
         */
        void abortIdle() {
            Looper.myQueue().removeIdleHandler(IdleConnectionHandler.this);
            removeMessages(MSG_IDLE);
            removeMessages(MSG_INACTIVE);
            MessageCenterService service = s.get();
            if (service != null)
                service.cancelIdleAlarm();
        }

        public void forceInactiveIfNeeded() {
            post(new Runnable() {
                public void run() {
                    MessageCenterService service = s.get();
                    if (service != null && mRefCount <= 0 && !service.isInactive()) {
                        forceInactive();
                    }
                }
            });
        }

        public void forceInactive() {
            MessageCenterService service = s.get();
            if (service != null) {
                removeMessages(MSG_INACTIVE);
                if (service.isConnected())
                    service.inactive();
            }
        }

        void queueInactive() {
            // send inactive state message only if connected
            MessageCenterService service = s.get();
            if (service != null && service.isConnected()) {
                sendMessageDelayed(obtainMessage(MSG_INACTIVE), INACTIVE_TIME);
            }
        }

        public boolean isHeld() {
            return mRefCount > 0;
        }

        public void test() {
            post(new Runnable() {
                public void run() {
                    if (!hasMessages(MSG_TEST)) {
                        sendMessageDelayed(obtainMessage(MSG_TEST), FAST_PING_TIMEOUT);
                    }
                }
            });
        }

        private void abortTest() {
            removeMessages(MSG_TEST);
        }
    }

    private final BroadcastReceiver mInactivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (mIdleHandler != null) {
                    mIdleHandler.forceInactive();
                }
                if (mHelper != null && mHelper.isStruggling()) {
                    Log.d(TAG, "connection is not going well, shutting down message center");
                    stopSelf();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        // configure XMPP client
        configure();

        BUS.register(this);

        // create the roster store
        mRosterStore = new SQLiteRosterStore(this);

        // waiting receipt list
        // also used for keeping the device on while waiting for message delivery
        mWaitingReceipt = new WakefulHashSet<>(10, this, PowerManager
            .PARTIAL_WAKE_LOCK, Kontalk.TAG + "-SEND");

        // create the global wake locks
        mWakeLock = SystemUtils.createPartialWakeLock(this, Kontalk.TAG + "-Connect", false);
        mPingLock = SystemUtils.createPartialWakeLock(this, Kontalk.TAG + "-Ping", false);

        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        // cancel any pending alarm intent
        cancelIdleAlarm();

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mPushService = PushServiceManager.getInstance(this);

        // create idle handler
        createIdleHandler();

        // create main thread handler
        mHandler = new Handler();

        // register screen off listener for manual inactivation
        registerInactivity();
    }

    void queueTask(Runnable task) {
        if (mThreadPool != null) {
            mThreadPool.execute(task);
        }
    }

    private void createIdleHandler() {
        HandlerThread thread = new HandlerThread("IdleThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        int refCount = Kontalk.get().getReferenceCounter();
        mIdleHandler = new IdleConnectionHandler(this, refCount, thread.getLooper());
    }

    private void registerInactivity() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mInactivityReceiver, filter);
    }

    private void unregisterInactivity() {
        unregisterReceiver(mInactivityReceiver);
    }

    boolean sendMessage(org.jivesoftware.smack.packet.Message message, long databaseId) {
        final KontalkConnection conn = mConnection;
        if (conn != null) {
            if (databaseId > 0) {
                // set up an ack listener so we can set message status to sent
                try {
                    conn.addStanzaIdAcknowledgedListener(message.getStanzaId(),
                        new MessageAckListener(this, databaseId));
                }
                catch (StreamManagementException.StreamManagementNotEnabledException e) {
                    return false;
                }

                // matching release is in SM ack listener
                mIdleHandler.hold(false);
                mWaitingReceipt.add(databaseId);
            }

            boolean sent = sendPacket(message);
            if (!sent && databaseId > 0) {
                // message was not sent, remove from waiting queue
                mWaitingReceipt.remove(databaseId);
                mIdleHandler.release();
            }

            return sent;
        }
        return false;
    }

    boolean sendPacket(Stanza packet) {
        return sendPacket(packet, true);
    }

    /**
     * Sends a packet to the connection if found.
     *
     * @param bumpIdle true if the idle handler must be notified of this event
     */
    boolean sendPacket(Stanza packet, boolean bumpIdle) {
        // reset idler if requested
        if (bumpIdle && mIdleHandler != null)
            mIdleHandler.reset();

        final XMPPConnection conn = mConnection;
        if (conn != null) {
            try {
                conn.sendStanza(packet);
                return true;
            }
            catch (NotConnectedException e) {
                // ignored
                Log.v(TAG, "not connected. Dropping packet " + packet);
            }
            catch (InterruptedException e) {
                // ignored
                Log.v(TAG, "interrupted. Dropping packet " + packet);
            }
        }

        return false;
    }

    void sendIqWithReply(IQ packet, boolean bumpIdle, final StanzaListener callback, @Nullable final ExceptionCallback errorCallback) {
        // reset idler if requested
        if (bumpIdle && mIdleHandler != null)
            mIdleHandler.reset();

        final XMPPConnection conn = mConnection;
        if (conn != null) {
            conn.sendIqRequestAsync(packet).onSuccess(new SuccessCallback<IQ>() {
                @Override
                public void onSuccess(IQ result) {
                    try {
                        callback.processStanza(result);
                    }
                    catch (NotConnectedException e) {
                        Log.v(TAG, "not connected. Dropping result packet " + result);
                        if (errorCallback != null)
                            errorCallback.processException(e);
                    }
                    catch (InterruptedException e) {
                        Log.v(TAG, "interrupted. Dropping result packet " + result);
                        if (errorCallback != null)
                            errorCallback.processException(e);
                    }
                    catch (SmackException.NotLoggedInException e) {
                        Log.v(TAG, "not logged in. Dropping result packet " + result);
                        if (errorCallback != null)
                            errorCallback.processException(e);
                    }
                }
            })
            .onError(new org.jivesoftware.smack.util.ExceptionCallback<Exception>() {
                @Override
                public void processException(Exception exception) {
                    if (errorCallback != null)
                        errorCallback.processException(exception);
                }
            });
        }
    }

    private void configure() {
        SmackInitializer.initialize(this);
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

        BUS.unregister(this);

        quit(false);

        // deactivate ping manager
        AndroidAdaptiveServerPingManager.onDestroy();

        // destroy roster store
        mRosterStore.onDestroy();
        mRosterStore = null;

        // unregister screen off listener for manual inactivation
        unregisterInactivity();

        // destroy references
        mAlarmManager = null;
        mLocalBroadcastManager = null;
        // also release wakelocks just to be sure
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
        if (mPingLock != null) {
            mPingLock.release();
            mPingLock = null;
        }

        // currently in online mode
        // take care of specific situations for rescheduling us
        if (!isOfflineMode(this)) {
            // shutting down due to missing network connection
            // schedule a reconnection for next time we'll have network
            // (only on systems on which we do not receive network state changes
            //  and if we don't have push notifications or pending work)
            if (!SystemUtils.isReceivingNetworkStateChanges() &&
                    (!isPushNotificationsAvailable(this) || hasPendingWork()) &&
                    !SystemUtils.isNetworkConnectionAvailable(this)) {
                Log.i(TAG, "network absent and no way of resuming, scheduling job for next time");
                scheduleStartJob();
            }

            // if we have pending messages, it means the OS is killing us
            // schedule a reconnection for next time we'll have network
            /* TODO needs more analysis
            else if (mustSetForeground(this) && hasPendingWork()) {
                // TODO remember to check with hasPendingWork if we need to foreground
                Log.i(TAG, "we have pending work, scheduling foreground job for next time");
                scheduleStartJob();
            }
            */
        }
    }

    private boolean hasPendingWork() {
        return MessagesProviderClient.getPendingMessagesCount(this) > 0 ||
            Preferences.getLastPushNotification() > 0;
    }

    public boolean isStarted() {
        return mLocalBroadcastManager != null;
    }

    private synchronized void quit(boolean restarting) {
        if (!restarting) {
            // quit the idle handler
            mIdleHandler.quit();
            mIdleHandler = null;
            // destroy the service handler
            // (can't stop it because it's the main thread)
            mHandler = null;
        }
        else {
            // reset the reference counter
            int refCount = Kontalk.get().getReferenceCounter();
            mIdleHandler.reset(refCount);
        }

        // stop all running tasks
        if (mThreadPool != null) {
            mThreadPool.shutdownNow();
            mThreadPool = null;
        }

        // disable listeners
        if (mHelper != null)
            mHelper.setListener(null);
        if (mConnection != null)
            mConnection.removeConnectionListener(this);

        // abort connection helper (if any)
        if (mHelper != null) {
            // this is because of NetworkOnMainThreadException
            AbortThread abortThread = new AbortThread(mHelper);
            abortThread.start();
            mHelper = null;
            try {
                abortThread.join();
            }
            catch (InterruptedException ignored) {
            }
        }

        // disconnect from server (if any)
        if (mConnection != null) {
            // disable ping manager
            AndroidAdaptiveServerPingManager
                .getInstanceFor(mConnection, this)
                .setEnabled(false);

            // synchronize with listener creation in connected()
            synchronized (mConnection) {
                PingManager.getInstanceFor(mConnection)
                    .unregisterPingFailedListener(mPingFailedListener);
                mPingFailedListener = null;
            }

            // this is because of NetworkOnMainThreadException
            DisconnectThread disconnectThread = new DisconnectThread(mConnection);
            disconnectThread.start();
            disconnectThread.joinTimeout(500);

            // clear the connection only if we are quitting
            if (!restarting) {
                // clear the roster store since we are about to close it
                getRoster().setRosterStore(null);
                mConnection = null;
            }
        }

        if (mUploadServices != null) {
            mUploadServices.clear();
            mUploadServices = null;
        }

        // clear cached data from contacts
        Contact.invalidateData();

        // stop any key pair regeneration service
        endKeyPairRegeneration();

        BUS.removeAllStickyEvents();
        BUS.post(new DisconnectedEvent());

        if (!restarting) {
            // release the wakelock if not restarting
            mWakeLock.release();
        }
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
                mHelper.interrupt();
            }
            catch (Exception e) {
                // ignored
            }
        }
    }

    private static final class DisconnectThread extends Thread {
        private final KontalkConnection mConn;

        public DisconnectThread(KontalkConnection conn) {
            mConn = conn;
        }

        @Override
        public void run() {
            try {
                mConn.disconnect();
            }
            catch (Exception e) {
                mConn.instantShutdown();
            }
        }

        public void joinTimeout(long millis) {
            try {
                // we must wait for the connection to actually close
                join(millis);
                // this won't send the last sm ack, preventing another interruptable zone
                mConn.suspendSmAck();
                interrupt();
            }
            catch (InterruptedException ignored) {
            }
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();

            // proceed to start only if network is available
            boolean canConnect = canConnect();
            boolean doConnect;

            switch (action != null ? action : "") {
                case ACTION_HOLD:
                    doConnect = handleHold(intent);
                    break;

                case ACTION_RELEASE:
                    doConnect = handleRelease();
                    break;

                case ACTION_IDLE:
                    doConnect = handleIdle();
                    break;

                case ACTION_PUSH_START:
                    doConnect = handlePushStart();
                    break;

                case ACTION_PUSH_STOP:
                    doConnect = handlePushStop();
                    break;

                case ACTION_PUSH_REGISTERED:
                    doConnect = handlePushRegistered(intent);
                    break;

                case ACTION_REGENERATE_KEYPAIR:
                    doConnect = handleRegenerateKeyPair(intent);
                    break;

                case ACTION_IMPORT_KEYPAIR:
                    doConnect = handleImportKeyPair(intent);
                    break;

                case ACTION_RESTART:
                    doConnect = handleRestart();
                    break;

                case ACTION_TEST:
                    doConnect = handleTest(canConnect, intent);
                    break;

                case ACTION_PING:
                    doConnect = handlePing(canConnect);
                    break;

                default:
                    // no command means normal service start, connect if not connected
                    doConnect = true;
                    break;
            }

            if (isOfflineMode(this)) {
                // stop immediately
                canConnect = doConnect = false;
            }

            if (canConnect && doConnect)
                createConnection();

            if (!canConnect && !doConnect && !isConnected() && !isConnecting()) {
                // no reason to exist
                stopSelf();
            }
            else {
                // immediately setup (or disable) the foreground notification if requested
                setForeground(intent.getBooleanExtra(EXTRA_FOREGROUND, false));
            }

            mFirstStart = false;
        }
        else {
            Log.v(TAG, "restarting after service crash");
            start(getApplicationContext());
        }
    }

    // methods below handle single intent commands
    // the returned value is assigned to doConnect in onStartCommand()

    /**
     * For documentation purposes only. Used by the command handler to quickly
     * find what command string they handle.
     */
    @Retention(value = RetentionPolicy.SOURCE)
    @Target(value = ElementType.METHOD)
    private @interface CommandHandler {
        String[] name();
    }

    @CommandHandler(name = ACTION_HOLD)
    private boolean handleHold(Intent intent) {
        if (!mFirstStart)
            mIdleHandler.hold(intent.getBooleanExtra("org.kontalk.activate", false));
        return true;
    }

    @CommandHandler(name = ACTION_RELEASE)
    private boolean handleRelease() {
        mIdleHandler.release();
        return false;
    }

    @CommandHandler(name = ACTION_IDLE)
    private boolean handleIdle() {
        mIdleHandler.idle();
        return false;
    }

    @CommandHandler(name = ACTION_PUSH_START)
    private boolean handlePushStart() {
        setPushNotifications(true);
        return false;
    }

    @CommandHandler(name = ACTION_PUSH_STOP)
    private boolean handlePushStop() {
        setPushNotifications(false);
        return false;
    }

    @CommandHandler(name = ACTION_PUSH_REGISTERED)
    private boolean handlePushRegistered(Intent intent) {
        String regId = intent.getStringExtra(PUSH_REGISTRATION_ID);
        // registration cycle under way
        if (regId == null && mPushRegistrationCycle) {
            mPushRegistrationCycle = false;
            pushRegister();
        }
        else
            setPushRegistrationId(regId);
        return false;
    }

    @CommandHandler(name = ACTION_REGENERATE_KEYPAIR)
    @Deprecated
    private boolean handleRegenerateKeyPair(Intent intent) {
        beginKeyPairRegeneration(intent.getStringExtra(EXTRA_PASSPHRASE));
        return true;
    }

    @CommandHandler(name = ACTION_IMPORT_KEYPAIR)
    @Deprecated
    private boolean handleImportKeyPair(Intent intent) {
        // zip file with keys
        Uri file = intent.getParcelableExtra(EXTRA_KEYPACK);
        // passphrase to decrypt files
        String passphrase = intent.getStringExtra(EXTRA_PASSPHRASE);
        beginKeyPairImport(file, passphrase);
        return false;
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleUploadPrivateKey(UploadPrivateKeyRequest request) {
        if (isConnected()) {
            beginUploadPrivateKey(request.passphrase);
        }
    }

    @CommandHandler(name = ACTION_RESTART)
    private boolean handleRestart() {
        quit(true);
        return true;
    }

    @CommandHandler(name = ACTION_TEST)
    private boolean handleTest(boolean canConnect, Intent intent) {
        if (isConnected()) {
            boolean checkNetwork = intent.getBooleanExtra(EXTRA_TEST_CHECK_NETWORK, false);
            if (checkNetwork) {
                if (mCurrentNetwork == null || !mCurrentNetwork.equals(SystemUtils.getCurrentNetworkName(this))) {
                    // network type changed - restart immediately
                    quit(true);
                    return canConnect;
                }
            }

            if (canTest()) {
                mLastTest = SystemClock.elapsedRealtime();
                mIdleHandler.test();
            }
            return false;
        }
        else {
            if (mHelper != null && mHelper.isBackingOff()) {
                // helper is waiting for backoff - restart immediately
                quit(true);
            }
            return canConnect;
        }
    }

    @CommandHandler(name = ACTION_PING)
    private boolean handlePing(boolean canConnect) {
        if (isConnected()) {
            // acquire a wake lock
            mPingLock.acquire();
            final XMPPConnection connection = mConnection;
            final PingManager pingManager = PingManager.getInstanceFor(connection);
            final WakeLock pingLock = mPingLock;
            Async.go(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (pingManager.pingMyServer(true, SLOW_PING_TIMEOUT)) {
                            AndroidAdaptiveServerPingManager
                                .getInstanceFor(connection, MessageCenterService.this)
                                .pingSuccess();
                        }
                        else {
                            AndroidAdaptiveServerPingManager
                                .getInstanceFor(connection, MessageCenterService.this)
                                .pingFailed();
                        }
                    }
                    catch (NotConnectedException e) {
                        // ignored
                    }
                    catch (InterruptedException e) {
                        // ignored
                    }
                    finally {
                        // release the wake lock
                        if (pingLock != null)
                            pingLock.release();
                    }
                }
            }, "PingServerIfNecessary (" + mConnection.getConnectionCounter() + ')');
            return false;
        }
        else {
            return canConnect;
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleUploadAttachment(UploadAttachmentRequest request) {
        // take the first available upload service :)
        IUploadService uploadService = getUploadService();
        if (uploadService != null) {
            CompositeMessage message = CompositeMessage.loadMessage(this, request.databaseId);
            if (message == null) {
                Log.w(TAG, "message not found: " + request.databaseId);
                return;
            }

            AttachmentComponent attachment = message.getComponent(AttachmentComponent.class);
            if (attachment == null) {
                // this is actually a bug
                ReportingManager.logException(new IllegalArgumentException("no attachments in message!"));
                Log.w(TAG, "no attachments in message " + request.databaseId);
                return;
            }

            Uri preMediaUri = attachment.getLocalUri();
            long fileLength;

            try {
                // encrypt the file if necessary
                if (message.getSecurityFlags() != Coder.SECURITY_CLEARTEXT) {
                    InputStream in = null;
                    try {
                        in = getContentResolver().openInputStream(preMediaUri);

                        // retrieve conversation for encrypting
                        Conversation conv = Conversation
                            .loadFromUserId(this, message.getRecipient());
                        if (conv == null) {
                            // this is actually a bug
                            ReportingManager.logException(new IllegalArgumentException("no conversation for message!"));
                            Log.w(TAG, "unable to load conversation for encrypting message " + request.databaseId);
                            return;
                        }

                        String[] encryptTo;
                        if (conv.isGroupChat()) {
                            encryptTo = conv.getGroupPeers();
                        }
                        else {
                            encryptTo = new String[] { message.getRecipient() };
                        }

                        File encrypted = MessageUtils.encryptFile(this, in,
                            SystemUtils.toString(encryptTo));
                        fileLength = encrypted.length();
                        preMediaUri = Uri.fromFile(encrypted);
                    }
                    finally {
                        SystemUtils.closeStream(in);
                    }
                }
                else {
                    fileLength = MediaStorage.getLength(this, preMediaUri);
                }
            }
            catch (Exception e) {
                Log.w(TAG, "error preprocessing media: " + preMediaUri, e);
                // simulate upload error
                UploadService.errorNotification(this,
                    getString(R.string.notify_ticker_upload_error),
                    getString(R.string.notify_text_upload_error));
                return;
            }

            final Uri mediaUri = preMediaUri;

            // build a filename
            String filename = CompositeMessage.getFilename(attachment.getMime(), new Date());
            if (filename == null)
                filename = MediaStorage.UNKNOWN_FILENAME;

            // media message - start upload service
            final String mime = attachment.getMime();
            final long databaseId = request.databaseId;
            final boolean encrypt = message.getSecurityFlags() != Coder.SECURITY_CLEARTEXT;
            uploadService.getPostUrl(filename, fileLength, mime, new IUploadService.UrlCallback() {
                @Override
                public void callback(String putUrl, String getUrl) {
                    // start upload intent service
                    // delete original (actually it's the encrypted temp file) if we already encrypted it
                    UploadService.start(MessageCenterService.this, mediaUri,
                        putUrl, getUrl, databaseId, mime, encrypt);
                }
            });

        }
        else {
            // TODO warn user about this problem
            Log.w(TAG, "no upload service - this shouldn't happen!");
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleSendMessage(SendMessageRequest request) {
        if (isConnected()) {
            sendMessage(request);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleSendChatState(SendChatStateRequest request) {
        if (isConnected()) {
            sendChatState(request);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleRosterMatch(RosterMatchRequest request) {
        if (isConnected()) {
            RosterMatch iq = new RosterMatch();

            for (String item : request.userIds) {
                iq.addItem(item);
            }

            // directed to the probe component
            try {
                iq.setTo(JidCreate.bareFrom(Localpart.from("probe"),
                    Domainpart.from(mServer.getNetwork())));
            }
            catch (XmppStringprepException e) {
                // cannot happen
                throw new RuntimeException(e);
            }

            iq.setStanzaId(request.id);
            // iq default type is get

            RosterMatchListener listener = new RosterMatchListener(iq);
            sendIqWithReply(iq, true, listener, listener);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleRosterStatus(RosterStatusRequest request) {
        if (mRosterStore != null) {
            RosterPacket.Item entry = mRosterStore.getEntry(request.jid);
            if (entry != null) {
                RosterPacket.ItemType subscriptionType = entry.getItemType();
                boolean subscribedFrom = subscriptionType == RosterPacket.ItemType.both ||
                    subscriptionType == RosterPacket.ItemType.from;
                boolean subscribedTo = subscriptionType == RosterPacket.ItemType.both ||
                    subscriptionType == RosterPacket.ItemType.to;

                BUS.post(new RosterStatusEvent(entry.getJid(), entry.getName(),
                    subscribedFrom, subscribedTo, request.id));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handlePresence(PresenceRequest request) {
        if (isConnected()) {
            final Roster roster = getRoster();

            if (request.jid == null) {
                for (RosterEntry entry : roster.getEntries()) {
                    BUS.post(replyPresenceRequest(roster, entry,
                        entry.getJid(), request.id));
                }

                // broadcast our own presence
                BUS.post(replyMyPresenceRequest(request.id));
            }
            else {
                final BareJid jid = request.jid.asBareJid();
                final RosterEntry entry = roster.getEntry(jid);
                BUS.post(replyPresenceRequest(roster, entry, jid, request.id));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleSubscribe(SubscribeRequest request) {
        if (isConnected()) {
            sendPacket(new Presence(request.jid, Presence.Type.subscribe));
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleUnsubscribe(UnsubscribeRequest request) {
        if (isConnected()) {
            sendPacket(new Presence(request.jid, Presence.Type.unsubscribe));
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleUpdateStatus(UpdateStatusRequest request) {
        if (isConnected()) {
            sendPacket(new Presence(Presence.Type.available,
                request.status, 0, null));
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleLastActivity(LastActivityRequest request) {
        if (isConnected()) {
            LastActivity p = new LastActivity(request.jid);
            p.setStanzaId(request.id);
            sendIqWithReply(p, true, mLastActivityListener, mLastActivityListener);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handlePublicKey(PublicKeyRequest request) {
        if (isConnected()) {
            if (request.jid != null) {
                // request public key for a specific user
                PublicKeyPublish p = new PublicKeyPublish();
                p.setTo(request.jid);
                p.setStanzaId(request.id);

                PublicKeyListener listener = new PublicKeyListener(this, p);
                sendIqWithReply(p, true, listener, listener);
            }
            else {
                // request public keys for the whole roster
                Collection<RosterEntry> buddies = getRoster().getEntries();
                for (RosterEntry buddy : buddies) {

                    final PublicKeyPublish p = new PublicKeyPublish();
                    p.setTo(buddy.getJid());

                    StanzaListener iqListener;
                    ExceptionCallback exListener;
                    final PublicKeyListener listener = new PublicKeyListener(this, p);

                    String stanzaId = request.id;
                    if (stanzaId != null) {
                        // add a prefix to the ID
                        // this was done to properly create request/response pairs
                        // Smack was getting angry at this
                        final String randomId = StringUtils.randomString(6);
                        stanzaId = randomId + stanzaId;

                        // TODO this class may be useful to others
                        iqListener = new StanzaListener() {
                            @Override
                            public void processStanza(Stanza packet) throws NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
                                // remove the prefix and call the original listener
                                String currentId = packet.getStanzaId();
                                if (currentId != null && currentId.length() > randomId.length())
                                    packet.setStanzaId(currentId.substring(randomId.length()));
                                listener.processStanza(packet);
                            }
                        };
                        // TODO this class may be useful to others
                        exListener = new ExceptionCallback() {
                            @Override
                            public void processException(Exception exception) {
                                // remove the prefix and call the original listener
                                String currentId = p.getStanzaId();
                                if (currentId != null && currentId.length() > randomId.length())
                                    p.setStanzaId(currentId.substring(randomId.length()));
                                listener.processException(exception);
                            }
                        };
                    }
                    else {
                        iqListener = listener;
                        exListener = listener;
                    }

                    p.setStanzaId(stanzaId);

                    sendIqWithReply(p, true, iqListener, exListener);
                }

                // request our own public key (odd eh?)
                PublicKeyPublish p = new PublicKeyPublish();
                p.setStanzaId(request.id);
                p.setTo(mConnection.getUser().asBareJid());
                PublicKeyListener listener = new PublicKeyListener(this, p);
                sendIqWithReply(p, true, listener, listener);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleServerList(ServerListRequest request) {
        if (isConnected()) {
            ServerlistCommand p = new ServerlistCommand();
            try {
                p.setTo(JidCreate.from("network", mServer.getNetwork(), ""));
            }
            catch (XmppStringprepException e) {
                Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
                // report it because it's a big deal
                ReportingManager.logException(e);
                return;
            }

            StanzaFilter filter = new StanzaIdFilter(p.getStanzaId());
            // TODO cache the listener (it shouldn't change)
            mConnection.addAsyncStanzaListener(new StanzaListener() {
                public void processStanza(Stanza packet) throws NotConnectedException {
                    if (packet.getError() == null) {
                        List<String> servers = ((ServerlistCommand.ServerlistCommandData) packet)
                            .getItems();
                        if (servers != null) {
                            BUS.post(new ServerListEvent(servers
                                .toArray(new String[0]), packet.getStanzaId()));
                        }
                        else {
                            BUS.post(new ServerListEvent(new XMPPException.XMPPErrorException(packet, StanzaError
                                .getBuilder(StanzaError.Condition.internal_server_error).build()), packet.getStanzaId()));
                        }
                    }
                }
            }, filter);

            sendPacket(p);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleSetUserPrivacy(SetUserPrivacyRequest request) {
        if (isConnected()) {
            setUserPrivacy(request.jid, request.command);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleBlocklist(BlocklistRequest request) {
        if (isConnected()) {
            requestBlocklist(request.id);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void handleVersion(VersionRequest request) {
        if (isConnected()) {
            Version version = new Version(request.jid);
            version.setStanzaId(request.id);
            sendPacket(version);
        }
    }

    /**
     * Creates a connection to server if needed.
     */
    private synchronized void createConnection() {
        // connection is null or disconnected and no helper is currently running
        if ((mConnection == null || !mConnection.isConnected()) && mHelper == null) {
            // acquire the wakelock
            mWakeLock.acquire();
            // hold on to the message center
            mIdleHandler.hold(false);

            // reset push notification variable
            mPushNotifications = Preferences.getPushNotificationsEnabled(this) &&
                mPushService != null && mPushService.isServiceAvailable();
            // reset waiting messages
            mWaitingReceipt.clear();

            // setup task execution pool
            mThreadPool = Executors.newCachedThreadPool();

            mInactive = false;

            // retrieve account name
            Account acc = Authenticator.getDefaultAccount(this);
            mMyUsername = (acc != null) ? acc.name : null;

            // get server from preferences
            mServer = Preferences.getEndpointServer(this);

            if (mConnection == null) {
                mHelper = new XMPPConnectionHelper(this, mServer, false);
            }
            else {
                // reuse connection if the server is the same
                KontalkConnection reuseConnection = mServer.equals(mConnection.getServer()) ?
                        mConnection : null;
                mHelper = new XMPPConnectionHelper(this, mServer, false, reuseConnection);
            }

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
        restart(this);
    }

    @Override
    public void authenticationFailed() {
        // fire up a notification explaining the situation
        MessagingNotification.authenticationError(this);
    }

    @Override
    public void reconnectingIn(int seconds) {
        // not used
    }

    @Override
    public void aborted(Exception e) {
        if (e != null) {
            // we are being called from the connection helper because of
            // connection errors. Set up a wake up timer to try again
            setWakeupAlarm();
        }
        // unrecoverable error - exit
        stopSelf();
    }

    @Override
    public synchronized void created(final XMPPConnection connection) {
        Log.v(TAG, "connection created.");
        mConnection = (KontalkConnection) connection;

        // setup version manager
        final VersionManager verMgr = VersionManager.getInstanceFor(connection);
        verMgr.setVersion(getString(R.string.app_name), SystemUtils.getVersionFullName(this));

        // setup presence and roster listener
        PresenceListener presenceListener = new PresenceListener(this);
        RosterListener rosterListener = new RosterListener(this);
        Roster roster = getRoster();
        roster.addRosterLoadedListener(rosterListener);
        roster.addRosterListener(rosterListener);
        roster.setRosterStore(mRosterStore);
        roster.addSubscribeListener(presenceListener);

        StanzaFilter filter;

        filter = new StanzaTypeFilter(Presence.class);
        connection.addAsyncStanzaListener(presenceListener, filter);

        filter = new StanzaTypeFilter(org.jivesoftware.smack.packet.Message.class);
        connection.addSyncStanzaListener(new MessageListener(this), filter);

        // this is used as a reply callback
        mLastActivityListener = new LastActivityListener();

        filter = new StanzaTypeFilter(Version.class);
        connection.addAsyncStanzaListener(new VersionListener(), filter);
    }

    @Override
    public void connected(final XMPPConnection connection) {
        // enable ping manager
        AndroidAdaptiveServerPingManager
            .getInstanceFor(connection, this)
            .setEnabled(true);

        // save the network we connected through
        mCurrentNetwork = SystemUtils.getCurrentNetworkName(this);

        synchronized (connection) {
            if (mPingFailedListener == null) {
                mPingFailedListener = new PingFailedListener() {
                    @Override
                    public void pingFailed() {
                        if (isStarted() && mConnection == connection) {
                            Log.v(TAG, "ping failed, restarting message center");
                            // restart message center
                            restart(getApplicationContext());
                        }
                    }
                };
                PingManager pingManager = PingManager.getInstanceFor(connection);
                pingManager.registerPingFailedListener(mPingFailedListener);
                pingManager.setPingInterval(0);
            }
        }
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        Log.v(TAG, "authenticated!");

        // add message ack listener
        if (mConnection.isSmEnabled()) {
            mConnection.removeAllStanzaIdAcknowledgedListeners();
        }
        else {
            Log.w(TAG, "stream management not available - disabling delivery receipts");
        }

        // we can release the message center now
        // this must be done before sending the presence since it's based on isHeld()
        mIdleHandler.release();

        // send presence
        sendPresence(mIdleHandler.isHeld() ? Presence.Mode.available : Presence.Mode.away);
        // clear upload service
        if (mUploadServices != null)
            mUploadServices.clear();
        // discovery
        discovery();

        // helper is not needed any more
        mHelper = null;

        BUS.removeStickyEvent(ConnectedEvent.class);
        BUS.postSticky(new ConnectedEvent());

        // we can now release any pending push notification
        Preferences.setLastPushNotification(-1);

        // force inactive state if needed
        mIdleHandler.forceInactiveIfNeeded();

        // update alarm manager
        AndroidAdaptiveServerPingManager
            .getInstanceFor(connection, this)
            .onConnectionCompleted();

        // request server key if needed
        Async.go(new Runnable() {
            @Override
            public void run() {
                final XMPPConnection conn = mConnection;
                if (conn != null && conn.isConnected()) {
                    Jid jid = conn.getXMPPServiceDomain();
                    if (Keyring.getPublicKey(MessageCenterService.this, jid.toString(), MyUsers.Keys.TRUST_UNKNOWN) == null) {
                        BUS.post(new PublicKeyRequest(jid));
                    }
                }
            }
        });

        // re-acquire the wakelock for a limited time to allow for messages to come
        // it will then be released automatically
        mWakeLock.acquire(WAIT_FOR_MESSAGES_DELAY);
    }

    void broadcast(String action) {
        broadcast(action, null, null);
    }

    void broadcast(String action, String extraName, String extraValue) {
        if (mLocalBroadcastManager != null) {
            Intent i = new Intent(action);
            if (extraName != null)
                i.putExtra(extraName, extraValue);

            mLocalBroadcastManager.sendBroadcast(i);
        }
    }

    /**
     * Discovers info and items.
     */
    private void discovery() {
        StanzaFilter filter;

        Jid to;
        try {
            to = JidCreate.domainBareFrom(mServer.getNetwork());
        }
        catch (XmppStringprepException e) {
            Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
            // report it because it's a big deal
            ReportingManager.logException(e);
            return;
        }

        DiscoverInfo info = new DiscoverInfo();
        info.setTo(to);
        filter = new StanzaIdFilter(info.getStanzaId());
        mConnection.addAsyncStanzaListener(new DiscoverInfoListener(this), filter);
        sendPacket(info);

        DiscoverItems items = new DiscoverItems();
        items.setTo(to);
        filter = new StanzaIdFilter(items.getStanzaId());
        mConnection.addAsyncStanzaListener(new DiscoverItemsListener(this), filter);
        sendPacket(items);
    }

    synchronized void active(boolean available) {
        final XMPPConnection connection = mConnection;
        if (connection != null) {
            cancelIdleAlarm();

            if (available) {
                if (ClientStateIndicationManager.isSupported(connection)) {
                    Log.d(TAG, "entering active state");
                    try {
                        ClientStateIndicationManager.active(connection);
                    }
                    catch (NotConnectedException e) {
                        return;
                    }
                    catch (InterruptedException e) {
                        return;
                    }
                }

                sendPresence(Presence.Mode.available);
                mInactive = false;
            }
            // test ping
            mIdleHandler.test();
        }
    }

    synchronized void inactive() {
        final XMPPConnection connection = mConnection;
        if (connection != null) {
            if (!mInactive) {
                if (ClientStateIndicationManager.isSupported(connection)) {
                    Log.d(TAG, "entering inactive state");
                    try {
                        ClientStateIndicationManager.inactive(connection);
                    }
                    catch (NotConnectedException e) {
                        cancelIdleAlarm();
                        return;
                    }
                    catch (InterruptedException e) {
                        cancelIdleAlarm();
                        return;
                    }
                }
                sendPresence(Presence.Mode.away);
            }

            setIdleAlarm();
            mInactive = true;
        }
    }

    boolean isInactive() {
        return mInactive;
    }

    boolean fastReply() {
        if (!isConnected()) return false;

        try {
            return PingManager.getInstanceFor(mConnection)
                .pingMyServer(false, FAST_PING_TIMEOUT);
        }
        catch (NotConnectedException e) {
            return false;
        }
        catch (InterruptedException e) {
            return false;
        }
    }

    long getLastReceivedStanza() {
        return mConnection != null ? mConnection.getLastStanzaReceived() : 0;
    }

    /**
     * Sends our initial presence.
     */
    private void sendPresence(Presence.Mode mode) {
        sendPacket(createPresence(mode));
    }

    private Presence createPresence(Presence.Mode mode) {
        String status = Preferences.getStatusMessage();
        Presence p = new Presence(Presence.Type.available);
        if (!TextUtils.isEmpty(status))
            p.setStatus(status);
        if (mode != null)
            p.setMode(mode);

        // TODO find a place for this
        p.addExtension(new CapsExtension("http://www.kontalk.org/", "none", "sha-1"));

        return p;
    }

    Roster getRoster() {
        return (mConnection != null) ? Roster.getInstanceFor(mConnection) : null;
    }

    private boolean isRosterLoaded() {
        Roster roster = getRoster();
        return roster != null && roster.isLoaded();
    }

    RosterEntry getRosterEntry(BareJid jid) {
        Roster roster = getRoster();
        return (roster != null) ? roster.getEntry(jid) : null;
    }

    private boolean isAuthorized(BareJid jid) {
        if (Authenticator.isSelfJID(this, jid))
            return true;
        RosterEntry entry = getRosterEntry(jid);
        return entry != null && isAuthorized(entry);
    }

    private boolean isAuthorized(RosterEntry entry) {
        return (isRosterEntrySubscribed(entry) || Authenticator.isSelfJID(this, entry.getJid()));
    }

    private boolean isRosterEntrySubscribed(RosterEntry entry) {
        return (entry != null && (entry.getType() == RosterPacket.ItemType.to || entry.getType() == RosterPacket.ItemType.both) &&
            !entry.isSubscriptionPending());
    }

    private PresenceEvent replyPresenceRequest(Roster roster, @Nullable RosterEntry entry, BareJid jid, String id) {
        PresenceEvent event;

        // asking our own presence
        if (Authenticator.isSelfJID(this, jid)) {
            return replyMyPresenceRequest(id);
        }

        // entry present and not pending subscription
        else if (isRosterEntrySubscribed(entry)) {
            // roster entry found, send presence probe
            Presence presence = roster.getPresence(jid);
            event = PresenceListener.createEvent(this, presence, entry, id);
        }
        else {
            // null type indicates no roster entry found or not authorized
            event = new NoPresenceEvent(jid, id);
        }

        return event;
    }

    private PresenceEvent replyMyPresenceRequest(String id) {
        return new UserOnlineEvent(mConnection.getUser(), null, 0,
            null, null, null, true, true, getMyFingerprint(), id);
    }

    private String getMyFingerprint() {
        try {
            PersonalKey key = Kontalk.get().getPersonalKey();
            return key.getFingerprint();
        }
        catch (Exception e) {
            // something bad happened
            Log.w(TAG, "unable to load personal key");
            return null;
        }
    }

    private void setUserPrivacy(BareJid to, PrivacyCommand command) {
        switch (command) {
            case ACCEPT: {
                // standard response: subscribed
                sendPacket(new Presence(to, Presence.Type.subscribed));
                // send a subscription request anyway
                sendPacket(new Presence(to, Presence.Type.subscribe));
                break;
            }
            case REJECT: {
                Presence unsub = new Presence(Presence.Type.unsubscribe);
                unsub.setTo(to);
                sendPacket(unsub);
                // will also block
            }
            case BLOCK:
                blockUser(to);
                break;
            case UNBLOCK:
                unblockUser(to);
                break;
        }

        // clear the request status
        ContentValues values = new ContentValues(1);
        values.put(Threads.REQUEST_STATUS, Threads.REQUEST_NONE);

        getContentResolver().update(Requests.CONTENT_URI,
            values, Threads.PEER + "=?", new String[]{to.toString()});
    }

    private void blockUser(final BareJid jid) {
        sendIqWithReply(BlockingCommand.block(jid.toString()), true, new StanzaListener() {
                @Override
                public void processStanza(Stanza packet) throws NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
                    if (packet instanceof IQ && ((IQ) packet).getType() == IQ.Type.result) {
                        UsersProvider.setBlockStatus(MessageCenterService.this, jid.toString(), true);

                        // invalidate cached contact
                        Contact.invalidate(jid.toString());

                        // post result
                        BUS.post(new UserBlockedEvent(jid));
                    }
                }
            }, null);
    }

    private void unblockUser(final BareJid jid) {
        sendIqWithReply(BlockingCommand.unblock(jid.toString()), true, new StanzaListener() {
            @Override
            public void processStanza(Stanza packet) throws NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
                if (packet instanceof IQ && ((IQ) packet).getType() == IQ.Type.result) {
                    UsersProvider.setBlockStatus(MessageCenterService.this, jid.toString(), false);

                    // invalidate cached contact
                    Contact.invalidate(jid.toString());

                    // post result
                    BUS.post(new UserUnblockedEvent(jid));
                }
            }
        }, null);
    }

    private void requestBlocklist(final String id) {
        IQ p = BlockingCommand.blocklist();
        if (id != null)
            p.setStanzaId(id);

        sendIqWithReply(p, true, new StanzaListener() {
            @Override
            public void processStanza(Stanza packet) throws NotConnectedException, InterruptedException, SmackException.NotLoggedInException {
                if (packet instanceof BlockingCommand) {
                    BlockingCommand blocklist = (BlockingCommand) packet;
                    List<String> blocked = blocklist.getItems();
                    BUS.post(new BlocklistEvent(blocked != null ? XMPPUtils
                        .parseJids(blocked) : new Jid[0], blocklist.getStanzaId()));
                }
            }
        }, new ExceptionCallback() {
            @Override
            public void processException(Exception exception) {
                BUS.post(new BlocklistEvent(exception, id));
            }
        });
    }

    private void sendChatState(SendChatStateRequest request) {
        Stanza packet;

        if (request.group) {
            GroupInfo groupInfo = MessagesProviderClient.getGroupInfo(this, request.to.toString());
            if (groupInfo == null) {
                Log.w(TAG, "group not found: " + request.to);
                return;
            }

            Jid[] toGroup = XMPPUtils.parseJids(MessagesProviderClient
                .getGroupMembers(this, request.to.toString(), 0));

            GroupController groupController = GroupControllerFactory
                .createController(groupInfo.getType(), mConnection, this);

            // check if we can send messages even with some members with no subscriptipn
            if (!groupController.canSendWithNoSubscription()) {
                for (Jid jid : toGroup) {
                    if (!isAuthorized(jid.asBareJid())) {
                        Log.i(TAG, "not subscribed to " + jid + ", not sending group message");
                        return;
                    }
                }
            }

            GroupCommand groupCommand = groupController.info();
            // FIXME careful to this, might need abstraction
            groupCommand.setMembers(toGroup);
            groupCommand.setGroupJid(groupInfo.getJid());

            packet = groupController.beforeEncryption(groupCommand, null);

            // TODO encryption

            // post-process for group delivery
            packet = groupController.afterEncryption(groupCommand, packet, packet);
        }
        else {
            // message stanza
            packet = new org.jivesoftware.smack.packet.Message(request.to,
                org.jivesoftware.smack.packet.Message.Type.chat);
        }

        packet.setStanzaId(request.id);
        packet.addExtension(new ChatStateExtension(request.chatState));

        sendPacket(packet);
    }

    private void sendMessage(SendMessageRequest request) {
        if (!isRosterLoaded()) {
            Log.d(TAG, "roster not loaded yet, not sending message");
            return;
        }

        CompositeMessage message = CompositeMessage.loadMessage(this, request.databaseId);
        if (message == null) {
            Log.w(TAG, "message not found: " + request.databaseId);
            return;
        }

        Conversation conv = Conversation.loadFromUserId(this, message.getPeer());
        if (conv == null) {
            // this is actually a bug
            ReportingManager.logException(new IllegalArgumentException("no conversation for message!"));
            Log.w(TAG, "unable to load conversation for encrypting message " + request.databaseId);
            return;
        }

        Jid to;
        // used for verifying isPaused()
        Jid convJid;
        Jid[] toGroup;
        GroupController groupController = null;
        GroupInfo groupInfo = null;

        final GroupComponent group = message.getComponent(GroupComponent.class);
        if (group != null) {
            groupInfo = group.getContent();

            toGroup = XMPPUtils.parseJids(conv.getGroupPeersForSending());
            convJid = groupInfo.getJid();

            groupController = GroupControllerFactory
                .createController(groupInfo.getType(), mConnection, this);
            // the to field will be filled by the group controller
            to = null;

            // check if we can send messages even with some members with no subscriptipn
            if (!groupController.canSendWithNoSubscription()) {
                for (Jid jid : toGroup) {
                    if (!isAuthorized(jid.asBareJid())) {
                        Log.i(TAG, "not subscribed to " + jid + ", not sending group message");
                        return;
                    }
                }
            }
        }
        else {
            to = JidCreate.fromOrThrowUnchecked(message.getPeer());
            toGroup = new Jid[]{to};
            convJid = to;
        }

        PersonalKey key;
        try {
            key = Kontalk.get().getPersonalKey();
        }
        catch (Exception pgpe) {
            Log.w(TAG, "no personal key available - not allowed to send messages");
            // warn user: message will not be sent
            if (MessagingNotification.isPaused(convJid)) {
                Toast.makeText(this, R.string.warn_no_personal_key,
                    Toast.LENGTH_LONG).show();
            }
            return;
        }

        // check if message is already pending
        final long msgId = request.databaseId;
        if (mWaitingReceipt.contains(msgId)) {
            Log.v(TAG, "message already queued and waiting - dropping");
            return;
        }

        final String id = message.getId();

        // hold on to message center while we send the message
        mIdleHandler.hold(false);

        Stanza m, originalStanza;

        // pre-process message for group delivery
        GroupCommand groupCommand = null;
        if (groupController != null) {
            GroupCommandComponent groupCmdComponent = message.getComponent(GroupCommandComponent.class);
            if (groupCmdComponent != null) {
                if (groupCmdComponent.isCreateCommand()) {
                    groupCommand = groupController.createGroup();
                    ((CreateGroupCommand) groupCommand).setSubject(groupInfo.getSubject());
                }
                else if (groupCmdComponent.isSetSubjectCommand()) {
                    groupCommand = groupController.setSubject();
                    ((SetSubjectCommand) groupCommand).setSubject(groupInfo.getSubject());
                }
                else if (groupCmdComponent.isAddOrRemoveCommand()) {
                    groupCommand = groupController.addRemoveMembers();
                    ((AddRemoveMembersCommand) groupCommand).setSubject(groupInfo.getSubject());
                    ((AddRemoveMembersCommand) groupCommand)
                        .setAddedMembers(XMPPUtils.parseJids(groupCmdComponent.getAddedMembers()));
                    ((AddRemoveMembersCommand) groupCommand)
                        .setRemovedMembers(XMPPUtils.parseJids(groupCmdComponent.getRemovedMembers()));
                }
                else if (groupCmdComponent.isPartCommand()) {
                    groupCommand = groupController.part();
                    ((PartCommand) groupCommand).setDatabaseId(msgId);
                }
            }

            if (groupCommand == null) {
                groupCommand = groupController.info();
            }

            // FIXME careful to this, might need abstraction
            groupCommand.setMembers(toGroup);
            groupCommand.setGroupJid(groupInfo.getJid());

            m = groupController.beforeEncryption(groupCommand, null);
        }
        else {
            // message stanza
            m = new org.jivesoftware.smack.packet.Message();
        }

        originalStanza = m;

        // if the group manager doesn't return a <message/> (e.g. MUC)
        boolean isMessage = (m instanceof org.jivesoftware.smack.packet.Message);

        if (to != null)
            m.setTo(to);

        // set message id
        m.setStanzaId(id);

        // request ack if we are sending a message to a non-group chat
        // TODO this will change when we'll support group chat ack
        boolean ackRequest = isMessage && groupController == null;

        if (isMessage) {
            org.jivesoftware.smack.packet.Message msg = (org.jivesoftware.smack.packet.Message) m;
            msg.setType(org.jivesoftware.smack.packet.Message.Type.chat);

            if (!(request instanceof SendDeliveryReceiptRequest)) {
                TextComponent body = message.getComponent(TextComponent.class);
                if (body != null) {
                    msg.setBody(body.getContent());
                }
                else {
                    HiddenTextComponent body2 = message.getComponent(HiddenTextComponent.class);
                    if (body2 != null) {
                        msg.setBody(body2.getContent());
                    }
                }

                // generate preview if needed
                AttachmentComponent attachment = message.getComponent(AttachmentComponent.class);
                if (attachment != null) {
                    if (attachment.getLocalUri() != null && attachment.getPreviewFile() != null) {
                        if (!attachment.getPreviewFile().isFile()) {
                            try {
                                MediaStorage.cacheThumbnail(this, attachment.getLocalUri(),
                                    attachment.getPreviewFile(), true);
                            }
                            catch (Exception e) {
                                Log.w(TAG, "unable to generate preview for media", e);
                            }
                        }

                        msg.addExtension(new BitsOfBinary(MediaStorage.THUMBNAIL_MIME_NETWORK,
                            attachment.getPreviewFile()));
                    }

                    // add download url if present
                    if (attachment.getFetchUrl() != null) {
                        // in this case we will need the length too
                        msg.addExtension(new OutOfBandData(attachment.getFetchUrl(),
                            attachment.getMime(), attachment.getLength(),
                            attachment.getSecurityFlags() != Coder.SECURITY_CLEARTEXT));
                    }
                }

                // add location data if present
                LocationComponent location = message.getComponent(LocationComponent.class);
                if (location != null) {
                    double lat = location.getLatitude();
                    double lon = location.getLongitude();
                    UserLocation userLocation = new UserLocation(lat, lon,
                        location.getText(), location.getStreet());
                    m.addExtension(userLocation);
                }

                // add referenced message if any
                InReplyToComponent inReplyTo = message.getComponent(InReplyToComponent.class);
                if (inReplyTo != null) {
                    ReferencedMessage referencedMsg = inReplyTo.getContent();
                    if (referencedMsg != null) {
                        DelayInformation fwdDelay = new DelayInformation(new Date(referencedMsg.getTimestamp()));

                        try {
                            org.jivesoftware.smack.packet.Message fwdMessage =
                                new org.jivesoftware.smack.packet.Message(referencedMsg.getPeer(),
                                    referencedMsg.getTextContent());
                            fwdMessage.setStanzaId(referencedMsg.getMessageId());

                            m.addExtension(new Forwarded(fwdDelay, fwdMessage));
                        }
                        catch (XmppStringprepException e) {
                            Log.w(TAG, "unable to parse referenced message JID: " + referencedMsg.getPeer(), e);
                            // this is serious, report it
                            ReportingManager.logException(e);
                        }
                    }
                }
            }

            if (message.getSecurityFlags() != Coder.SECURITY_CLEARTEXT) {
                byte[] toMessage = null;
                boolean encryptError = false;
                try {
                    Coder coder = Keyring.getEncryptCoder(this, mServer, key, SystemUtils.toString(toGroup));
                    if (coder != null) {

                        // no extensions, create a simple text version to save space
                        if (msg.getExtensions().size() == 0) {
                            if (!(request instanceof SendDeliveryReceiptRequest)) {
                                // a special case for delivery receipts whom doesn't have a body
                                // but we want to encrypt it for groups (extensions.size() > 0)
                                toMessage = coder.encryptText(msg.getBody());
                            }
                        }

                        // some extension, encrypt whole stanza just to be sure
                        else {
                            toMessage = coder.encryptStanza(msg.toXML(null));
                        }

                        if (toMessage != null) {
                            org.jivesoftware.smack.packet.Message encMsg =
                                new org.jivesoftware.smack.packet.Message(msg.getTo(), msg.getType());

                            encMsg.setBody(getString(R.string.text_encrypted));
                            encMsg.setStanzaId(m.getStanzaId());
                            encMsg.addExtension(new E2EEncryption(toMessage));

                            // save the unencrypted stanza for later
                            originalStanza = msg;
                            m = encMsg;
                        }
                    }
                }

                // FIXME there is some very ugly code here
                // FIXME notify just once per session (store in Kontalk instance?)

                catch (IllegalArgumentException noPublicKey) {
                    // warn user: message will be not sent
                    if (MessagingNotification.isPaused(convJid)) {
                        Toast.makeText(this, R.string.warn_no_public_key,
                            Toast.LENGTH_LONG).show();
                    }
                    encryptError = true;
                }
                catch (GeneralSecurityException e) {
                    // warn user: message will not be sent
                    if (MessagingNotification.isPaused(convJid)) {
                        Toast.makeText(this, R.string.warn_encryption_failed,
                            Toast.LENGTH_LONG).show();
                    }
                    encryptError = true;
                }

                if (encryptError) {
                    // message was not encrypted for some reason, mark it pending user review
                    MessageUpdater.forMessage(this, msgId)
                        .setStatus(Messages.STATUS_PENDING)
                        .commit();

                    mIdleHandler.release();
                    return;
                }
            }
        }

        // post-process for group delivery
        if (groupController != null) {
            m = groupController.afterEncryption(groupCommand, m, originalStanza);
        }

        if (isMessage) {
            // received receipt
            if (request instanceof SendDeliveryReceiptRequest) {
                m.addExtension(new DeliveryReceipt(message.getId()));
            }
            else {
                // add active chat state if this is a message
                m.addExtension(new ChatStateExtension(ChatState.active));

                // add receipt if necessary
                if (ackRequest)
                    DeliveryReceiptRequest.addTo((org.jivesoftware.smack.packet.Message) m);
            }

            sendMessage((org.jivesoftware.smack.packet.Message) m, msgId);
        }
        else {
            // no wake lock management in this case
            sendPacket(m);
        }

        // the real sendMessage has its own hold/release pair
        mIdleHandler.release();
    }

    /*
    @Deprecated
    private void sendMessage(Bundle data) {
        if (!isRosterLoaded()) {
            Log.d(TAG, "roster not loaded yet, not sending message");
            return;
        }

        final String groupJid = data.getString("org.kontalk.message.group.jid");
        Jid to;
        // used for verifying isPaused()
        Jid convJid;
        String[] toGroup;
        GroupController group = null;

        if (groupJid != null) {
            toGroup = data.getStringArray("org.kontalk.message.to");
            try {
                // TODO this should be discovered first
                to = JidCreate.from("multicast", mConnection.getXMPPServiceDomain(), "");
                convJid = JidCreate.from(groupJid);
            }
            catch (XmppStringprepException e) {
                Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
                // report it because it's a big deal
                ReportingManager.logException(e);
                return;
            }

            // TODO take type from data
            group = GroupControllerFactory
                .createController(KontalkGroupController.GROUP_TYPE, mConnection, this);

            // check if we can send messages even with some members with no subscriptipn
            if (!group.canSendWithNoSubscription()) {
                for (String jid : toGroup) {
                    try {
                        BareJid bareJid = JidCreate.bareFrom(jid);
                        if (!isAuthorized(bareJid)) {
                            Log.i(TAG, "not subscribed to " + jid + ", not sending group message");
                            return;
                        }
                    }
                    catch (XmppStringprepException e) {
                        Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
                        // report it because it's a big deal
                        ReportingManager.logException(e);
                        return;
                    }
                }
            }
        }
        else {
            try {
                to = JidCreate.from(data.getString("org.kontalk.message.to"));
            }
            catch (XmppStringprepException e) {
                Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
                // report it because it's a big deal
                ReportingManager.logException(e);
                return;
            }
            toGroup = new String[]{to.toString()};
            convJid = to;
        }

        PersonalKey key;
        try {
            key = Kontalk.get().getPersonalKey();
        }
        catch (Exception pgpe) {
            Log.w(TAG, "no personal key available - not allowed to send messages");
            // warn user: message will not be sent
            if (MessagingNotification.isPaused(convJid)) {
                Toast.makeText(this, R.string.warn_no_personal_key,
                    Toast.LENGTH_LONG).show();
            }
            return;
        }

        // check if message is already pending
        final long msgId = data.getLong("org.kontalk.message.msgId");
        if (mWaitingReceipt.contains(msgId)) {
            Log.v(TAG, "message already queued and waiting - dropping");
            return;
        }

        final String id = data.getString("org.kontalk.message.packetId");

        final boolean encrypt = data.getBoolean("org.kontalk.message.encrypt");
        final String mime = data.getString("org.kontalk.message.mime");
        String _mediaUri = data.getString("org.kontalk.message.media.uri");
        if (_mediaUri != null) {
            // take the first available upload service :)
            IUploadService uploadService = getUploadService();
            if (uploadService != null) {
                Uri preMediaUri = Uri.parse(_mediaUri);
                final String previewPath = data.getString("org.kontalk.message.preview.path");
                long fileLength;

                try {
                    // encrypt the file if necessary
                    if (encrypt) {
                        InputStream in = null;
                        try {
                            in = getContentResolver().openInputStream(preMediaUri);
                            File encrypted = MessageUtils.encryptFile(this, in, toGroup);
                            fileLength = encrypted.length();
                            preMediaUri = Uri.fromFile(encrypted);
                        }
                        finally {
                            SystemUtils.closeStream(in);
                        }
                    }
                    else {
                        fileLength = MediaStorage.getLength(this, preMediaUri);
                    }
                }
                catch (Exception e) {
                    Log.w(TAG, "error preprocessing media: " + preMediaUri, e);
                    // simulate upload error
                    UploadService.errorNotification(this,
                        getString(R.string.notify_ticker_upload_error),
                        getString(R.string.notify_text_upload_error));
                    return;
                }

                final Uri mediaUri = preMediaUri;

                // build a filename
                String filename = CompositeMessage.getFilename(mime, new Date());
                if (filename == null)
                    filename = MediaStorage.UNKNOWN_FILENAME;

                // media message - start upload service
                final String uploadTo = to.toString();
                final String[] uploadGroupTo = toGroup;
                uploadService.getPostUrl(filename, fileLength, mime, new IUploadService.UrlCallback() {
                    @Override
                    public void callback(String putUrl, String getUrl) {
                        // start upload intent service
                        // TODO move to UploadService.start static method
                        Intent i = new Intent(MessageCenterService.this, UploadService.class);
                        i.setData(mediaUri);
                        i.setAction(UploadService.ACTION_UPLOAD);
                        i.putExtra(UploadService.EXTRA_POST_URL, putUrl);
                        i.putExtra(UploadService.EXTRA_GET_URL, getUrl);
                        i.putExtra(UploadService.EXTRA_DATABASE_ID, msgId);
                        i.putExtra(UploadService.EXTRA_MESSAGE_ID, id);
                        i.putExtra(UploadService.EXTRA_MIME, mime);
                        // this will be used only for out of band data
                        i.putExtra(UploadService.EXTRA_ENCRYPT, encrypt);
                        i.putExtra(UploadService.EXTRA_PREVIEW_PATH, previewPath);
                        // delete original (actually it's the encrypted temp file) if we already encrypted it
                        i.putExtra(UploadService.EXTRA_DELETE_ORIGINAL, encrypt);
                        i.putExtra(UploadService.EXTRA_USER, groupJid != null ? uploadGroupTo : uploadTo);
                        if (groupJid != null)
                            i.putExtra(UploadService.EXTRA_GROUP, groupJid);
                        ContextCompat.startForegroundService(MessageCenterService.this, i);
                    }
                });

            }
            else {
                // TODO warn user about this problem
                Log.w(TAG, "no upload service - this shouldn't happen!");
            }
        }
        else {
            // hold on to message center while we send the message
            mIdleHandler.hold(false);

            Stanza m, originalStanza;

            // pre-process message for group delivery
            GroupCommand groupCommand = null;
            if (group != null) {
                int groupCommandId = data.getInt("org.kontalk.message.group.command", 0);
                switch (groupCommandId) {
                    case GROUP_COMMAND_PART:
                        groupCommand = group.part();
                        ((PartCommand) groupCommand).setDatabaseId(msgId);
                        // FIXME careful to this, might need abstraction
                        groupCommand.setMembers(toGroup);
                        groupCommand.setGroupJid(groupJid);
                        break;
                    case GROUP_COMMAND_CREATE: {
                        String subject = data.getString("org.kontalk.message.group.subject");
                        groupCommand = group.createGroup();
                        ((CreateGroupCommand) groupCommand).setSubject(subject);
                        groupCommand.setMembers(toGroup);
                        groupCommand.setGroupJid(groupJid);
                        break;
                    }
                    case GROUP_COMMAND_SUBJECT: {
                        String subject = data.getString("org.kontalk.message.group.subject");
                        groupCommand = group.setSubject();
                        ((SetSubjectCommand) groupCommand).setSubject(subject);
                        // FIXME careful to this, might need abstraction
                        groupCommand.setMembers(toGroup);
                        groupCommand.setGroupJid(groupJid);
                        break;
                    }
                    case GROUP_COMMAND_MEMBERS: {
                        String subject = data.getString("org.kontalk.message.group.subject");
                        String[] added = data.getStringArray("org.kontalk.message.group.add");
                        String[] removed = data.getStringArray("org.kontalk.message.group.remove");
                        groupCommand = group.addRemoveMembers();
                        ((AddRemoveMembersCommand) groupCommand).setSubject(subject);
                        ((AddRemoveMembersCommand) groupCommand).setAddedMembers(added);
                        ((AddRemoveMembersCommand) groupCommand).setRemovedMembers(removed);
                        groupCommand.setMembers(toGroup);
                        groupCommand.setGroupJid(groupJid);
                        break;
                    }
                    default:
                        groupCommand = group.info();
                        // FIXME careful to this, might need abstraction
                        groupCommand.setMembers(toGroup);
                        groupCommand.setGroupJid(groupJid);
                }

                m = group.beforeEncryption(groupCommand, null);
            }
            else {
                // message stanza
                m = new org.jivesoftware.smack.packet.Message();
            }

            originalStanza = m;
            boolean isMessage = (m instanceof org.jivesoftware.smack.packet.Message);

            if (to != null)
                m.setTo(to);

            // set message id
            m.setStanzaId(id);

            // message server id
            String serverId = isMessage ? data.getString("org.kontalk.message.ack") : null;
            boolean ackRequest = isMessage &&
                !data.getBoolean("org.kontalk.message.standalone", false) &&
                group == null;

            if (isMessage) {
                org.jivesoftware.smack.packet.Message msg = (org.jivesoftware.smack.packet.Message) m;
                msg.setType(org.jivesoftware.smack.packet.Message.Type.chat);
                String body = data.getString("org.kontalk.message.body");
                if (body != null)
                    msg.setBody(body);

                String fetchUrl = data.getString("org.kontalk.message.fetch.url");

                // generate preview if needed
                String _previewUri = data.getString("org.kontalk.message.preview.uri");
                String previewFilename = data.getString("org.kontalk.message.preview.path");
                if (_previewUri != null && previewFilename != null) {
                    File previewPath = new File(previewFilename);
                    if (!previewPath.isFile()) {
                        Uri previewUri = Uri.parse(_previewUri);
                        try {
                            MediaStorage.cacheThumbnail(this, previewUri, previewPath, true);
                        }
                        catch (Exception e) {
                            Log.w(TAG, "unable to generate preview for media", e);
                        }
                    }

                    m.addExtension(new BitsOfBinary(MediaStorage.THUMBNAIL_MIME_NETWORK, previewPath));
                }

                // add download url if present
                if (fetchUrl != null) {
                    // in this case we will need the length too
                    long length = data.getLong("org.kontalk.message.length");
                    m.addExtension(new OutOfBandData(fetchUrl, mime, length, encrypt));
                }

                // add location data if present
                if (data.containsKey("org.kontalk.message.geo_lat")) {
                    double lat = data.getDouble("org.kontalk.message.geo_lat");
                    double lon = data.getDouble("org.kontalk.message.geo_lon");
                    UserLocation userLocation = new UserLocation(lat, lon);

                    if (data.containsKey("org.kontalk.message.geo_text")) {
                        String text = data.getString("org.kontalk.message.geo_text");
                        userLocation.setText(text);
                    }
                    if (data.containsKey("org.kontalk.message.geo_street")) {
                        String street = data.getString("org.kontalk.message.geo_street");
                        userLocation.setStreet(street);
                    }

                    m.addExtension(userLocation);
                }

                // add referenced message if any
                long inReplyToId = data.getLong("org.kontalk.message.inReplyTo", 0);
                if (inReplyToId > 0) {
                    ReferencedMessage referencedMsg = ReferencedMessage.load(this, inReplyToId);
                    if (referencedMsg != null) {
                        DelayInformation fwdDelay = new DelayInformation(new Date(referencedMsg.getTimestamp()));

                        try {
                            org.jivesoftware.smack.packet.Message fwdMessage =
                                new org.jivesoftware.smack.packet.Message(referencedMsg.getPeer(),
                                    referencedMsg.getTextContent());
                            fwdMessage.setStanzaId(referencedMsg.getMessageId());

                            m.addExtension(new Forwarded(fwdDelay, fwdMessage));
                        }
                        catch (XmppStringprepException e) {
                            Log.w(TAG, "unable to parse referenced message JID: " + referencedMsg.getPeer(), e);
                            // this is serious, report it
                            ReportingManager.logException(e);
                        }
                    }
                }

                if (encrypt) {
                    byte[] toMessage = null;
                    try {
                        Coder coder = Keyring.getEncryptCoder(this, mServer, key, toGroup);
                        if (coder != null) {

                            // no extensions, create a simple text version to save space
                            if (m.getExtensions().size() == 0) {
                                toMessage = coder.encryptText(body);
                            }

                            // some extension, encrypt whole stanza just to be sure
                            else {
                                toMessage = coder.encryptStanza(m.toXML(null));
                            }

                            org.jivesoftware.smack.packet.Message encMsg =
                                new org.jivesoftware.smack.packet.Message(m.getTo(),
                                    ((org.jivesoftware.smack.packet.Message) m).getType());

                            encMsg.setBody(getString(R.string.text_encrypted));
                            encMsg.setStanzaId(m.getStanzaId());
                            encMsg.addExtension(new E2EEncryption(toMessage));

                            // save the unencrypted stanza for later
                            originalStanza = m;
                            m = encMsg;
                        }
                    }

                    // FIXME there is some very ugly code here
                    // FIXME notify just once per session (store in Kontalk instance?)

                    catch (IllegalArgumentException noPublicKey) {
                        // warn user: message will be not sent
                        if (MessagingNotification.isPaused(convJid)) {
                            Toast.makeText(this, R.string.warn_no_public_key,
                                Toast.LENGTH_LONG).show();
                        }
                    }
                    catch (GeneralSecurityException e) {
                        // warn user: message will not be sent
                        if (MessagingNotification.isPaused(convJid)) {
                            Toast.makeText(this, R.string.warn_encryption_failed,
                                Toast.LENGTH_LONG).show();
                        }
                    }

                    if (toMessage == null) {
                        // message was not encrypted for some reason, mark it pending user review
                        MessageUpdater.forMessage(this, msgId)
                            .setStatus(Messages.STATUS_PENDING)
                            .commit();

                        mIdleHandler.release();
                        return;
                    }
                }
            }

            // post-process for group delivery
            if (group != null) {
                m = group.afterEncryption(groupCommand, m, originalStanza);
            }

            if (isMessage) {
                // received receipt
                if (serverId != null) {
                    m.addExtension(new DeliveryReceipt(serverId));
                }
                else {
                    ChatState chatState;
                    try {
                        chatState = ChatState.valueOf(data.getString(EXTRA_CHAT_STATE));
                        // add chat state if message is not a received receipt
                        m.addExtension(new ChatStateExtension(chatState));
                    }
                    catch (Exception ignored) {
                    }

                    // standalone: no receipt
                    if (ackRequest)
                        DeliveryReceiptRequest.addTo((org.jivesoftware.smack.packet.Message) m);
                }

                sendMessage((org.jivesoftware.smack.packet.Message) m, msgId);
            }
            else {
                // no wake lock management in this case
                sendPacket(m);
            }

            // the real sendMessage has its own hold/release pair
            mIdleHandler.release();
        }
    }
    */

    private void ensureUploadServices() {
        if (mUploadServices == null)
            mUploadServices = new ArrayList<>(2);
    }

    void addUploadService(IUploadService service) {
        ensureUploadServices();
        mUploadServices.add(service);
        BUS.post(new UploadServiceFoundEvent());
    }

    void addUploadService(IUploadService service, int priority) {
        ensureUploadServices();
        mUploadServices.add(priority, service);
        BUS.post(new UploadServiceFoundEvent());
    }

    /**
     * Returns the first available upload service post URL.
     */
    private IUploadService getUploadService() {
        return (mUploadServices != null && mUploadServices.size() > 0) ?
            mUploadServices.get(0) : null;
    }

    /** Dangerous check for push notifications availability. */
    private static boolean isPushNotificationsAvailable(Context context) {
        if (!Preferences.getPushNotificationsEnabled(context))
            return false;

        IPushService psm = PushServiceManager.getInstance(context);
        return (psm != null &&
            (psm.isServiceAvailable() || psm.isRegisteredOnServer() || psm.isRegistered()));
    }

    /**
     * If the user ignored our request to not be optimized, we must become a foreground service.
     * @return true if the user decided to battery optimize us and with no hope of push notifications
     */
    public static boolean mustSetForeground(Context context) {
        return !SystemUtils.isIgnoringBatteryOptimizations(context) &&
            !isPushNotificationsAvailable(context);
    }

    /** Return true if the service should be started in foreground. */
    public static boolean shouldStartInForeground(Context context) {
        return mustSetForeground(context) ||
            Preferences.getForegroundServiceEnabled(context);
    }

    private void setForeground(boolean enable) {
        if (enable) {
            startForeground(NOTIFICATION_ID_FOREGROUND,
                MessagingNotification.buildForegroundNotification(this));
        }
        else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        }
    }

    private void beginKeyPairRegeneration(String passphrase) {
        if (mKeyPairRegenerator == null) {
            try {
                // lock message center while doing this
                hold(this, true);
                mKeyPairRegenerator = new RegenerateKeyPairListener(this, passphrase);
                mKeyPairRegenerator.run();
            }
            catch (Exception e) {
                Log.e(TAG, "unable to initiate keypair regeneration", e);
                // TODO warn user

                endKeyPairRegeneration();
            }
        }
    }

    void endKeyPairRegeneration() {
        if (mKeyPairRegenerator != null) {
            mKeyPairRegenerator.abort();
            mKeyPairRegenerator = null;
            // release message center
            release(this);
        }
    }

    private void beginKeyPairImport(Uri keypack, String passphrase) {
        if (mKeyPairImporter == null) {
            try {
                ZipInputStream zip = new ZipInputStream(getContentResolver()
                    .openInputStream(keypack));

                mKeyPairImporter = new ImportKeyPairListener(this, zip, passphrase);
                mKeyPairImporter.run();
            }
            catch (Exception e) {
                Log.e(TAG, "unable to initiate keypair import", e);
                Toast.makeText(this, R.string.err_import_keypair_failed,
                    Toast.LENGTH_LONG).show();

                endKeyPairImport();
            }
        }
    }

    void endKeyPairImport() {
        if (mKeyPairImporter != null) {
            mKeyPairImporter.abort();
            mKeyPairImporter = null;
        }
    }

    private void beginUploadPrivateKey(String exportPasshrase) {
        try {
            String passphrase = Kontalk.get().getCachedPassphrase();
            byte[] privateKeyData = Authenticator.getPrivateKeyExportData(this, passphrase, exportPasshrase);
            PrivateKeyUploadListener uploadListener = new PrivateKeyUploadListener(this, privateKeyData);
            uploadListener.uploadAndListen();
        }
        catch (PGPException | IOException e) {
            Log.e(TAG, "unable to load private key data", e);
        }
    }

    private boolean canTest() {
        long now = SystemClock.elapsedRealtime();
        return ((now - mLastTest) > MIN_TEST_INTERVAL);
    }

    public boolean canConnect() {
        return SystemUtils.isNetworkConnectionAvailable(this) && !isOfflineMode(this);
    }

    public boolean isConnected() {
        return mConnection != null && mConnection.isAuthenticated();
    }

    public boolean isConnecting() {
        return mHelper != null;
    }

    public static boolean isOfflineMode(Context context) {
        return Preferences.getOfflineMode();
    }

    public static EventBus bus() {
        return BUS;
    }

    private static Intent getBaseIntent(Context context) {
        return new Intent(context, MessageCenterService.class);
    }

    private static Intent getStartIntent(Context context) {
        return getBaseIntent(context);
    }

    /** @deprecated Must go away ASAP. */
    @Deprecated
    public static void startService(Context context, Intent intent) {
        startForegroundIfNeeded(context, intent);
    }

    private static void startForegroundIfNeeded(Context context, Intent intent) {
        if (shouldStartInForeground(context)) {
            startForegroundForced(context, intent);
        }
        else {
            try {
                context.startService(intent);
            }
            catch (IllegalStateException e) {
                // for some reason we are starting in the background
                // but we are not allowed to do so. Just start in foreground.
                startForegroundForced(context, intent);
            }
        }
    }

    private static void startForegroundForced(Context context, Intent intent) {
        intent.putExtra(EXTRA_FOREGROUND, true);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void start(Context context) {
        // check for offline mode
        if (isOfflineMode(context)) {
            Log.d(TAG, "offline mode enable - abort service start");
            return;
        }

        // check for network state
        if (SystemUtils.isNetworkConnectionAvailable(context)) {
            Log.d(TAG, "starting message center");
            final Intent intent = getStartIntent(context);

            startForegroundIfNeeded(context, intent);
        }
        else {
            Log.d(TAG, "network not available or background data disabled - abort service start");
        }
    }

    public static void stop(Context context) {
        Log.d(TAG, "shutting down message center");
        context.stopService(getBaseIntent(context));
    }

    public static void restart(Context context) {
        Log.d(TAG, "restarting message center");
        Intent i = getBaseIntent(context);
        i.setAction(ACTION_RESTART);
        startForegroundIfNeeded(context, i);
    }

    public static void test(Context context, boolean checkNetwork) {
        Log.d(TAG, "testing message center connection");
        Intent i = getBaseIntent(context);
        i.setAction(ACTION_TEST);
        i.putExtra(EXTRA_TEST_CHECK_NETWORK, checkNetwork);
        startForegroundIfNeeded(context, i);
    }

    public static void ping(Context context) {
        Log.d(TAG, "ping message center connection");
        Intent i = getBaseIntent(context);
        i.setAction(ACTION_PING);
        startForegroundIfNeeded(context, i);
    }

    /**
     * Tells the message center we are holding on to it, preventing shutdown for
     * inactivity.
     *
     * @param activate true to wake up from CSI and send become available.
     */
    public static void hold(final Context context, boolean activate) {
        // increment the application counter
        Kontalk.get().hold();

        Intent i = getBaseIntent(context);
        i.setAction(ACTION_HOLD);
        i.putExtra("org.kontalk.activate", activate);
        startForegroundIfNeeded(context, i);
    }

    /**
     * Tells the message center we are releasing it, allowing shutdown for
     * inactivity.
     */
    public static void release(final Context context) {
        // decrement the application counter
        Kontalk.get().release();

        Intent i = getBaseIntent(context);
        i.setAction(ACTION_RELEASE);
        startForegroundIfNeeded(context, i);
    }

    /**
     * Broadcasts our presence to the server.
     */
    public static void updateStatus(String status) {
        BUS.post(new UpdateStatusRequest(status));
    }

    public static String messageId() {
        return StringUtils.randomString(30);
    }

    @Deprecated
    public static void regenerateKeyPair(final Context context, String passphrase) {
        Intent i = getBaseIntent(context);
        i.setAction(MessageCenterService.ACTION_REGENERATE_KEYPAIR);
        i.putExtra(EXTRA_PASSPHRASE, passphrase);
        startForegroundIfNeeded(context, i);
    }

    @Deprecated
    public static void importKeyPair(final Context context, Uri keypack, String passphrase) {
        Intent i = getBaseIntent(context);
        i.setAction(MessageCenterService.ACTION_IMPORT_KEYPAIR);
        i.putExtra(EXTRA_KEYPACK, keypack);
        i.putExtra(EXTRA_PASSPHRASE, passphrase);
        startForegroundIfNeeded(context, i);
    }

    /**
     * Starts the push notifications registration process.
     */
    public static void enablePushNotifications(Context context) {
        Intent i = getBaseIntent(context);
        i.setAction(ACTION_PUSH_START);
        startForegroundIfNeeded(context, i);
    }

    /**
     * Starts the push notifications unregistration process.
     */
    public static void disablePushNotifications(Context context) {
        Intent i = getBaseIntent(context);
        i.setAction(ACTION_PUSH_STOP);
        startForegroundIfNeeded(context, i);
    }

    /**
     * Caches the given registration Id for use with push notifications.
     */
    public static void registerPushNotifications(Context context, String registrationId) {
        Intent i = getBaseIntent(context);
        i.setAction(ACTION_PUSH_REGISTERED);
        i.putExtra(PUSH_REGISTRATION_ID, registrationId);
        startForegroundIfNeeded(context, i);
    }

    public void setPushNotifications(boolean enabled) {
        mPushNotifications = enabled;
        if (mPushNotifications) {
            if (mPushRegistrationId == null)
                pushRegister();
        }
        else {
            pushUnregister();
        }
    }

    void pushRegister() {
        if (sPushSenderId != null) {
            if (mPushService != null && mPushService.isServiceAvailable()) {
                // senderId will be given by serverinfo if any
                mPushRegistrationId = mPushService.getRegistrationId();
                if (TextUtils.isEmpty(mPushRegistrationId))
                    // start registration
                    mPushService.register(sPushListener, sPushSenderId);
                else
                    // already registered - send registration id to server
                    setPushRegistrationId(mPushRegistrationId);
            }
        }
    }

    private void pushUnregister() {
        if (mPushService != null) {
            if (mPushService.isRegistered())
                // start unregistration
                mPushService.unregister(sPushListener);
            else
                // force unregistration
                setPushRegistrationId(null);
        }
    }

    private void setPushRegistrationId(String regId) {
        mPushRegistrationId = regId;

        // notify the server about the change
        if (canConnect() && isConnected()) {
            if (regId != null) {
                sendPushRegistration(regId);
            }
            else {
                sendPushUnregistration();
            }
        }
    }

    private void sendPushRegistration(final String regId) {
        IQ iq = PushRegistration.register(DEFAULT_PUSH_PROVIDER, regId);
        try {
            iq.setTo(JidCreate.from("push", mServer.getNetwork(), ""));
        }
        catch (XmppStringprepException e) {
            Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
            // report it because it's a big deal
            ReportingManager.logException(e);
            return;
        }

        sendIqWithReply(iq, true, new StanzaListener() {
            @Override
            public void processStanza(Stanza packet) throws NotConnectedException {
                if (mPushService != null)
                    mPushService.setRegisteredOnServer(regId != null);
            }
        }, null);
    }

    private void sendPushUnregistration() {
        IQ iq = PushRegistration.unregister(DEFAULT_PUSH_PROVIDER);
        try {
            iq.setTo(JidCreate.from("push", mServer.getNetwork(), ""));
        }
        catch (XmppStringprepException e) {
            Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
            // report it because it's a big deal
            ReportingManager.logException(e);
            return;
        }

        sendIqWithReply(iq, true, new StanzaListener() {
            @Override
            public void processStanza(Stanza packet) throws NotConnectedException {
                if (mPushService != null)
                    mPushService.setRegisteredOnServer(false);
            }
        }, null);
    }

    public static String getPushSenderId() {
        return sPushSenderId;
    }

    private void scheduleStartJob() {
        if (SystemUtils.supportsJobScheduler())
            StartMessageCenterJob.schedule(this);
        // in theory, lack of the job scheduler means old behavior
        // so we don't need this because the NetworkStateReceiver will work
    }

    void setWakeupAlarm() {
        long delay = Preferences.getWakeupTimeMillis(this,
            MIN_WAKEUP_TIME);

        // start message center pending intent
        PendingIntent pi = PendingIntent.getService(
            getApplicationContext(), 0, getStartIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        // we don't use the shared alarm manager instance here
        // since this can happen after the service has begun to stop
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delay, pi);
    }

    private void ensureIdleAlarm() {
        if (mIdleIntent == null) {
            Intent i = getStartIntent(this);
            i.setAction(ACTION_IDLE);
            mIdleIntent = PendingIntent.getService(
                getApplicationContext(), 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    void cancelIdleAlarm() {
        // synchronized access since we might get a call from IdleThread
        final AlarmManager alarms = mAlarmManager;
        if (alarms != null) {
            ensureIdleAlarm();
            alarms.cancel(mIdleIntent);
        }
    }

    private void setIdleAlarm() {
        // even if this is called from IdleThread, we don't need to synchronize
        // because at that point mConnection is null so we never get called
        long delay = Preferences.getIdleTimeMillis(this, 0);
        if (delay > 0) {
            ensureIdleAlarm();
            mAlarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, delay, mIdleIntent);
        }
    }

}

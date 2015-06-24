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
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.RosterLoadedListener;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.caps.packet.CapsExtension;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.csi.ClientStateIndicationManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.iqlast.packet.LastActivity;
import org.jivesoftware.smackx.iqversion.VersionManager;
import org.jivesoftware.smackx.iqversion.packet.Version;
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptRequest;
import org.jxmpp.util.XmppStringUtils;

import android.accounts.Account;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
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
import android.util.Log;
import android.widget.Toast;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.authenticator.LegacyAuthentication;
import org.kontalk.client.BitsOfBinary;
import org.kontalk.client.BlockingCommand;
import org.kontalk.client.E2EEncryption;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.KontalkConnection;
import org.kontalk.client.PublicKeyPublish;
import org.kontalk.client.RosterMatch;
import org.kontalk.client.OutOfBandData;
import org.kontalk.client.PushRegistration;
import org.kontalk.client.ServerlistCommand;
import org.kontalk.client.SmackInitializer;
import org.kontalk.client.VCard4;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.MyMessages.Threads;
import org.kontalk.provider.MyMessages.Threads.Requests;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.service.UploadService;
import org.kontalk.service.XMPPConnectionHelper;
import org.kontalk.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;


/**
 * The Message Center Service.
 * Use {@link Intent}s to deliver commands (via {@link Context#startService}).
 * Service will broadcast intents when certain events occur.
 * @author Daniele Ricci
 * @version 4.0
 */
public class MessageCenterService extends Service implements ConnectionHelperListener {
    public static final String TAG = MessageCenterService.class.getSimpleName();

    static {
        SmackConfiguration.DEBUG = BuildConfig.DEBUG;
    }

    public static final String ACTION_PACKET = "org.kontalk.action.PACKET";
    public static final String ACTION_HOLD = "org.kontalk.action.HOLD";
    public static final String ACTION_RELEASE = "org.kontalk.action.RELEASE";
    public static final String ACTION_RESTART = "org.kontalk.action.RESTART";
    public static final String ACTION_TEST = "org.kontalk.action.TEST";
    public static final String ACTION_MESSAGE = "org.kontalk.action.MESSAGE";
    public static final String ACTION_PUSH_START = "org.kontalk.push.START";
    public static final String ACTION_PUSH_STOP = "org.kontalk.push.STOP";
    public static final String ACTION_PUSH_REGISTERED = "org.kontalk.push.REGISTERED";
    public static final String ACTION_IDLE = "org.kontalk.action.IDLE";
    public static final String ACTION_PING = "org.kontalk.action.PING";

    /** Request the roster. */
    public static final String ACTION_ROSTER = "org.kontalk.action.ROSTER";

    /** Request roster match. */
    public static final String ACTION_ROSTER_MATCH = "org.kontalk.action.ROSTER_MATCH";

    /**
     * Broadcasted when we are connected and authenticated to the server.
     * Send this intent to receive the same as a broadcast if connected. */
    public static final String ACTION_CONNECTED = "org.kontalk.action.CONNECTED";

    /**
     * Broadcasted when the roster has been loaded.
     * Send this intent to receive the same as a broadcast if the roster has
     * already been loaded.
     */
    public static final String ACTION_ROSTER_LOADED = "org.kontalk.action.ROSTER_LOADED";

    /**
     * Broadcasted when a presence stanza is received.
     * Send this intent to broadcast presence.
     * Send this intent with type="probe" to request a presence in the roster.
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
     * Broadcasted when key pair regeneration has completed.
     */
    public static final String ACTION_REGENERATE_KEYPAIR = "org.kontalk.action.REGEN_KEYPAIR";

    /** Commence key pair import. */
    public static final String ACTION_IMPORT_KEYPAIR = "org.kontalk.action.IMPORT_KEYPAIR";

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
     * Broadcasted when receiving a public key.
     * Send this intent to request a public key.
     */
    public static final String ACTION_PUBLICKEY = "org.kontalk.action.PUBLICKEY";

    /**
     * Broadcasted when receiving the server list.
     * Send this intent to request the server list.
     */
    public static final String ACTION_SERVERLIST = "org.kontalk.action.SERVERLIST";

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

    /**
     * Broadcasted when receiving version information.
     * Send this intent to request version information to an entity.
     */
    public static final String ACTION_VERSION = "org.kontalk.action.VERSION";

    // common parameters
    public static final String EXTRA_PACKET_ID = "org.kontalk.packet.id";
    public static final String EXTRA_TYPE = "org.kontalk.packet.type";

    // use with org.kontalk.action.PACKET
    public static final String EXTRA_PACKET = "org.kontalk.packet";
    public static final String EXTRA_PACKET_GROUP = "org.kontalk.packet.group";
    public static final String EXTRA_STAMP = "org.kontalk.packet.delay";

    // use with org.kontalk.action.PRESENCE/SUBSCRIBED
    public static final String EXTRA_FROM = "org.kontalk.stanza.from";
    public static final String EXTRA_TO = "org.kontalk.stanza.to";
    public static final String EXTRA_STATUS = "org.kontalk.presence.status";
    public static final String EXTRA_SHOW = "org.kontalk.presence.show";
    public static final String EXTRA_PRIORITY = "org.kontalk.presence.priority";
    public static final String EXTRA_PRIVACY = "org.kontalk.presence.privacy";
    public static final String EXTRA_FINGERPRINT = "org.kontalk.presence.fingerprint";
    public static final String EXTRA_SUBSCRIBED_FROM = "org.kontalk.presence.subscribed.from";
    public static final String EXTRA_SUBSCRIBED_TO = "org.kontalk.presence.subscribed.to";

    // use with org.kontalk.action.ROSTER
    public static final String EXTRA_JIDLIST = "org.kontalk.roster.JIDList";

    // use with org.kontalk.action.LAST_ACTIVITY
    public static final String EXTRA_SECONDS = "org.kontalk.last.seconds";

    // use with org.kontalk.action.VCARD
    public static final String EXTRA_PUBLIC_KEY = "org.kontalk.vcard.publicKey";

    // used with org.kontalk.action.BLOCKLIST
    public static final String EXTRA_BLOCKLIST = "org.kontalk.blocklist";

    // used with org.kontalk.action.IMPORT_KEYPAIR
    public static final String EXTRA_KEYPACK = "org.kontalk.keypack";
    public static final String EXTRA_PASSPHRASE = "org.kontalk.passphrase";

    // used with org.kontalk.action.VERSION
    public static final String EXTRA_VERSION_NAME = "org.kontalk.version.name";
    public static final String EXTRA_VERSION_NUMBER = "org.kontalk.version.number";

    // used for org.kontalk.presence.privacy.action extra
    /** Accept subscription. */
    public static final int PRIVACY_ACCEPT = 0;
    /** Block user. */
    public static final int PRIVACY_BLOCK = 1;
    /** Unblock user. */
    public static final int PRIVACY_UNBLOCK = 2;
    /** Reject subscription and block. */
    public static final int PRIVACY_REJECT = 3;

    /** Message URI. */
    public static final String EXTRA_MESSAGE = "org.kontalk.message";

    // other
    public static final String PUSH_REGISTRATION_ID = "org.kontalk.PUSH_REGISTRATION_ID";
    private static final String DEFAULT_PUSH_PROVIDER = "gcm";

    /** How much time before a wakeup alarm triggers. */
    public final static int DEFAULT_WAKEUP_TIME = 900000;
    /** Minimal wakeup time. */
    public final static int MIN_WAKEUP_TIME = 300000;

    /** How much time to wait to idle the message center (default 5 mins). */
    private final static int DEFAULT_IDLE_TIME = 5*60*1000;

    /** Normal ping tester timeout. */
    private static final int SLOW_PING_TIMEOUT = 10000;
    /** Fast ping tester timeout. */
    private static final int FAST_PING_TIMEOUT = 3000;
    /** Minimal interval between connection tests (5 mins). */
    private static final int MIN_TEST_INTERVAL = 5*60*1000;

    static final IPushListener sPushListener = PushServiceManager.getDefaultListener();

    /** Push service instance. */
    private IPushService mPushService;
    /** Push notifications enabled flag. */
    boolean mPushNotifications;
    /** Server push sender id. This is static so the {@link IPushListener} can see it. */
    static String sPushSenderId;
    /** Push registration id. */
    private String mPushRegistrationId;
    /** Flag marking a currently ongoing push registration cycle (unregister/register) */
    boolean mPushRegistrationCycle;

    // created in onCreate
    private WakeLock mWakeLock;
    private WakeLock mPingLock;
    LocalBroadcastManager mLocalBroadcastManager;
    private AlarmManager mAlarmManager;

    private PingFailedListener mPingFailedListener;

    /** Cached last used server. */
    EndpointServer mServer;
    /** The connection helper instance. */
    private XMPPConnectionHelper mHelper;
    /** The connection instance. */
    KontalkConnection mConnection;
    /** My username (account name). */
    String mMyUsername;

    /** Supported upload services. */
    Map<String, String> mUploadServices;

    /** Roster store. */
    private SQLiteRosterStore mRosterStore;

    /** Service handler. */
    Handler mHandler;

    /** Idle handler. */
    IdleConnectionHandler mIdleHandler;
    /** Inactive state flag (for CSI). */
    private boolean mInactive;
    /** Timestamp of last use of {@link #ACTION_TEST}. */
    private long mLastTest;
    /** Pending intent for idle signaling. */
    private PendingIntent mIdleIntent;

    private boolean mFirstStart = true;

    /** Messages waiting for server receipt (packetId: internalStorageId). */
    Map<String, Long> mWaitingReceipt = new HashMap<String, Long>();

    private RegenerateKeyPairListener mKeyPairRegenerator;
    private ImportKeyPairListener mKeyPairImporter;

    static final class IdleConnectionHandler extends Handler implements IdleHandler {
        /** Idle signal. */
        private static final int MSG_IDLE = 1;
        /** Inactive signal (for CSI). */
        private static final int MSG_INACTIVE = 2;
        /** Test signal. */
        private static final int MSG_TEST = 3;

        /** How much time to wait to enter inactive state. */
        private final static int INACTIVE_TIME = 30000;

        /** A reference to the message center. */
        private WeakReference<MessageCenterService> s;
        /** Reference counter. */
        private int mRefCount;

        public IdleConnectionHandler(MessageCenterService service, int refCount, Looper looper) {
            super(looper);
            s = new WeakReference<MessageCenterService>(service);
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
                // push notifications unavailable: set up an alarm for next time
                if (service.mPushRegistrationId == null) {
                    service.setWakeupAlarm();
                }

                Log.d(TAG, "shutting down message center due to inactivity");
                service.stopSelf();

                return true;
            }

            else if (msg.what == MSG_INACTIVE && !service.isInactive()) {
                service.inactive();
                return true;
            }

            else if (msg.what == MSG_TEST) {
                long now = System.currentTimeMillis();
                if ((now - service.getLastReceivedStanza()) >= FAST_PING_TIMEOUT) {
                    if (!service.fastReply()) {
                        Log.v(TAG, "test ping failed");
                        AdaptiveServerPingManager.pingFailed(service.mConnection);
                        restart(service.getApplicationContext());
                    }
                    else {
                        AdaptiveServerPingManager.pingSuccess(service.mConnection);
                    }
                }
                return true;
            }

            return false;
        }

        /** Resets the idle timer. */
        public void reset(int refCount) {
            mRefCount = refCount;
            reset();
        }

        /** Resets the idle timer. */
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

        public void hold() {
            mRefCount++;
            if (mRefCount > 0) {
                MessageCenterService service = s.get();
                if (service != null && service.isInactive() && service.isConnected()) {
                    service.active();
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
                        queueInactive();
                    }
                });
            }
        }

        public void quit() {
            abortIdle();
            getLooper().quit();
        }

        /** Aborts any idle message because we are using the service or quitting. */
        private void abortIdle() {
            Looper.myQueue().removeIdleHandler(IdleConnectionHandler.this);
            removeMessages(MSG_IDLE);
            removeMessages(MSG_INACTIVE);
            MessageCenterService service = s.get();
            if (service != null)
                service.cancelIdleAlarm();
        }

        public void queueInactiveIfNeeded() {
            post(new Runnable() {
                public void run() {
                    if (mRefCount <= 0 && !hasMessages(MSG_INACTIVE)) {
                        queueInactive();
                    }
                }
            });
        }

        public void forceInactive() {
            MessageCenterService service = s.get();
            if (service != null && !service.isInactive()) {
                removeMessages(MSG_INACTIVE);
                service.inactive();
            }
        }

        private void queueInactive() {
            // send inactive state message only if connected
            MessageCenterService service = s.get();
            if (service != null && service.isConnected()) {
                sendMessageDelayed(obtainMessage(MSG_INACTIVE), INACTIVE_TIME);
            }
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
    } ;

    @Override
    public void onCreate() {
        configure();

        // activate ping manager
        AdaptiveServerPingManager.onCreate(this);

        // create the roster store
        mRosterStore = new SQLiteRosterStore(this);

        // create the global wake lock
        PowerManager pwr = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pwr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Kontalk.TAG);
        mWakeLock.setReferenceCounted(false);
        mPingLock = pwr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Kontalk.TAG + "-Ping");
        mPingLock.setReferenceCounted(false);

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

    private void createIdleHandler() {
        HandlerThread thread = new HandlerThread("IdleThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        int refCount = Kontalk.get(this).getReferenceCounter();
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

    void sendPacket(Stanza packet) {
        sendPacket(packet, true);
    }

    /**
     * Sends a packet to the connection if found.
     * @param bumpIdle true if the idle handler must be notified of this event
     */
    void sendPacket(Stanza packet, boolean bumpIdle) {
        // reset idler if requested
        if (bumpIdle) mIdleHandler.reset();

        if (mConnection != null) {
            try {
                mConnection.sendStanza(packet);
            }
            catch (NotConnectedException e) {
                // ignored
                Log.v(TAG, "not connected. Dropping packet " + packet);
            }
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
        quit(false);
        // deactivate ping manager
        AdaptiveServerPingManager.onDestroy();
        // destroy roster store
        mRosterStore.onDestroy();
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
    }

    public boolean isStarted() {
        return mLocalBroadcastManager != null;
    }

    private synchronized void quit(boolean restarting) {
        if (!restarting) {
            // quit the idle handler
            mIdleHandler.quit();
            mIdleHandler = null;
        }
        else {
            // reset the reference counter
            int refCount = ((Kontalk) getApplicationContext()).getReferenceCounter();
            mIdleHandler.reset(refCount);
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
            // disable ping manager
            AdaptiveServerPingManager.getInstanceFor(mConnection).setEnabled(false);
            PingManager.getInstanceFor(mConnection)
                .unregisterPingFailedListener(mPingFailedListener);
            // this is because of NetworkOnMainThreadException
            new DisconnectThread(mConnection).start();
            mConnection = null;
        }

        // clear cached data from contacts
        Contact.invalidateData();

        // stop any key pair regeneration service
        if (!LegacyAuthentication.isUpgrading())
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
        private final AbstractXMPPConnection mConn;

        public DisconnectThread(AbstractXMPPConnection conn) {
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
        // stop immediately
        if (isOfflineMode(this))
            stopSelf();

        if (intent != null) {
            String action = intent.getAction();

            // proceed to start only if network is available
            boolean canConnect = canConnect();
            boolean isConnected = isConnected();
            boolean doConnect = false;

            // TODO convert actions to classes

            if (ACTION_HOLD.equals(action)) {
                if (!mFirstStart)
                    mIdleHandler.hold();
                doConnect = true;
            }

            else if (ACTION_RELEASE.equals(action)) {
                mIdleHandler.release();
            }

            else if (ACTION_IDLE.equals(action)) {
                mIdleHandler.idle();
            }

            else if (ACTION_PUSH_START.equals(action)) {
                setPushNotifications(true);
            }

            else if (ACTION_PUSH_STOP.equals(action)) {
                setPushNotifications(false);
            }

            else if (ACTION_PUSH_REGISTERED.equals(action)) {
                String regId = intent.getStringExtra(PUSH_REGISTRATION_ID);
                // registration cycle under way
                if (regId == null && mPushRegistrationCycle) {
                    mPushRegistrationCycle = false;
                    pushRegister();
                }
                else
                    setPushRegistrationId(regId);
            }

            else if (ACTION_REGENERATE_KEYPAIR.equals(action)) {
                doConnect = true;
                beginKeyPairRegeneration();
            }

            else if (ACTION_IMPORT_KEYPAIR.equals(action)) {
                // zip file with keys
                Uri file = intent.getParcelableExtra(EXTRA_KEYPACK);
                // passphrase to decrypt files
                String passphrase = intent.getStringExtra(EXTRA_PASSPHRASE);
                beginKeyPairImport(file, passphrase);
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

            else if (ACTION_TEST.equals(action)) {
                if (isConnected()) {
                    if (canTest()) {
                        mLastTest = SystemClock.elapsedRealtime();
                        mIdleHandler.test();
                    }
                }
                else {
                    doConnect = canConnect;
                }
            }

            else if (ACTION_PING.equals(action)) {
                if (isConnected()) {
                    // acquire a wake lock
                    mPingLock.acquire();
                    final XMPPConnection connection = mConnection;
                    final PingManager pingManager = PingManager.getInstanceFor(connection);
                    Async.go(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (pingManager.pingMyServer(true, SLOW_PING_TIMEOUT)) {
                                    AdaptiveServerPingManager.pingSuccess(connection);
                                }
                                else {
                                    AdaptiveServerPingManager.pingFailed(connection);
                                }
                            }
                            catch (SmackException.NotConnectedException e) {
                                // ignored
                            }
                            finally {
                                // release the wake lock
                                if (mPingLock != null)
                                    mPingLock.release();
                            }
                        }
                    }, "PingServerIfNecessary (" + mConnection.getConnectionCounter() + ')');
                }
                else {
                    doConnect = canConnect;
                }
            }

            else if (ACTION_MESSAGE.equals(action)) {
                if (canConnect && isConnected)
                    sendMessage(intent.getExtras());
            }

            else if (ACTION_ROSTER.equals(action) || ACTION_ROSTER_MATCH.equals(action)) {
                if (canConnect && isConnected) {
                    Stanza iq;

                    if (ACTION_ROSTER_MATCH.equals(action)) {
                        iq = new RosterMatch();
                        String[] list = intent.getStringArrayExtra(EXTRA_JIDLIST);

                        for (String item : list) {
                            ((RosterMatch) iq).addItem(item);
                        }

                        // directed to the probe component
                        iq.setTo(XmppStringUtils.completeJidFrom("probe", mServer.getNetwork()));
                    }
                    else {
                        iq = new RosterPacket();
                    }

                    String id = intent.getStringExtra(EXTRA_PACKET_ID);
                    iq.setStanzaId(id);
                    // iq default type is get

                    sendPacket(iq);
                }
            }

            else if (ACTION_ROSTER_LOADED.equals(action)) {
                if (isConnected && isRosterLoaded()) {
                    broadcast(ACTION_ROSTER_LOADED);
                }
            }

            else if (ACTION_PRESENCE.equals(action)) {
                if (canConnect && isConnected) {
                    final String id = intent.getStringExtra(EXTRA_PACKET_ID);
                    String type = intent.getStringExtra(EXTRA_TYPE);
                    String to = intent.getStringExtra(EXTRA_TO);

                    if ("probe".equals(type)) {
                        // probing is actually looking into the roster
                        Roster roster = getRoster();

                        if (to == null) {
                            for (RosterEntry entry : roster.getEntries()) {
                                broadcastPresence(roster, entry, id);
                            }

                            // broadcast our own presence
                            broadcastMyPresence(id);
                        }
                        else {
                            broadcastPresence(roster, to, id);
                        }
                    }
                    else {
                        String show = intent.getStringExtra(EXTRA_SHOW);
                        Presence p = new Presence(type != null ? Presence.Type.valueOf(type) : Presence.Type.available);
                        p.setStanzaId(id);
                        p.setTo(to);
                        if (intent.hasExtra(EXTRA_PRIORITY))
                            p.setPriority(intent.getIntExtra(EXTRA_PRIORITY, 0));
                        p.setStatus(intent.getStringExtra(EXTRA_STATUS));
                        if (show != null)
                            p.setMode(Presence.Mode.valueOf(show));

                        sendPacket(p);
                    }

                }
            }

            else if (ACTION_LAST_ACTIVITY.equals(action)) {
                if (canConnect && isConnected) {
                    LastActivity p = new LastActivity();

                    p.setStanzaId(intent.getStringExtra(EXTRA_PACKET_ID));
                    p.setTo(intent.getStringExtra(EXTRA_TO));

                    sendPacket(p);
                }
            }

            else if (ACTION_VCARD.equals(action)) {
                if (canConnect && isConnected) {
                    VCard4 p = new VCard4();
                    p.setTo(intent.getStringExtra(EXTRA_TO));

                    sendPacket(p);
                }
            }

            else if (ACTION_PUBLICKEY.equals(action)) {
                if (canConnect && isConnected) {
                    String to = intent.getStringExtra(EXTRA_TO);
                    if (to != null) {
                        // request public key for a specific user
                        PublicKeyPublish p = new PublicKeyPublish();
                        p.setStanzaId(intent.getStringExtra(EXTRA_PACKET_ID));
                        p.setTo(to);

                        sendPacket(p);
                    }
                    else {
                        // request public keys for the whole roster
                        Collection<RosterEntry> buddies = getRoster().getEntries();
                        for (RosterEntry buddy : buddies) {
                            if (isRosterEntrySubscribed(buddy)) {
                                PublicKeyPublish p = new PublicKeyPublish();
                                p.setStanzaId(intent.getStringExtra(EXTRA_PACKET_ID));
                                p.setTo(buddy.getUser());

                                sendPacket(p);
                            }
                        }

                        // request our own public key (odd eh?)
                        PublicKeyPublish p = new PublicKeyPublish();
                        p.setStanzaId(intent.getStringExtra(EXTRA_PACKET_ID));
                        p.setTo(XmppStringUtils.parseBareJid(mConnection.getUser()));
                        sendPacket(p);
                    }
                }
            }

            else if (ACTION_SERVERLIST.equals(action)) {
                if (canConnect && isConnected) {
                    ServerlistCommand p = new ServerlistCommand();
                    p.setTo(XmppStringUtils.completeJidFrom("network", mServer.getNetwork()));

                    StanzaFilter filter = new StanzaIdFilter(p.getStanzaId());
                    // TODO cache the listener (it shouldn't change)
                    mConnection.addAsyncStanzaListener(new StanzaListener() {
                        public void processPacket(Stanza packet) throws NotConnectedException {
                            Intent i = new Intent(ACTION_SERVERLIST);
                            List<String> _items = ((ServerlistCommand.ServerlistCommandData) packet)
                                .getItems();
                            if (_items != null && _items.size() != 0 && packet.getError() == null) {
                                String[] items = new String[_items.size()];
                                _items.toArray(items);

                                i.putExtra(EXTRA_FROM, packet.getFrom());
                                i.putExtra(EXTRA_JIDLIST, items);
                            }
                            mLocalBroadcastManager.sendBroadcast(i);
                        }
                    }, filter);

                    sendPacket(p);
                }
            }

            else if (ACTION_SUBSCRIBED.equals(action)) {
                if (canConnect && isConnected) {

                    sendSubscriptionReply(intent.getStringExtra(EXTRA_TO),
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

            else if (ACTION_VERSION.equals(action)) {
                if (isConnected) {
                    Version version = new Version(intent.getStringExtra(EXTRA_TO));
                    version.setStanzaId(intent.getStringExtra(EXTRA_PACKET_ID));
                    sendPacket(version);
                }
            }

            else {
                // no command means normal service start, connect if not connected
                doConnect = true;
            }

            if (canConnect && doConnect)
                createConnection();

            // no reason to exist
            if (!canConnect && !doConnect && !isConnected && !isConnecting())
                stopSelf();

            mFirstStart = false;
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
            mPushNotifications = Preferences.getPushNotificationsEnabled(this) &&
                mPushService.isServiceAvailable();
            // reset waiting messages
            mWaitingReceipt.clear();

            mInactive = false;

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
    public void authenticationFailed() {
        // fire up a notification explaining the situation
        MessagingNotification.authenticationError(this);
    }

    @Override
    public void reconnectingIn(int seconds) {
        // not used
    }

    @Override
    public void reconnectionFailed(Exception error) {
        // not used
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
    public synchronized void created(final XMPPConnection connection) {
        Log.v(TAG, "connection created.");
        mConnection = (KontalkConnection) connection;

        // setup version manager
        final VersionManager verMgr = VersionManager.getInstanceFor(connection);
        verMgr.setVersion(getString(R.string.app_name), SystemUtils.getVersionFullName(this));

        // setup roster
        Roster roster = getRoster();
        roster.addRosterLoadedListener(new RosterLoadedListener() {
            @Override
            public void onRosterLoaded(Roster roster) {
                // resend failed and pending messages
                resendPendingMessages(false);
                // resend failed and pending received receipts
                resendPendingReceipts();
                // roster has been loaded
                broadcast(ACTION_ROSTER_LOADED);
            }
        });
        roster.setRosterStore(mRosterStore);

        // enable ping manager
        AdaptiveServerPingManager.getInstanceFor(connection).setEnabled(true);
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

        StanzaFilter filter;

        filter = new StanzaTypeFilter(Presence.class);
        connection.addAsyncStanzaListener(new PresenceListener(this), filter);

        filter = new StanzaTypeFilter(RosterMatch.class);
        connection.addAsyncStanzaListener(new RosterMatchListener(this), filter);

        filter = new StanzaTypeFilter(org.jivesoftware.smack.packet.Message.class);
        connection.addSyncStanzaListener(new MessageListener(this), filter);

        filter = new StanzaTypeFilter(LastActivity.class);
        connection.addAsyncStanzaListener(new LastActivityListener(this), filter);

        filter = new StanzaTypeFilter(Version.class);
        connection.addAsyncStanzaListener(new VersionListener(this), filter);

        filter = new StanzaTypeFilter(PublicKeyPublish.class);
        connection.addAsyncStanzaListener(new PublicKeyListener(this), filter);
    }

    @Override
    public void connected(XMPPConnection connection) {
        // not used.
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {
        Log.v(TAG, "authenticated!");

        // add message ack listener
        if (mConnection.isSmEnabled()) {
            mConnection.addStanzaAcknowledgedListener(new MessageAckListener(this));
        }
        else {
            Log.w(TAG, "stream management not available - disabling delivery receipts");
        }

        // send presence
        sendPresence();
        // discovery
        discovery();
        // pending messages and receipts will be sent when roster will be loaded
        // send pending subscription replies
        sendPendingSubscriptionReplies();

        // helper is not needed any more
        mHelper = null;

        broadcast(ACTION_CONNECTED);

        // we can now release any pending push notification
        Preferences.setLastPushNotification(this, -1);

        // queue inactive message if needed
        mIdleHandler.queueInactiveIfNeeded();

        // update alarm manager
        AdaptiveServerPingManager.onConnected(mConnection);

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

        StanzaFilter filter = new StanzaIdFilter(info.getStanzaId());
        mConnection.addAsyncStanzaListener(new DiscoverInfoListener(this), filter);
        sendPacket(info);
    }

    private void active() {
        if (mConnection != null && ClientStateIndicationManager.isSupported(mConnection)) {
            Log.d(TAG, "entering active state");
            cancelIdleAlarm();
            try {
                ClientStateIndicationManager.active(mConnection);
                mInactive = false;
                // test ping
                mIdleHandler.test();
            }
            catch (NotConnectedException e) {
                // ignored
            }
        }
    }

    private void inactive() {
        if (mConnection != null && ClientStateIndicationManager.isSupported(mConnection)) {
            Log.d(TAG, "entering inactive state");
            try {
                ClientStateIndicationManager.inactive(mConnection);
                setIdleAlarm();
                mInactive = true;
            }
            catch (NotConnectedException e) {
                cancelIdleAlarm();
            }
        }
    }

    private boolean isInactive() {
        return mInactive;
    }

    private boolean fastReply() {
        if (!isConnected()) return false;

        try {
            return PingManager.getInstanceFor(mConnection)
                .pingMyServer(false, FAST_PING_TIMEOUT);
        }
        catch (NotConnectedException e) {
            return false;
        }
    }

    private long getLastReceivedStanza() {
        return mConnection != null ? mConnection.getLastStanzaReceived() : 0;
    }

    /** Sends our initial presence. */
    private void sendPresence() {
        sendPacket(createPresence());
    }

    private Presence createPresence() {
        String status = Preferences.getStatusMessage(this);
        Presence p = new Presence(Presence.Type.available);
        if (status != null)
            p.setStatus(status);

        // TODO find a place for this
        p.addExtension(new CapsExtension("http://www.kontalk.org/", "none", "sha-1"));

        return p;
    }

    /**
     * Queries for pending messages and send them through.
     * @param retrying if true, we are retrying to send media messages after
     * receiving upload info (non-media messages will be filtered out)
     */
    void resendPendingMessages(boolean retrying) {
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
                Messages.MESSAGE_ID,
                Messages.PEER,
                Messages.BODY_CONTENT,
                Messages.SECURITY_FLAGS,
                Messages.ATTACHMENT_MIME,
                Messages.ATTACHMENT_LOCAL_URI,
                Messages.ATTACHMENT_FETCH_URL,
                Messages.ATTACHMENT_PREVIEW_PATH,
                Messages.ATTACHMENT_LENGTH,
                Messages.ATTACHMENT_COMPRESS,
                // TODO Messages.ATTACHMENT_SECURITY_FLAGS,
            },
            filter.toString(),
            null, Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String msgId = c.getString(1);
            String peer = c.getString(2);
            byte[] textContent = c.getBlob(3);
            int securityFlags = c.getInt(4);
            String attMime = c.getString(5);
            String attFileUri = c.getString(6);
            String attFetchUrl = c.getString(7);
            String attPreviewPath = c.getString(8);
            long attLength = c.getLong(9);
            int compress = c.getInt(10);
            // TODO int attSecurityFlags = c.getInt(11);

            // media message encountered and no upload service available - delay message
            if (attFileUri != null && attFetchUrl == null && getUploadService() == null && !retrying) {
                Log.w(TAG, "no upload info received yet, delaying media message");
                continue;
            }

            Bundle b = new Bundle();
            // mark as retrying
            b.putBoolean("org.kontalk.message.retrying", true);

            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.packetId", msgId);
            b.putString("org.kontalk.message.to", peer);
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
                b.putInt("org.kontalk.message.compress", compress);
            }

            Log.v(TAG, "resending pending message " + id);
            sendMessage(b);
        }

        c.close();
    }

    void resendPendingReceipts() {
        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
            new String[] {
                Messages._ID,
                Messages.MESSAGE_ID,
                Messages.PEER,
            },
            Messages.DIRECTION + " = " + Messages.DIRECTION_IN + " AND " +
            Messages.STATUS + " = " + Messages.STATUS_INCOMING,
            null, Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String msgId = c.getString(1);
            String peer = c.getString(2);

            Bundle b = new Bundle();

            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.packetId", msgId);
            b.putString("org.kontalk.message.to", peer);
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
            String to = c.getString(0);
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

            sendSubscriptionReply(to, null, action);
        }

        c.close();
    }

    Roster getRoster() {
        return (mConnection != null) ? Roster.getInstanceFor(mConnection) : null;
    }

    private boolean isRosterLoaded() {
        Roster roster = getRoster();
        return roster != null && roster.isLoaded();
    }

    RosterEntry getRosterEntry(String jid) {
        Roster roster = getRoster();
        return (roster != null) ? roster.getEntry(jid) : null;
    }

    private boolean isAuthorized(String jid) {
        RosterEntry entry = getRosterEntry(jid);
        return (isRosterEntrySubscribed(entry) || Authenticator.isSelfJID(this, jid));
    }

    private boolean isRosterEntrySubscribed(RosterEntry entry) {
        return (entry != null && (entry.getType() == RosterPacket.ItemType.to || entry.getType() == RosterPacket.ItemType.both) &&
            entry.getStatus() != RosterPacket.ItemStatus.SUBSCRIPTION_PENDING);
    }

    private void broadcastPresence(Roster roster, RosterEntry entry, String id) {
        broadcastPresence(roster, entry, entry.getUser(), id);
    }

    private void broadcastPresence(Roster roster, String jid, String id) {
        broadcastPresence(roster, roster.getEntry(jid), jid, id);
    }

    private void broadcastPresence(Roster roster, RosterEntry entry, String jid, String id) {
        Intent i;
        // entry present and not pending subscription
        if (isRosterEntrySubscribed(entry) || Authenticator.isSelfJID(this, jid)) {
            // roster entry found, look for presence
            Presence presence = roster.getPresence(jid);
            i = PresenceListener.createIntent(this, presence, entry);
        }
        else {
            // null type indicates no roster entry found or not authorized
            i = new Intent(ACTION_PRESENCE);
            i.putExtra(EXTRA_FROM, jid);
        }

        // to keep track of request-reply
        i.putExtra(EXTRA_PACKET_ID, id);
        mLocalBroadcastManager.sendBroadcast(i);
    }

    /** A special method to broadcast our own presence. */
    private void broadcastMyPresence(String id) {
        Presence presence = createPresence();
        presence.setFrom(mConnection.getUser());

        Intent i = PresenceListener.createIntent(this, presence, null);
        i.putExtra(EXTRA_FINGERPRINT, getMyFingerprint());
        i.putExtra(EXTRA_SUBSCRIBED_FROM, true);
        i.putExtra(EXTRA_SUBSCRIBED_TO, true);

        // to keep track of request-reply
        i.putExtra(EXTRA_PACKET_ID, id);
        mLocalBroadcastManager.sendBroadcast(i);
    }

    private String getMyFingerprint() {
        try {
            PersonalKey key = Kontalk.get(this).getPersonalKey();
            return key.getFingerprint();
        }
        catch (Exception e) {
            // something bad happened
            Log.w(TAG, "unable to load personal key");
            return null;
        }
    }

    private void sendSubscriptionReply(String to, String packetId, int action) {

        if (action == PRIVACY_ACCEPT) {
            // standard response: subscribed
            Presence p = new Presence(Presence.Type.subscribed);
            p.setStanzaId(packetId);
            p.setTo(to);
            sendPacket(p);

            // send a subscription request anyway
            p = new Presence(Presence.Type.subscribe);
            p.setTo(to);
            sendPacket(p);
        }

        else if (action == PRIVACY_BLOCK || action == PRIVACY_UNBLOCK || action == PRIVACY_REJECT) {
            sendPrivacyListCommand(to, action);
        }

        // clear the request status
        ContentValues values = new ContentValues(1);
        values.put(Threads.REQUEST_STATUS, Threads.REQUEST_NONE);

        getContentResolver().update(Requests.CONTENT_URI,
            values, CommonColumns.PEER + "=?", new String[]{to});
    }

    private void sendPrivacyListCommand(final String to, final int action) {
        IQ p;

        if (action == PRIVACY_BLOCK || action == PRIVACY_REJECT) {
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

        if (action == PRIVACY_REJECT) {
            // send unsubscribed too
            Presence unsub = new Presence(Presence.Type.unsubscribe);
            unsub.setTo(to);
            sendPacket(unsub);
        }

        // setup packet filter for response
        StanzaFilter filter = new StanzaIdFilter(p.getStanzaId());
        StanzaListener listener = new StanzaListener() {
            public void processPacket(Stanza packet) {

                if (packet instanceof IQ && ((IQ) packet).getType() == IQ.Type.result) {
                    UsersProvider.setBlockStatus(MessageCenterService.this,
                        to, action == PRIVACY_BLOCK || action == PRIVACY_REJECT);

                    // invalidate cached contact
                    Contact.invalidate(to);

                    // broadcast result
                    broadcast((action == PRIVACY_BLOCK || action == PRIVACY_REJECT) ?
                        ACTION_BLOCKED : ACTION_UNBLOCKED,
                        EXTRA_FROM, to);
                }

            }
        };
        mConnection.addAsyncStanzaListener(listener, filter);

        // send IQ
        sendPacket(p);
    }

    private void requestBlocklist() {
        Stanza p = BlockingCommand.blocklist();
        String packetId = p.getStanzaId();

        // listen for response (TODO cache the listener, it shouldn't change)
        StanzaFilter idFilter = new StanzaIdFilter(packetId);
        mConnection.addAsyncStanzaListener(new StanzaListener() {
            public void processPacket(Stanza packet) {
                // we don't need this listener anymore
                mConnection.removeAsyncStanzaListener(this);

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
        if (!isRosterLoaded()) {
            Log.d(TAG, "roster not loaded yet, not sending message");
            return;
        }

        boolean retrying = data.getBoolean("org.kontalk.message.retrying");
        String to = data.getString("org.kontalk.message.to");

        if (!isAuthorized(to)) {
            Log.i(TAG, "not subscribed to " + to + ", not sending message");
            // warn user: message will not be sent
            if (!retrying && to.equalsIgnoreCase(MessagingNotification.getPaused())) {
                Toast.makeText(this, R.string.warn_not_subscribed,
                    Toast.LENGTH_LONG).show();
            }
            return;
        }

        PersonalKey key;
        try {
            key = ((Kontalk) getApplicationContext()).getPersonalKey();
        }
        catch (Exception pgpe) {
            Log.w(TAG, "no personal key available - not allowed to send messages");
            // warn user: message will not be sent
            if (to.equalsIgnoreCase(MessagingNotification.getPaused())) {
                Toast.makeText(this, R.string.warn_no_personal_key,
                    Toast.LENGTH_LONG).show();
            }
            return;
        }

        // check if message is already pending
        long msgId = data.getLong("org.kontalk.message.msgId");
        if (mWaitingReceipt.containsValue(msgId)) {
            Log.v(TAG, "message already queued and waiting - dropping");
            return;
        }

        String id = data.getString("org.kontalk.message.packetId");

        boolean encrypt = data.getBoolean("org.kontalk.message.encrypt");
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
                // compress ratio (if any)
                int compress = data.getInt("org.kontalk.message.compress");

                // start upload intent service
                Intent i = new Intent(this, UploadService.class);
                i.setData(mediaUri);
                i.setAction(UploadService.ACTION_UPLOAD);
                i.putExtra(UploadService.EXTRA_POST_URL, postUrl);
                i.putExtra(UploadService.EXTRA_DATABASE_ID, msgId);
                i.putExtra(UploadService.EXTRA_MESSAGE_ID, id);
                i.putExtra(UploadService.EXTRA_MIME, mime);
                i.putExtra(UploadService.EXTRA_ENCRYPT, encrypt);
                i.putExtra(UploadService.EXTRA_PREVIEW_PATH, previewPath);
                i.putExtra(UploadService.EXTRA_COMPRESS, compress);
                i.putExtra(UploadService.EXTRA_USER, to);
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
            if (to != null) m.setTo(to);

            // set message id
            m.setStanzaId(id);
            if (msgId > 0)
                mWaitingReceipt.put(id, msgId);

            String body = data.getString("org.kontalk.message.body");
            if (body != null)
                m.setBody(body);

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
                    catch (IOException e) {
                        Log.w(TAG, "unable to generate preview for media", e);
                    }
                }

                m.addExtension(new BitsOfBinary(MediaStorage.THUMBNAIL_MIME_NETWORK, previewPath));
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
                m.addExtension(new OutOfBandData(fetchUrl, mime, length, encrypt));
            }

            if (encrypt) {
                byte[] toMessage = null;
                try {
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

                        encMsg.setBody(getString(R.string.text_encrypted));
                        encMsg.setStanzaId(m.getStanzaId());
                        encMsg.addExtension(new E2EEncryption(toMessage));

                        m = encMsg;
                    }
                }

                // FIXME there is some very ugly code here
                // FIXME notify just once per session (store in Kontalk instance?)

                catch (IllegalArgumentException noPublicKey) {
                    // warn user: message will be not sent
                    if (to.equalsIgnoreCase(MessagingNotification.getPaused())) {
                        Toast.makeText(this, R.string.warn_no_public_key,
                            Toast.LENGTH_LONG).show();
                    }
                }

                catch (GeneralSecurityException e) {
                    // warn user: message will not be sent
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
                    if (msgId > 0)
                        mWaitingReceipt.remove(id);
                    mIdleHandler.release();
                    return;
                }
            }

            // message server id
            String serverId = data.getString("org.kontalk.message.ack");
            boolean ackRequest = !data.getBoolean("org.kontalk.message.standalone", false);

            // received receipt
            if (serverId != null) {
                m.addExtension(new DeliveryReceipt(serverId));
            }
            else {
                // add chat state if message is not a received receipt
                if (chatState != null)
                    m.addExtension(new ChatStateExtension(chatState));

                // standalone message: no receipt
                if (ackRequest)
                    DeliveryReceiptRequest.addTo(m);
            }

            sendPacket(m);

            // no ack request, release message center immediately
            if (!ackRequest)
                mIdleHandler.release();
        }
    }

    /** Process an incoming message. */
    Uri incoming(CompositeMessage msg) {
        final String sender = msg.getSender(true);

        // save to local storage
        ContentValues values = new ContentValues();
        values.put(Messages.MESSAGE_ID, msg.getId());
        values.put(Messages.PEER, sender);

        MessageUtils.fillContentValues(values, msg);

        values.put(Messages.STATUS, Messages.STATUS_INCOMING);
        values.put(Messages.UNREAD, true);
        values.put(Messages.NEW, true);
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
        final Context context = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
            UsersProvider.markRegistered(context, sender);
            }
        }).start();

        // fire notification only if message was actually inserted to database
        if (msgUri != null && !sender.equalsIgnoreCase(MessagingNotification.getPaused())) {
            // update notifications (delayed)
            MessagingNotification.delayedUpdateMessagesNotification(getApplicationContext(), true);
        }

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
                mKeyPairRegenerator = new RegenerateKeyPairListener(this);
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
        }
    }

    /**
     * Used by {@link XMPPConnectionHelper} to retrieve the keyring that will
     * be used for the next login while upgrading from legacy.
     */
    @Override
    public PGPKeyPairRingProvider getKeyPairRingProvider() {
        return mKeyPairRegenerator;
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
                // TODO warn user

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

    private boolean canTest() {
        long now = SystemClock.elapsedRealtime();
        return ((now - mLastTest) > MIN_TEST_INTERVAL);
    }

    public boolean canConnect() {
        return isNetworkConnectionAvailable(this) && !isOfflineMode(this);
    }

    public boolean isConnected() {
        return mConnection != null && mConnection.isAuthenticated();
    }

    public boolean isConnecting() {
        return mHelper != null;
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
        return new Intent(context, MessageCenterService.class);
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
        context.startService(i);
    }

    public static void test(Context context) {
        Log.d(TAG, "testing message center connection");
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_TEST);
        context.startService(i);
    }

    public static void ping(Context context) {
        Log.d(TAG, "ping message center connection");
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PING);
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

    /** Broadcasts our presence to the server. */
    public static void updateStatus(final Context context) {
        // FIXME this is what sendPresence already does
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PRESENCE);
        i.putExtra(EXTRA_STATUS, Preferences.getStatusMessage(context));
        context.startService(i);
    }

    /** Sends a chat state message. */
    public static void sendChatState(final Context context, String to, ChatState state) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.chatState", state.name());
        i.putExtra("org.kontalk.message.standalone", true);
        context.startService(i);
    }

    /** Sends a text message. */
    public static void sendTextMessage(final Context context, String to, String text, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", TextComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.body", text);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    /** Sends a binary message. */
    public static void sendBinaryMessage(final Context context,
            String to,
            String mime, Uri localUri, long length, String previewPath,
            boolean encrypt, int compress,
        long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.media.uri", localUri.toString());
        i.putExtra("org.kontalk.message.length", length);
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.compress", compress);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void sendUploadedMedia(final Context context, String to,
            String mime, Uri localUri, long length, String previewPath, String fetchUrl,
            boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.preview.uri", localUri.toString());
        i.putExtra("org.kontalk.message.length", length);
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.fetch.url", fetchUrl);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static String messageId() {
        return StringUtils.randomString(30);
    }

    /** Replies to a presence subscription request. */
    public static void replySubscription(final Context context, String to, int action) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_SUBSCRIBED);
        i.putExtra(EXTRA_TO, to);
        i.putExtra(EXTRA_PRIVACY, action);
        context.startService(i);
    }

    public static void regenerateKeyPair(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_REGENERATE_KEYPAIR);
        context.startService(i);
    }

    public static void importKeyPair(final Context context, Uri keypack, String passphrase) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_IMPORT_KEYPAIR);
        i.putExtra(EXTRA_KEYPACK, keypack);
        i.putExtra(EXTRA_PASSPHRASE, passphrase);
        context.startService(i);
    }

    public static void requestConnectionStatus(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_CONNECTED);
        context.startService(i);
    }

    public static void requestRosterStatus(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_ROSTER_LOADED);
        context.startService(i);
    }

    public static void requestLastActivity(final Context context, String to, String id) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_LAST_ACTIVITY);
        i.putExtra(EXTRA_TO, to);
        i.putExtra(EXTRA_PACKET_ID, id);
        context.startService(i);
    }

    public static void requestVersionInfo(final Context context, String to, String id) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_VERSION);
        i.putExtra(EXTRA_TO, to);
        i.putExtra(EXTRA_PACKET_ID, id);
        context.startService(i);
    }

    public static void requestVCard(final Context context, String to) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_VCARD);
        i.putExtra(EXTRA_TO, to);
        context.startService(i);
    }

    public static void requestPublicKey(final Context context, String to) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_PUBLICKEY);
        i.putExtra(EXTRA_TO, to);
        context.startService(i);
    }

    public static void requestServerList(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_SERVERLIST);
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
        i.putExtra(PUSH_REGISTRATION_ID, registrationId);
        context.startService(i);
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
            if (mPushService.isServiceAvailable()) {
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
        if (mPushService.isRegistered())
            // start unregistration
            mPushService.unregister(sPushListener);
        else
            // force unregistration
            setPushRegistrationId(null);
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
        iq.setTo("push@" + mServer.getNetwork());
        try {
            mConnection.sendIqWithResponseCallback(iq, new StanzaListener() {
                @Override
                public void processPacket(Stanza packet) throws NotConnectedException {
                    if (mPushService != null)
                        mPushService.setRegisteredOnServer(regId != null);
                }
            });
        }
        catch (NotConnectedException e) {
            // ignored
        }
    }

    private void sendPushUnregistration() {
        IQ iq = PushRegistration.unregister(DEFAULT_PUSH_PROVIDER);
        iq.setTo("push@" + mServer.getNetwork());
        try {
            mConnection.sendIqWithResponseCallback(iq, new StanzaListener() {
                @Override
                public void processPacket(Stanza packet) throws NotConnectedException {
                    if (mPushService != null)
                        mPushService.setRegisteredOnServer(false);
                }
            });
        }
        catch (NotConnectedException e) {
            // ignored
        }
    }

    public static String getPushSenderId() {
        return sPushSenderId;
    }

    private void setWakeupAlarm() {
        long delay = Preferences.getWakeupTimeMillis(this,
            MIN_WAKEUP_TIME, DEFAULT_WAKEUP_TIME);

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

    private void cancelIdleAlarm() {
        ensureIdleAlarm();
        mAlarmManager.cancel(mIdleIntent);
    }

    private void setIdleAlarm() {
        long delay = Preferences.getIdleTimeMillis(this, 0, DEFAULT_IDLE_TIME);
        if (delay > 0) {
            ensureIdleAlarm();
            mAlarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, delay, mIdleIntent);
        }
    }

}

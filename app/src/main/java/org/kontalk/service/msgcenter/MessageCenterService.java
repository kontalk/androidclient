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

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipInputStream;

import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.debugger.AbstractDebugger;
import org.jivesoftware.smack.debugger.SmackDebugger;
import org.jivesoftware.smack.debugger.SmackDebuggerFactory;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.roster.packet.RosterPacket;
import org.jivesoftware.smack.util.Async;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.caps.packet.CapsExtension;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jivesoftware.smackx.chatstates.packet.ChatStateExtension;
import org.jivesoftware.smackx.csi.ClientStateIndicationManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
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
import org.jxmpp.stringprep.XmppStringprepException;
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
import android.support.v4.app.ServiceCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.authenticator.LegacyAuthentication;
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
import org.kontalk.client.VCard4;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.message.LocationComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MessagesProviderUtils;
import org.kontalk.provider.MyMessages.CommonColumns;
import org.kontalk.provider.MyMessages.Groups;
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
import org.kontalk.service.msgcenter.group.AddRemoveMembersCommand;
import org.kontalk.service.msgcenter.group.CreateGroupCommand;
import org.kontalk.service.msgcenter.group.GroupCommand;
import org.kontalk.service.msgcenter.group.GroupController;
import org.kontalk.service.msgcenter.group.GroupControllerFactory;
import org.kontalk.service.msgcenter.group.KontalkGroupController;
import org.kontalk.service.msgcenter.group.PartCommand;
import org.kontalk.service.msgcenter.group.SetSubjectCommand;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;
import org.spongycastle.openpgp.PGPException;

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

    static {
        SmackConfiguration.DEBUG = Log.isDebug();
        // we need our own debugger factory because of our internal logging system
        SmackConfiguration.setDebuggerFactory(new SmackDebuggerFactory() {
            @Override
            public SmackDebugger create(XMPPConnection connection, Writer writer, Reader reader) throws IllegalArgumentException {
                return new AbstractDebugger(connection, writer, reader) {
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
    public static final String ACTION_MESSAGE = "org.kontalk.action.MESSAGE";
    public static final String ACTION_PUSH_START = "org.kontalk.push.START";
    public static final String ACTION_PUSH_STOP = "org.kontalk.push.STOP";
    public static final String ACTION_PUSH_REGISTERED = "org.kontalk.push.REGISTERED";
    public static final String ACTION_IDLE = "org.kontalk.action.IDLE";
    public static final String ACTION_PING = "org.kontalk.action.PING";
    public static final String ACTION_MEDIA_READY = "org.kontalk.action.MEDIA_READY";

    /**
     * Request the roster.
     */
    public static final String ACTION_ROSTER = "org.kontalk.action.ROSTER";

    /**
     * Request roster match.
     */
    public static final String ACTION_ROSTER_MATCH = "org.kontalk.action.ROSTER_MATCH";

    /**
     * Broadcasted when we are connected and authenticated to the server.
     * Send this intent to receive the same as a broadcast if connected.
     */
    public static final String ACTION_CONNECTED = "org.kontalk.action.CONNECTED";

    /**
     * Broadcasted when the roster has been loaded.
     * Send this intent to receive the same as a broadcast if the roster has
     * already been loaded.
     */
    public static final String ACTION_ROSTER_LOADED = "org.kontalk.action.ROSTER_LOADED";

    /**
     * Send this intent to request roster status of any user.
     * Broadcasted back in reply to requests.
     */
    public static final String ACTION_ROSTER_STATUS = "org.kontalk.action.ROSTER_STATUS";

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

    /**
     * Commence key pair import.
     */
    public static final String ACTION_IMPORT_KEYPAIR = "org.kontalk.action.IMPORT_KEYPAIR";

    /**
     * Broadcasted when private key was uploaded to server.
     * Send this intent to upload your private key.
     */
    public static final String ACTION_UPLOAD_PRIVATEKEY = "org.kontalk.action.UPLOAD_PRIVATEKEY";

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

    /**
     * Broadcasted when a block request has ben accepted by the server.
     */
    public static final String ACTION_BLOCKED = "org.kontalk.action.BLOCKED";

    /**
     * Broadcasted when an unblock request has ben accepted by the server.
     */
    public static final String ACTION_UNBLOCKED = "org.kontalk.action.UNBLOCKED";

    /**
     * Broadcasted when receiving version information.
     * Send this intent to request version information to an entity.
     */
    public static final String ACTION_VERSION = "org.kontalk.action.VERSION";

    /**
     * Send this intent to update the foreground service status of the message center.
     */
    public static final String ACTION_FOREGROUND = "org.kontalk.action.FOREGROUND";

    // common parameters
    public static final String EXTRA_PACKET_ID = "org.kontalk.packet.id";
    public static final String EXTRA_TYPE = "org.kontalk.packet.type";
    public static final String EXTRA_ERROR_CONDITION = "org.kontalk.packet.error.condition";

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
    public static final String EXTRA_STAMP = "org.kontalk.packet.delay";

    // use with org.kontalk.action.ROSTER(_MATCH)
    public static final String EXTRA_JIDLIST = "org.kontalk.roster.JIDList";
    public static final String EXTRA_ROSTER_NAME = "org.kontalk.roster.name";

    // use with org.kontalk.action.LAST_ACTIVITY
    public static final String EXTRA_SECONDS = "org.kontalk.last.seconds";

    // use with org.kontalk.action.VCARD
    public static final String EXTRA_PUBLIC_KEY = "org.kontalk.vcard.publicKey";

    // used with org.kontalk.action.BLOCKLIST
    public static final String EXTRA_BLOCKLIST = "org.kontalk.blocklist";

    // used with org.kontalk.action.IMPORT_KEYPAIR
    public static final String EXTRA_KEYPACK = "org.kontalk.keypack";
    public static final String EXTRA_PASSPHRASE = "org.kontalk.passphrase";

    // use with org.kontalk.action.UPLOAD_PRIVATEKEY
    public static final String EXTRA_EXPORT_PASSPHRASE = "org.kontalk.export_passphrase";
    public static final String EXTRA_TOKEN = "org.kontalk.token";

    // used with org.kontalk.action.VERSION
    public static final String EXTRA_VERSION_NAME = "org.kontalk.version.name";
    public static final String EXTRA_VERSION_NUMBER = "org.kontalk.version.number";

    // used for org.kontalk.presence.privacy.action extra
    /**
     * Accept subscription.
     */
    public static final int PRIVACY_ACCEPT = 0;
    /**
     * Block user.
     */
    public static final int PRIVACY_BLOCK = 1;
    /**
     * Unblock user.
     */
    public static final int PRIVACY_UNBLOCK = 2;
    /**
     * Reject subscription and block.
     */
    public static final int PRIVACY_REJECT = 3;

    /**
     * Message URI.
     */
    public static final String EXTRA_MESSAGE = "org.kontalk.message";

    // other
    public static final String PUSH_REGISTRATION_ID = "org.kontalk.PUSH_REGISTRATION_ID";
    private static final String DEFAULT_PUSH_PROVIDER = "gcm";

    private static final int GROUP_COMMAND_CREATE = 1;
    private static final int GROUP_COMMAND_SUBJECT = 2;
    private static final int GROUP_COMMAND_PART = 3;
    private static final int GROUP_COMMAND_MEMBERS = 4;

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
    private static final int FAST_PING_TIMEOUT = 3000;
    /**
     * Minimal interval between connection tests (5 mins).
     */
    private static final int MIN_TEST_INTERVAL = 5 * 60 * 1000;

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
     * Messages waiting for server receipt (packetId: internalStorageId).
     */
    Map<String, Long> mWaitingReceipt = new HashMap<>();

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
                        if (service != null && !service.isInactive() && service.isConnected()) {
                            queueInactive();
                        }
                    }
                });
            }
        }

        public void quit() {
            abortIdle();
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
        if (!isOfflineMode(this)) {
            // immediately setup the foreground notification if requested
            setForeground();
        }

        // configure XMPP client
        configure();

        // create the roster store
        mRosterStore = new SQLiteRosterStore(this);
        // this will trigger create/upgrade
        mRosterStore.getWritableDatabase();

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

    void queueTask(Runnable task) {
        if (mThreadPool != null) {
            mThreadPool.execute(task);
        }
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
     *
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
            catch (InterruptedException e) {
                // ignored
                Log.v(TAG, "interrupted. Dropping packet " + packet);
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
            int refCount = ((Kontalk) getApplicationContext()).getReferenceCounter();
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
            PingManager.getInstanceFor(mConnection)
                .unregisterPingFailedListener(mPingFailedListener);
            mPingFailedListener = null;
            // this is because of NetworkOnMainThreadException
            DisconnectThread disconnectThread = new DisconnectThread(mConnection);
            disconnectThread.start();
            disconnectThread.joinTimeout(500);

            // clear the connection only if we are quitting
            if (!restarting)
                mConnection = null;
        }

        if (mUploadServices != null) {
            mUploadServices.clear();
            mUploadServices = null;
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

                case ACTION_UPLOAD_PRIVATEKEY:
                    doConnect = handleUploadPrivateKey(intent);
                    break;

                case ACTION_CONNECTED:
                    doConnect = handleConnected();
                    break;

                case ACTION_RESTART:
                    doConnect = handleRestart();
                    break;

                case ACTION_TEST:
                    doConnect = handleTest(canConnect);
                    break;

                case ACTION_PING:
                    doConnect = handlePing(canConnect);
                    break;

                case ACTION_MESSAGE:
                    doConnect = handleMessage(intent, canConnect);
                    break;

                case ACTION_ROSTER:
                case ACTION_ROSTER_MATCH:
                    doConnect = handleRoster(intent, canConnect);
                    break;

                case ACTION_ROSTER_LOADED:
                    doConnect = handleRosterLoaded();
                    break;

                case ACTION_ROSTER_STATUS:
                    doConnect = handleRosterStatus(intent);
                    break;

                case ACTION_PRESENCE:
                    doConnect = handlePresence(intent, canConnect);
                    break;

                case ACTION_LAST_ACTIVITY:
                    doConnect = handleLastActivity(intent, canConnect);
                    break;

                case ACTION_VCARD:
                    doConnect = handleVCard(intent, canConnect);
                    break;

                case ACTION_PUBLICKEY:
                    doConnect = handlePublicKey(intent, canConnect);
                    break;

                case ACTION_SERVERLIST:
                    doConnect = handleServerList(canConnect);
                    break;

                case ACTION_SUBSCRIBED:
                    doConnect = handleSubscribed(intent, canConnect);
                    break;

                case ACTION_RETRY:
                    doConnect = handleRetry();
                    break;

                case ACTION_BLOCKLIST:
                    doConnect = handleBlocklist();
                    break;

                case ACTION_VERSION:
                    doConnect = handleVersion(intent);
                    break;

                case ACTION_MEDIA_READY:
                    doConnect = handleMediaReady(intent);
                    break;

                case ACTION_FOREGROUND:
                    doConnect = handleForeground();
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

            // no reason to exist
            if (!canConnect && !doConnect && !isConnected() && !isConnecting())
                stopSelf();

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
    private boolean handleRegenerateKeyPair(Intent intent) {
        beginKeyPairRegeneration(intent.getStringExtra(EXTRA_PASSPHRASE));
        return true;
    }

    @CommandHandler(name = ACTION_IMPORT_KEYPAIR)
    private boolean handleImportKeyPair(Intent intent) {
        // zip file with keys
        Uri file = intent.getParcelableExtra(EXTRA_KEYPACK);
        // passphrase to decrypt files
        String passphrase = intent.getStringExtra(EXTRA_PASSPHRASE);
        beginKeyPairImport(file, passphrase);
        return false;
    }

    @CommandHandler(name = ACTION_UPLOAD_PRIVATEKEY)
    private boolean handleUploadPrivateKey(Intent intent) {
        String exportPassprase = intent.getStringExtra(EXTRA_EXPORT_PASSPHRASE);
        beginUploadPrivateKey(exportPassprase);
        return true;
    }

    @CommandHandler(name = ACTION_CONNECTED)
    private boolean handleConnected() {
        if (isConnected())
            broadcast(ACTION_CONNECTED);
        return false;
    }

    @CommandHandler(name = ACTION_RESTART)
    private boolean handleRestart() {
        quit(true);
        return true;
    }

    @CommandHandler(name = ACTION_TEST)
    private boolean handleTest(boolean canConnect) {
        if (isConnected()) {
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

    @CommandHandler(name = ACTION_MESSAGE)
    private boolean handleMessage(Intent intent, boolean canConnect) {
        if (canConnect && isConnected())
            sendMessage(intent.getExtras());
        return false;
    }

    @CommandHandler(name = {ACTION_ROSTER, ACTION_ROSTER_MATCH})
    private boolean handleRoster(Intent intent, boolean canConnect) {
        if (canConnect && isConnected()) {
            Stanza iq;

            if (ACTION_ROSTER_MATCH.equals(intent.getAction())) {
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
        return false;
    }

    @CommandHandler(name = ACTION_ROSTER_LOADED)
    private boolean handleRosterLoaded() {
        if (isConnected() && isRosterLoaded())
            broadcast(ACTION_ROSTER_LOADED);
        return false;
    }

    @CommandHandler(name = ACTION_ROSTER_STATUS)
    private boolean handleRosterStatus(Intent intent) {
        if (mRosterStore != null) {
            final String to = intent.getStringExtra(EXTRA_TO);

            RosterPacket.Item entry;
            try {
                entry = mRosterStore.getEntry(JidCreate.from(to));
            }
            catch (XmppStringprepException e) {
                Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
                // report it because it's a big deal
                ReportingManager.logException(e);
                return false;
            }
            if (entry != null) {
                final String id = intent.getStringExtra(EXTRA_PACKET_ID);

                Intent i = new Intent(ACTION_ROSTER_STATUS);
                i.putExtra(EXTRA_FROM, to);
                i.putExtra(EXTRA_ROSTER_NAME, entry.getName());

                RosterPacket.ItemType subscriptionType = entry.getItemType();
                i.putExtra(EXTRA_SUBSCRIBED_FROM, subscriptionType == RosterPacket.ItemType.both ||
                    subscriptionType == RosterPacket.ItemType.from);
                i.putExtra(EXTRA_SUBSCRIBED_TO, subscriptionType == RosterPacket.ItemType.both ||
                    subscriptionType == RosterPacket.ItemType.to);

                // to keep track of request-reply
                i.putExtra(EXTRA_PACKET_ID, id);

                mLocalBroadcastManager.sendBroadcast(i);
            }
        }
        return false;
    }

    @CommandHandler(name = ACTION_PRESENCE)
    private boolean handlePresence(Intent intent, boolean canConnect) {
        if (canConnect && isConnected()) {
            final String id = intent.getStringExtra(EXTRA_PACKET_ID);
            String type = intent.getStringExtra(EXTRA_TYPE);
            final String to = intent.getStringExtra(EXTRA_TO);

            if ("probe".equals(type)) {
                // probing is actually looking into the roster
                final Roster roster = getRoster();

                if (to == null) {
                    for (RosterEntry entry : roster.getEntries()) {
                        broadcastPresence(roster, entry, id);
                    }

                    // broadcast our own presence
                    broadcastMyPresence(id);
                }
                else {
                    queueTask(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                broadcastPresence(roster, JidCreate.bareFrom(to), id);
                            }
                            catch (XmppStringprepException e) {
                                Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
                                // report it because it's a big deal
                                ReportingManager.logException(e);
                                throw new IllegalArgumentException(e);
                            }
                        }
                    });
                }
            }
            else {
                // FIXME isn't this somewhat the same as createPresence?
                String show = intent.getStringExtra(EXTRA_SHOW);
                Presence p = new Presence(type != null ? Presence.Type.valueOf(type) : Presence.Type.available);
                p.setStanzaId(id);
                if (to != null)
                    p.setTo(to);
                if (intent.hasExtra(EXTRA_PRIORITY))
                    p.setPriority(intent.getIntExtra(EXTRA_PRIORITY, 0));
                String status = intent.getStringExtra(EXTRA_STATUS);
                if (!TextUtils.isEmpty(status))
                    p.setStatus(status);
                if (show != null)
                    p.setMode(Presence.Mode.valueOf(show));

                sendPacket(p);
            }
        }

        return false;
    }

    @CommandHandler(name = ACTION_LAST_ACTIVITY)
    private boolean handleLastActivity(Intent intent, boolean canConnect) {
        if (canConnect && isConnected()) {
            LastActivity p = new LastActivity();

            p.setStanzaId(intent.getStringExtra(EXTRA_PACKET_ID));
            p.setTo(intent.getStringExtra(EXTRA_TO));

            sendPacket(p);
        }
        return false;
    }

    @CommandHandler(name = ACTION_VCARD)
    private boolean handleVCard(Intent intent, boolean canConnect) {
        if (canConnect && isConnected()) {
            VCard4 p = new VCard4();
            p.setTo(intent.getStringExtra(EXTRA_TO));

            sendPacket(p);
        }
        return false;
    }

    @CommandHandler(name = ACTION_PUBLICKEY)
    private boolean handlePublicKey(Intent intent, boolean canConnect) {
        if (canConnect && isConnected()) {
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
                p.setTo(mConnection.getUser().asBareJid());
                sendPacket(p);
            }
        }
        return false;
    }

    @CommandHandler(name = ACTION_SERVERLIST)
    private boolean handleServerList(boolean canConnect) {
        if (canConnect && isConnected()) {
            ServerlistCommand p = new ServerlistCommand();
            p.setTo(XmppStringUtils.completeJidFrom("network", mServer.getNetwork()));

            StanzaFilter filter = new StanzaIdFilter(p.getStanzaId());
            // TODO cache the listener (it shouldn't change)
            mConnection.addAsyncStanzaListener(new StanzaListener() {
                public void processStanza(Stanza packet) throws NotConnectedException {
                    Intent i = new Intent(ACTION_SERVERLIST);
                    List<String> _items = ((ServerlistCommand.ServerlistCommandData) packet)
                        .getItems();
                    if (_items != null && _items.size() != 0 && packet.getError() == null) {
                        String[] items = new String[_items.size()];
                        _items.toArray(items);

                        i.putExtra(EXTRA_FROM, packet.getFrom().toString());
                        i.putExtra(EXTRA_JIDLIST, items);
                    }
                    mLocalBroadcastManager.sendBroadcast(i);
                }
            }, filter);

            sendPacket(p);
        }
        return false;
    }

    @CommandHandler(name = ACTION_SUBSCRIBED)
    private boolean handleSubscribed(Intent intent, boolean canConnect) {
        if (canConnect && isConnected()) {
            sendSubscriptionReply(intent.getStringExtra(EXTRA_TO),
                intent.getStringExtra(EXTRA_PACKET_ID),
                intent.getIntExtra(EXTRA_PRIVACY, PRIVACY_ACCEPT));
        }
        return false;
    }

    @CommandHandler(name = ACTION_RETRY)
    private boolean handleRetry() {
        // TODO we should retry only the requested message(s)
        // already connected: resend pending messages
        if (isConnected())
            resendPendingMessages(false, false);
        return false;
    }

    @CommandHandler(name = ACTION_BLOCKLIST)
    private boolean handleBlocklist() {
        if (isConnected())
            requestBlocklist();
        return false;
    }

    @CommandHandler(name = ACTION_VERSION)
    private boolean handleVersion(Intent intent) {
        if (isConnected()) {
            try {
                Version version = new Version(JidCreate.from(intent.getStringExtra(EXTRA_TO)));
                version.setStanzaId(intent.getStringExtra(EXTRA_PACKET_ID));
                sendPacket(version);
            }
            catch (XmppStringprepException e) {
                Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
                // report it because it's a big deal
                ReportingManager.logException(e);
            }
        }
        return false;
    }

    @CommandHandler(name = ACTION_MEDIA_READY)
    private boolean handleMediaReady(Intent intent) {
        long msgId = intent.getLongExtra("org.kontalk.message.msgId", 0);
        if (msgId > 0)
            sendReadyMedia(msgId);
        return true;
    }

    @CommandHandler(name = ACTION_FOREGROUND)
    private boolean handleForeground() {
        setForeground();
        return false;
    }

    /**
     * Creates a connection to server if needed.
     */
    private synchronized void createConnection() {
        // connection is null or disconnected and no helper is currently running
        if ((mConnection == null || !mConnection.isConnected()) && mHelper == null) {
            // acquire the wakelock
            mWakeLock.acquire();

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
    public void reconnectionFailed(Exception error) {
        // not used
    }

    @Override
    public void reconnectionSuccessful() {
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
        RosterListener rosterListener = new RosterListener(this, presenceListener);
        Roster roster = getRoster();
        roster.addRosterLoadedListener(rosterListener);
        roster.addRosterListener(rosterListener);
        roster.setRosterStore(mRosterStore);

        // enable ping manager
        AndroidAdaptiveServerPingManager
            .getInstanceFor(connection, this)
            .setEnabled(true);
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
        connection.addAsyncStanzaListener(presenceListener, filter);

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
            // FIXME ack listener recreated everytime
            mConnection.removeAllStanzaAcknowledgedListeners();
            mConnection.addStanzaAcknowledgedListener(new MessageAckListener(this));
        }
        else {
            Log.w(TAG, "stream management not available - disabling delivery receipts");
        }

        // send presence
        sendPresence(mIdleHandler.isHeld() ? Presence.Mode.available : Presence.Mode.away);
        // clear upload service
        if (mUploadServices != null)
            mUploadServices.clear();
        // discovery
        discovery();

        // helper is not needed any more
        mHelper = null;

        broadcast(ACTION_CONNECTED);

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
                    Jid jid = conn.getServiceName();
                    if (Keyring.getPublicKey(MessageCenterService.this, jid.toString(), MyUsers.Keys.TRUST_UNKNOWN) == null) {
                        PublicKeyPublish pub = new PublicKeyPublish();
                        pub.setTo(jid);
                        sendPacket(pub, false);
                    }
                }
            }
        });

        // release the wakelock
        mWakeLock.release();
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

        DiscoverInfo info = new DiscoverInfo();
        info.setTo(mServer.getNetwork());
        filter = new StanzaIdFilter(info.getStanzaId());
        mConnection.addAsyncStanzaListener(new DiscoverInfoListener(this), filter);
        sendPacket(info);

        DiscoverItems items = new DiscoverItems();
        items.setTo(mServer.getNetwork());
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

    private void sendReadyMedia(long databaseId) {
        Cursor c = getContentResolver().query(ContentUris
                .withAppendedId(Messages.CONTENT_URI, databaseId),
            new String[]{
                Messages._ID,
                Messages.THREAD_ID,
                Messages.MESSAGE_ID,
                Messages.PEER,
                Messages.BODY_CONTENT,
                Messages.BODY_MIME,
                Messages.SECURITY_FLAGS,
                Messages.ATTACHMENT_MIME,
                Messages.ATTACHMENT_LOCAL_URI,
                Messages.ATTACHMENT_FETCH_URL,
                Messages.ATTACHMENT_PREVIEW_PATH,
                Messages.ATTACHMENT_LENGTH,
                Messages.ATTACHMENT_COMPRESS,
                // TODO Messages.ATTACHMENT_SECURITY_FLAGS,
                Groups.GROUP_JID,
                Groups.SUBJECT,
            },
            null, null, null);

        sendMessages(c, false);

        c.close();
    }

    void resendPendingMessages(boolean retrying, boolean forcePending) {
        resendPendingMessages(retrying, forcePending, null);
    }

    /**
     * Queries for pending messages and send them through.
     *
     * @param retrying     if true, we are retrying to send media messages after
     *                     receiving upload info (non-media messages will be filtered out)
     * @param forcePending true to include pending user review messages
     * @param to           filter by recipient (optional)
     */
    void resendPendingMessages(boolean retrying, boolean forcePending, String to) {
        String[] filterArgs = null;

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
            .append(Messages.STATUS_QUEUED);


        // filter out pending messages
        if (!forcePending) filter
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

        if (to != null) {
            filter
                .append(" AND (")
                .append(Messages.PEER)
                .append("=? OR EXISTS (SELECT 1 FROM group_members WHERE ")
                .append(Groups.GROUP_JID)
                .append("=")
                .append(Messages.PEER)
                .append(" AND ")
                .append(Groups.PEER)
                .append("=?))");
            filterArgs = new String[]{to, to};
        }

        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
            new String[]{
                Messages._ID,
                Messages.THREAD_ID,
                Messages.MESSAGE_ID,
                Messages.PEER,
                Messages.BODY_CONTENT,
                Messages.BODY_MIME,
                Messages.SECURITY_FLAGS,
                Messages.ATTACHMENT_MIME,
                Messages.ATTACHMENT_LOCAL_URI,
                Messages.ATTACHMENT_FETCH_URL,
                Messages.ATTACHMENT_PREVIEW_PATH,
                Messages.ATTACHMENT_LENGTH,
                Messages.ATTACHMENT_COMPRESS,
                // TODO Messages.ATTACHMENT_SECURITY_FLAGS,
                Groups.GROUP_JID,
                Groups.SUBJECT,
                Messages.GEO_LATITUDE,
                Messages.GEO_LONGITUDE,
                Messages.GEO_TEXT,
                Messages.GEO_STREET,
            },
            filter.toString(), filterArgs,
            Messages._ID);

        sendMessages(c, retrying);

        c.close();
    }

    private void sendMessages(Cursor c, boolean retrying) {
        // this set will cache thread IDs within this cursor with
        // pending group commands (i.e. just processed group commands)
        // This will be looked up when sending consecutive message in the group
        // and stop them
        Set<Long> pendingGroupCommandThreads = new HashSet<>();

        while (c.moveToNext()) {
            long id = c.getLong(0);
            long threadId = c.getLong(1);
            String msgId = c.getString(2);
            String peer = c.getString(3);
            byte[] textContent = c.getBlob(4);
            String bodyMime = c.getString(5);
            int securityFlags = c.getInt(6);
            String attMime = c.getString(7);
            String attFileUri = c.getString(8);
            String attFetchUrl = c.getString(9);
            String attPreviewPath = c.getString(10);
            long attLength = c.getLong(11);
            int compress = c.getInt(12);
            // TODO int attSecurityFlags = c.getInt(13);

            String groupJid = c.getString(13); // 14
            String groupSubject = c.getString(14); // 15

            if (pendingGroupCommandThreads.contains(threadId)) {
                Log.v(TAG, "group message for pending group command - delaying");
                continue;
            }

            final boolean isGroupCommand = GroupCommandComponent.supportsMimeType(bodyMime);
            if (isGroupCommand) {
                if (groupJid == null) {
                    // orphan group command waiting to be sent
                    groupJid = peer;
                }
                else {
                    // cache the thread -- it will block future messages until
                    // this command is received by the server
                    pendingGroupCommandThreads.add(threadId);
                }
            }

            String[] groupMembers = null;
            if (groupJid != null) {
                /*
                 * Huge potential issue here. Selecting all members, regardless of pending flags,
                 * might e.g. deliver messages to removed users if there is a content message right
                 * after a remove command.
                 * However, selecting members with zero flags will make a remove command to be sent
                 * only to existing members and not to the ones being removed.
                 */
                groupMembers = MessagesProviderUtils.getGroupMembers(this, groupJid, -1);
                if (groupMembers.length == 0) {
                    // no group member left - skip message
                    // this might be a pending message that was queued before we realized there were no members left
                    // since the group might get populated again, we just skip the message but keep it
                    Log.d(TAG, "no members in group - skipping message");
                    continue;
                }
            }

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

            if (groupJid != null) {
                b.putString("org.kontalk.message.group.jid", groupJid);
                b.putString("org.kontalk.message.group.subject", groupSubject);
                // will be replaced by the group command (if any)
                b.putStringArray("org.kontalk.message.to", groupMembers);
            }
            else {
                b.putString("org.kontalk.message.to", peer);
            }

            // TODO shouldn't we pass security flags directly here??
            b.putBoolean("org.kontalk.message.encrypt", securityFlags != Coder.SECURITY_CLEARTEXT);

            if (isGroupCommand) {
                int cmd = 0;
                byte[] _command = c.getBlob(4);
                String command = new String(_command);

                String[] createMembers;
                String[] addMembers;
                String[] removeMembers = null;
                String subject;
                if ((createMembers = GroupCommandComponent.getCreateCommandMembers(command)) != null) {
                    cmd = GROUP_COMMAND_CREATE;
                    b.putStringArray("org.kontalk.message.to", createMembers);
                }
                else if (command.equals(GroupCommandComponent.COMMAND_PART)) {
                    cmd = GROUP_COMMAND_PART;
                }
                else if ((addMembers = GroupCommandComponent.getAddCommandMembers(command)) != null ||
                    (removeMembers = GroupCommandComponent.getRemoveCommandMembers(command)) != null) {
                    cmd = GROUP_COMMAND_MEMBERS;
                    b.putStringArray("org.kontalk.message.group.add", addMembers);
                    b.putStringArray("org.kontalk.message.group.remove", removeMembers);
                }
                else if ((subject = GroupCommandComponent.getSubjectCommand(command)) != null) {
                    cmd = GROUP_COMMAND_SUBJECT;
                    b.putString("org.kontalk.message.group.subject", subject);
                }

                b.putInt("org.kontalk.message.group.command", cmd);
            }
            else if (textContent != null) {
                b.putString("org.kontalk.message.body", MessageUtils.toString(textContent));
            }

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

            if (!c.isNull(15)) {
                double lat = c.getDouble(15);
                double lon = c.getDouble(16);
                b.putDouble("org.kontalk.message.geo_lat", lat);
                b.putDouble("org.kontalk.message.geo_lon", lon);

                if (!c.isNull(17)) {
                    String geoText = c.getString(17);
                    b.putString("org.kontalk.message.geo_text", geoText);
                }

                if (!c.isNull(18)) {
                    String geoStreet = c.getString(18);
                    b.putString("org.kontalk.message.geo_street", geoStreet);
                }
            }


            Log.v(TAG, "resending pending message " + id);
            sendMessage(b);
        }
    }

    void resendPendingReceipts() {
        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
            new String[]{
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

    void sendPendingSubscriptionReplies() {
        Cursor c = getContentResolver().query(Threads.CONTENT_URI,
            new String[]{
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

    RosterEntry getRosterEntry(BareJid jid) {
        Roster roster = getRoster();
        return (roster != null) ? roster.getEntry(jid) : null;
    }

    @Deprecated
    RosterEntry getRosterEntry(String jid) throws XmppStringprepException {
        return getRosterEntry(JidCreate.bareFrom(jid));
    }

    private boolean isAuthorized(BareJid jid) {
        if (Authenticator.isSelfJID(this, jid))
            return true;
        RosterEntry entry = getRosterEntry(jid);
        return entry != null && isAuthorized(entry);
    }

    @Deprecated
    private boolean isAuthorized(String jid) throws XmppStringprepException {
        BareJid bareJid = JidCreate.bareFrom(jid);
        if (Authenticator.isSelfJID(this, bareJid))
            return true;
        RosterEntry entry = getRosterEntry(bareJid);
        return entry != null && isAuthorized(entry);
    }

    private boolean isAuthorized(RosterEntry entry) {
        return (isRosterEntrySubscribed(entry) || Authenticator.isSelfJID(this, entry.getJid()));
    }

    private boolean isRosterEntrySubscribed(RosterEntry entry) {
        return (entry != null && (entry.getType() == RosterPacket.ItemType.to || entry.getType() == RosterPacket.ItemType.both) &&
            !entry.isSubscriptionPending());
    }

    private void broadcastPresence(Roster roster, RosterEntry entry, String id) {
        broadcastPresence(roster, entry, entry.getJid(), id);
    }

    void broadcastPresence(Roster roster, BareJid jid, String id) {
        broadcastPresence(roster, roster.getEntry(jid), jid, id);
    }

    private void broadcastPresence(Roster roster, RosterEntry entry, BareJid jid, String id) {
        // this method might be called async
        final LocalBroadcastManager lbm = mLocalBroadcastManager;
        if (lbm == null)
            return;

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
            i.putExtra(EXTRA_FROM, jid.toString());
        }

        // to keep track of request-reply
        i.putExtra(EXTRA_PACKET_ID, id);
        lbm.sendBroadcast(i);
    }

    /**
     * A special method to broadcast our own presence.
     */
    private void broadcastMyPresence(String id) {
        Presence presence = createPresence(null);
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
            public void processStanza(Stanza packet) {

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
            public void processStanza(Stanza packet) {
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

        final String groupJid = data.getString("org.kontalk.message.group.jid");
        String to;
        // used for verifying isPaused()
        String convJid;
        String[] toGroup;
        GroupController group = null;

        if (groupJid != null) {
            toGroup = data.getStringArray("org.kontalk.message.to");
            // TODO this should be discovered first
            to = XmppStringUtils.completeJidFrom("multicast", mConnection.getServiceName());
            convJid = groupJid;

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
            to = data.getString("org.kontalk.message.to");
            toGroup = new String[]{to};
            convJid = to;
        }

        Jid toJid = null;
        try {
            toJid = JidCreate.from(to);
        }
        catch (XmppStringprepException e) {
            Log.w(TAG, "error parsing JID: " + e.getCausingString(), e);
            // report it because it's a big deal
            ReportingManager.logException(e);
            return;
        }

        if (group == null && !isAuthorized(toJid.asBareJid())) {
            Log.i(TAG, "not subscribed to " + to + ", not sending message");
            // warn user: message will not be sent
            if (!retrying && MessagingNotification.isPaused(to)) {
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
            if (MessagingNotification.isPaused(convJid)) {
                Toast.makeText(this, R.string.warn_no_personal_key,
                    Toast.LENGTH_LONG).show();
            }
            return;
        }

        // check if message is already pending3
        final long msgId = data.getLong("org.kontalk.message.msgId");
        if (mWaitingReceipt.containsValue(msgId)) {
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
                        InputStream in = getContentResolver().openInputStream(preMediaUri);
                        File encrypted = MessageUtils.encryptFile(this, in, toGroup);
                        fileLength = encrypted.length();
                        preMediaUri = Uri.fromFile(encrypted);
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
                final String uploadTo = to;
                final String[] uploadGroupTo = toGroup;
                uploadService.getPostUrl(filename, fileLength, mime, new IUploadService.UrlCallback() {
                    @Override
                    public void callback(String putUrl, String getUrl) {
                        // start upload intent service
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
                        startService(i);
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

            if (to != null) m.setTo(to);

            // set message id
            m.setStanzaId(id);
            if (msgId > 0)
                mWaitingReceipt.put(id, msgId);

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
                                toMessage = coder.encryptStanza(m.toXML());
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
                        chatState = ChatState.valueOf(data.getString("org.kontalk.message.chatState"));
                        // add chat state if message is not a received receipt
                        m.addExtension(new ChatStateExtension(chatState));
                    }
                    catch (Exception ignored) {
                    }

                    // standalone: no receipt
                    if (ackRequest)
                        DeliveryReceiptRequest.addTo((org.jivesoftware.smack.packet.Message) m);
                }
            }

            sendPacket(m);

            // no ack request, release message center immediately
            if (!ackRequest)
                mIdleHandler.release();
        }
    }

    private void ensureUploadServices() {
        if (mUploadServices == null)
            mUploadServices = new ArrayList<>(2);
    }

    void addUploadService(IUploadService service) {
        ensureUploadServices();
        mUploadServices.add(service);
    }

    void addUploadService(IUploadService service, int priority) {
        ensureUploadServices();
        mUploadServices.add(priority, service);
    }

    /**
     * Returns the first available upload service post URL.
     */
    private IUploadService getUploadService() {
        return (mUploadServices != null && mUploadServices.size() > 0) ?
            mUploadServices.get(0) : null;
    }

    private void setForeground() {
        boolean enable = Preferences.getForegroundServiceEnabled(this);
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
            String passphrase = Kontalk.get(this).getCachedPassphrase();
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
        if (SystemUtils.isNetworkConnectionAvailable(context)) {
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
     *
     * @param activate true to wake up from CSI and send become available.
     */
    public static void hold(final Context context, boolean activate) {
        // increment the application counter
        ((Kontalk) context.getApplicationContext()).hold();

        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_HOLD);
        i.putExtra("org.kontalk.activate", activate);
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

    /**
     * Broadcasts our presence to the server.
     */
    public static void updateStatus(final Context context) {
        // FIXME this is what sendPresence already does
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PRESENCE);
        i.putExtra(EXTRA_STATUS, Preferences.getStatusMessage());
        context.startService(i);
    }

    /**
     * Sends a chat state message.
     */
    public static void sendChatState(final Context context, String to, ChatState state) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.chatState", state.name());
        i.putExtra("org.kontalk.message.standalone", true);
        context.startService(i);
    }

    /**
     * Sends a text message.
     */
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

    public static void sendGroupTextMessage(final Context context, String groupJid,
        String groupSubject, String[] to,
        String text, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", TextComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.group.subject", groupSubject);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.body", text);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void createGroup(final Context context, String groupJid,
        String groupSubject, String[] to, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", GroupCommandComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.group.subject", groupSubject);
        i.putExtra("org.kontalk.message.group.command", GROUP_COMMAND_CREATE);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void leaveGroup(final Context context, String groupJid,
        String[] to, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", GroupCommandComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.group.command", GROUP_COMMAND_PART);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void addGroupMembers(final Context context, String groupJid,
        String groupSubject, String[] to, String[] members, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", GroupCommandComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.group.subject", groupSubject);
        i.putExtra("org.kontalk.message.group.command", GROUP_COMMAND_MEMBERS);
        i.putExtra("org.kontalk.message.group.add", members);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void removeGroupMembers(final Context context, String groupJid,
        String groupSubject, String[] to, String[] members, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", GroupCommandComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.group.subject", groupSubject);
        i.putExtra("org.kontalk.message.group.command", GROUP_COMMAND_MEMBERS);
        i.putExtra("org.kontalk.message.group.remove", members);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void setGroupSubject(final Context context, String groupJid,
        String groupSubject, String[] to, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", GroupCommandComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.group.subject", groupSubject);
        i.putExtra("org.kontalk.message.group.command", GROUP_COMMAND_SUBJECT);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    /**
     * Sends a binary message.
     */
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

    public static void sendGroupBinaryMessage(final Context context, String groupJid, String[] to,
        String mime, Uri localUri, long length, String previewPath,
        boolean encrypt, int compress, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.media.uri", localUri.toString());
        i.putExtra("org.kontalk.message.length", length);
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.compress", compress);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    /**
     * Sends  a location message
     */
    public static void sendLocationMessage(final Context context, String to, String text,
        double lat, double lon, String geoText, String geoStreet, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", LocationComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.body", text);
        i.putExtra("org.kontalk.message.geo_lat", lat);
        i.putExtra("org.kontalk.message.geo_lon", lon);

        if (geoText != null)
            i.putExtra("org.kontalk.message.geo_text", geoText);
        if (geoStreet != null)
            i.putExtra("org.kontalk.message.geo_street", geoStreet);

        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    /**
     * Sends group location message
     */
    public static void sendGroupLocationMessage(final Context context, String groupJid,
        String groupSubject, String[] to,
        String text, double lat, double lon, String geoText, String geoStreet, boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", LocationComponent.MIME_TYPE);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.group.subject", groupSubject);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.body", text);
        i.putExtra("org.kontalk.message.geo_lat", lat);
        i.putExtra("org.kontalk.message.geo_lon", lon);

        if (geoText != null)
            i.putExtra("org.kontalk.message.geo_text", geoText);
        if (geoStreet != null)
            i.putExtra("org.kontalk.message.geo_street", geoStreet);

        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void sendGroupUploadedMedia(final Context context, String groupJid, String[] to,
        String mime, Uri localUri, long length, String previewPath, String fetchUrl,
        boolean encrypt, long msgId, String packetId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.packetId", packetId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.group.jid", groupJid);
        i.putExtra("org.kontalk.message.to", to);
        i.putExtra("org.kontalk.message.preview.uri", localUri.toString());
        i.putExtra("org.kontalk.message.length", length);
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.body", fetchUrl);
        i.putExtra("org.kontalk.message.fetch.url", fetchUrl);
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
        i.putExtra("org.kontalk.message.body", fetchUrl);
        i.putExtra("org.kontalk.message.fetch.url", fetchUrl);
        i.putExtra("org.kontalk.message.encrypt", encrypt);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void sendMedia(final Context context, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MEDIA_READY);
        i.putExtra("org.kontalk.message.msgId", msgId);
        context.startService(i);
    }

    public static void retryMessage(final Context context, Uri uri, boolean chatEncryptionEnabled) {
        boolean encrypted = Preferences.getEncryptionEnabled(context) && chatEncryptionEnabled;
        MessagesProviderUtils.retryMessage(context, uri, encrypted);
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_RETRY);
        // TODO not implemented yet
        i.putExtra(MessageCenterService.EXTRA_MESSAGE, uri);
        context.startService(i);
    }

    public static void retryMessagesTo(final Context context, String to) {
        MessagesProviderUtils.retryMessagesTo(context, to);
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_RETRY);
        // TODO not implemented yet
        i.putExtra(MessageCenterService.EXTRA_TO, to);
        context.startService(i);
    }

    public static void retryAllMessages(final Context context) {
        MessagesProviderUtils.retryAllMessages(context);
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_RETRY);
        context.startService(i);
    }

    public static String messageId() {
        return StringUtils.randomString(30);
    }

    /**
     * Replies to a presence subscription request.
     */
    public static void replySubscription(final Context context, String to, int action) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_SUBSCRIBED);
        i.putExtra(EXTRA_TO, to);
        i.putExtra(EXTRA_PRIVACY, action);
        context.startService(i);
    }

    public static void regenerateKeyPair(final Context context, String passphrase) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_REGENERATE_KEYPAIR);
        i.putExtra(EXTRA_PASSPHRASE, passphrase);
        context.startService(i);
    }

    public static void importKeyPair(final Context context, Uri keypack, String passphrase) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_IMPORT_KEYPAIR);
        i.putExtra(EXTRA_KEYPACK, keypack);
        i.putExtra(EXTRA_PASSPHRASE, passphrase);
        context.startService(i);
    }

    public static void uploadPrivateKey(final Context context, String exportPassphrase) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_UPLOAD_PRIVATEKEY);
        i.putExtra(EXTRA_EXPORT_PASSPHRASE, exportPassphrase);
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

    public static void requestRosterEntryStatus(final Context context, String to) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_ROSTER_STATUS);
        i.putExtra(MessageCenterService.EXTRA_TO, to);
        context.startService(i);
    }

    public static void requestPresence(final Context context, String to) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_PRESENCE);
        i.putExtra(MessageCenterService.EXTRA_TO, to);
        i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.probe.name());
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
        requestPublicKey(context, to, null);
    }

    public static void requestPublicKey(final Context context, String to, String id) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_PUBLICKEY);
        i.putExtra(EXTRA_TO, to);
        i.putExtra(EXTRA_PACKET_ID, id);
        context.startService(i);
    }

    public static void requestServerList(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_SERVERLIST);
        context.startService(i);
    }

    public static void updateForegroundStatus(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_FOREGROUND);
        context.startService(i);
    }

    /**
     * Starts the push notifications registration process.
     */
    public static void enablePushNotifications(Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PUSH_START);
        context.startService(i);
    }

    /**
     * Starts the push notifications unregistration process.
     */
    public static void disablePushNotifications(Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PUSH_STOP);
        context.startService(i);
    }

    /**
     * Caches the given registration Id for use with push notifications.
     */
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
        try {
            mConnection.sendIqWithResponseCallback(iq, new StanzaListener() {
                @Override
                public void processStanza(Stanza packet) throws NotConnectedException {
                    if (mPushService != null)
                        mPushService.setRegisteredOnServer(regId != null);
                }
            });
        }
        catch (NotConnectedException e) {
            // ignored
        }
        catch (InterruptedException e) {
            // ignored
        }
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
        try {
            mConnection.sendIqWithResponseCallback(iq, new StanzaListener() {
                @Override
                public void processStanza(Stanza packet) throws NotConnectedException {
                    if (mPushService != null)
                        mPushService.setRegisteredOnServer(false);
                }
            });
        }
        catch (NotConnectedException e) {
            // ignored
        }
        catch (InterruptedException e) {
            // ignored
        }
    }

    public static String getPushSenderId() {
        return sPushSenderId;
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

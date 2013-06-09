package org.kontalk.xmpp.service;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.ChatState;
import org.jivesoftware.smackx.packet.ChatStateExtension;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverItems;
import org.jivesoftware.smackx.packet.LastActivity;
import org.kontalk.xmpp.BuildConfig;
import org.kontalk.xmpp.GCMIntentService;
import org.kontalk.xmpp.authenticator.Authenticator;
import org.kontalk.xmpp.client.AckServerReceipt;
import org.kontalk.xmpp.client.BitsOfBinary;
import org.kontalk.xmpp.client.EndpointServer;
import org.kontalk.xmpp.client.KontalkConnection;
import org.kontalk.xmpp.client.MessageEncrypted;
import org.kontalk.xmpp.client.OutOfBandData;
import org.kontalk.xmpp.client.Ping;
import org.kontalk.xmpp.client.PushRegistration;
import org.kontalk.xmpp.client.RawPacket;
import org.kontalk.xmpp.client.ReceivedServerReceipt;
import org.kontalk.xmpp.client.SentServerReceipt;
import org.kontalk.xmpp.client.ServerReceipt;
import org.kontalk.xmpp.client.ServerReceiptRequest;
import org.kontalk.xmpp.client.StanzaGroupExtension;
import org.kontalk.xmpp.client.StanzaGroupExtensionProvider;
import org.kontalk.xmpp.client.UploadExtension;
import org.kontalk.xmpp.client.UploadInfo;
import org.kontalk.xmpp.crypto.Coder;
import org.kontalk.xmpp.message.AbstractMessage;
import org.kontalk.xmpp.message.ImageMessage;
import org.kontalk.xmpp.message.PlainTextMessage;
import org.kontalk.xmpp.message.VCardMessage;
import org.kontalk.xmpp.provider.MyMessages.Messages;
import org.kontalk.xmpp.provider.UsersProvider;
import org.kontalk.xmpp.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.xmpp.ui.MessagingNotification;
import org.kontalk.xmpp.ui.MessagingPreferences;
import org.kontalk.xmpp.util.MediaStorage;
import org.kontalk.xmpp.util.MessageUtils;
import org.kontalk.xmpp.util.RandomString;

import android.accounts.Account;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
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
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

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
        Connection.DEBUG_ENABLED = BuildConfig.DEBUG;
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

    // common parameters
    /** connect to custom server -- TODO not used yet */
    public static final String EXTRA_SERVER = "org.kontalk.server";
    public static final String EXTRA_PACKET_ID = "org.kontalk.packet.id";
    public static final String EXTRA_TYPE = "org.kontalk.packet.type";

    // use with org.kontalk.action.PACKET
    public static final String EXTRA_PACKET = "org.kontalk.packet";
    public static final String EXTRA_PACKET_GROUP = "org.kontalk.packet.group";
    public static final String EXTRA_STAMP = "org.kontalk.packet.delay";

    // use with org.kontalk.action.PRESENCE
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

    // use with org.kontalk.action.ROSTER
    public static final String EXTRA_USERLIST = "org.kontalk.roster.userList";
    public static final String EXTRA_JIDLIST = "org.kontalk.roster.JIDList";

    // use with org.kontalk.action.LAST_ACTIVITY
    public static final String EXTRA_SECONDS = "org.kontalk.last.seconds";

    // other
    public static final String GCM_REGISTRATION_ID = "org.kontalk.GCM_REGISTRATION_ID";

    /** Idle signal. */
    private static final int MSG_IDLE = 1;

    /** Push notifications enabled flag. */
    private boolean mPushNotifications;
    /** Server push sender id. This is static so {@link GCMIntentService} can see it. */
    private static String mPushSenderId;
    /** GCM registration id. */
    private String mPushRegistrationId;
    /** Flag marking a currently ongoing GCM registration cycle (unregister/register) */
    private boolean mPushRegistrationCycle;

    private LocalBroadcastManager mLocalBroadcastManager;   // created in onCreate

    /** Cached last used server. */
    private EndpointServer mServer;
    /** The connection helper instance. */
    private XMPPConnectionHelper mHelper;
    /** The connection instance. */
    private KontalkConnection mConnection;
    /** My username (account name). */
    private String mMyUsername;

    /** Supported upload services. */
    private Map<String, String> mUploadServices;

    /** Idle handler. */
    private IdleConnectionHandler mIdleHandler;

    /** Messages waiting for server receipt (packetId: internalStorageId). */
    private Map<String, Long> mWaitingReceipt = new HashMap<String, Long>();

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
                // we registered push notification - shutdown message center
                if (service.mPushRegistrationId != null) {
                    Log.d(TAG, "shutting down message center due to inactivity");
                    service.stopSelf();
                }
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
                    time = MessagingPreferences.getIdleTimeMillis(service, DEFAULT_IDLE_TIME);
                else
                    time = DEFAULT_IDLE_TIME;

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
        configure(ProviderManager.getInstance());

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        // create idle handler
        HandlerThread thread = new HandlerThread("IdleThread", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mIdleHandler = new IdleConnectionHandler(this, thread.getLooper());
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

        if (mConnection != null && mConnection.isConnected())
            mConnection.sendPacket(packet);
    }

    private void configure(ProviderManager pm) {
        pm.addIQProvider(Ping.ELEMENT_NAME, Ping.NAMESPACE, new Ping.Provider());
        pm.addIQProvider(UploadInfo.ELEMENT_NAME, UploadInfo.NAMESPACE, new UploadInfo.Provider());
        pm.addExtensionProvider(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE, new StanzaGroupExtensionProvider());
        pm.addExtensionProvider(SentServerReceipt.ELEMENT_NAME, SentServerReceipt.NAMESPACE, new SentServerReceipt.Provider());
        pm.addExtensionProvider(ReceivedServerReceipt.ELEMENT_NAME, ReceivedServerReceipt.NAMESPACE, new ReceivedServerReceipt.Provider());
        pm.addExtensionProvider(ServerReceiptRequest.ELEMENT_NAME, ServerReceiptRequest.NAMESPACE, new ServerReceiptRequest.Provider());
        pm.addExtensionProvider(AckServerReceipt.ELEMENT_NAME, AckServerReceipt.NAMESPACE, new AckServerReceipt.Provider());
        pm.addExtensionProvider(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE, new OutOfBandData.Provider());
        pm.addExtensionProvider(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE, new BitsOfBinary.Provider());
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

        if (mHelper != null) {
            mHelper.setListener(null);
            // this is because of NetworkOnMainThreadException
            new AbortThread(mHelper).start();
            mHelper = null;
        }
        if (mConnection != null) {
            mConnection.removeConnectionListener(this);
            // this is because of NetworkOnMainThreadException
            new DisconnectThread(mConnection).start();
            mConnection = null;
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
            }
            catch (Exception e) {
                // ignored
            }
        }
    }

    private static final class DisconnectThread extends Thread {
        private final Connection mConn;
        public DisconnectThread(Connection conn) {
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

            else {
                // no command means normal service start, connect if not connected
                doConnect = true;
            }

            if (canConnect && doConnect)
                createConnection();
        }
        else {
            Log.v(TAG, "restarting after service crash");
        }
    }

    /**
     * Create connection to server if needed.
     * WARNING this method blocks! Be sure to call it from a separate thread.
     */
    private synchronized void createConnection() {
        if (mConnection == null || (!mConnection.isAuthenticated() && mHelper == null)) {
            // reset push notification variable
            mPushNotifications = MessagingPreferences.getPushNotificationsEnabled(this);
            // reset waiting messages
            mWaitingReceipt.clear();

            // retrieve account name
            Account acc = Authenticator.getDefaultAccount(this);
            mMyUsername = (acc != null) ? acc.name : null;

            // get server from preferences
            mServer = MessagingPreferences.getEndpointServer(this);

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
    public void created() {
        Log.v(TAG, "connection created.");
        mConnection = (KontalkConnection) mHelper.getConnection();

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
    }

    @Override
    public void connected() {
        // not used.
    }

    @Override
    public void authenticated() {
        Log.v(TAG, "authenticated!");
        // helper is not needed any more
        mHelper = null;

        // discovery
        discovery();
        // send presence
        sendPresence();
        // resend failed and pending messages
        resendPendingMessages(false);
        // resend failed and pending received receipts
        resendPendingReceipts();

        broadcast(ACTION_CONNECTED);
    }

    private void broadcast(String action) {
        mLocalBroadcastManager.sendBroadcast(new Intent(action));
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
        String status = MessagingPreferences.getStatusMessageInternal(this);
        Presence p = new Presence(Presence.Type.available);
        if (status != null)
            p.setStatus(status);

        if (mPushNotifications) {
            String pushRegId = GCMRegistrar.getRegistrationId(this);
            if (!TextUtils.isEmpty(pushRegId))
                p.addExtension(new PushRegistration(pushRegId));
        }

        sendPacket(p);
    }

    /**
     * Queries for pending messages and send them through.
     * @param retrying if true, we are retrying to send media messages after
     * receiving upload info (non-media messages will be filtered out)
     */
    private void resendPendingMessages(boolean retrying) {
        StringBuilder filter = new StringBuilder(Messages.DIRECTION + " = " + Messages.DIRECTION_OUT + " AND " +
            Messages.STATUS + " <> " + Messages.STATUS_SENT + " AND " +
            Messages.STATUS + " <> " + Messages.STATUS_RECEIVED + " AND " +
            Messages.STATUS + " <> " + Messages.STATUS_NOTDELIVERED);

        // filter out non-media non-uploaded messages
        if (retrying) filter
            .append(" AND ")
            .append(Messages.FETCH_URL)
            .append(" IS NULL AND ")
            .append(Messages.LOCAL_URI)
            .append(" IS NOT NULL");

        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
            new String[] {
                Messages._ID,
                Messages.PEER,
                Messages.CONTENT,
                Messages.MIME,
                Messages.LOCAL_URI,
                Messages.FETCH_URL,
                Messages.PREVIEW_PATH,
                Messages.ENCRYPT_KEY
            },
            filter.toString(),
            null, Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String userId = c.getString(1);
            byte[] text = c.getBlob(2);
            String mime = c.getString(3);
            String fileUri = c.getString(4);
            String fetchUrl = c.getString(5);
            String previewPath = c.getString(6);
            String key = c.getString(7);

            // media message encountered and no upload service available - delay message
            if (fileUri != null && fetchUrl == null && getUploadService() == null && !retrying) {
                Log.w(TAG, "no upload info received yet, delaying media message");
                continue;
            }

            Bundle b = new Bundle();

            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.mime", mime);
            b.putString("org.kontalk.message.toUser", userId);
            b.putString("org.kontalk.message.encryptKey", key);

            // message has already been uploaded - just send media
            if (fetchUrl != null) {
                b.putString("org.kontalk.message.fetch.url", fetchUrl);
                b.putString("org.kontalk.message.preview.uri", fileUri);
                b.putString("org.kontalk.message.preview.path", previewPath);
            }
            // check if the message contains some large file to be sent
            else if (fileUri != null) {
                b.putString("org.kontalk.message.media.uri", fileUri);
                b.putString("org.kontalk.message.preview.path", previewPath);
            }
            // we have a simple boring plain text message :(
            else {
                b.putString("org.kontalk.message.body", new String(text));
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
            String key = data.getString("org.kontalk.message.encryptKey");
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

            // encrypt message
            if (key != null) {
                byte[] toMessage = null;
                Coder coder = null;
                try {
                    coder = MessagingPreferences.getEncryptCoder(key);
                    if (coder != null)
                        toMessage = coder.encrypt(body.getBytes());
                }
                catch (Exception e) {
                    // should we notify the user this message will be sent cleartext?
                    coder = null;
                }

                if (toMessage != null) {
                    body = Base64.encodeToString(toMessage, Base64.NO_WRAP);
                    m.addExtension(new MessageEncrypted());
                }
            }

            // add download url if present
            if (fetchUrl != null)
                m.addExtension(new OutOfBandData(fetchUrl, mime));

            // received receipt
            String serverId = data.getString("org.kontalk.message.ack");
            if (serverId != null) {
                m.addExtension(new ReceivedServerReceipt(serverId));
            }
            else {
                m.setBody(body);
                // standalone message: no receipt
                if (!data.getBoolean("org.kontalk.message.standalone", false))
                    m.addExtension(new ServerReceiptRequest());
                if (chatState != null)
                    m.addExtension(new ChatStateExtension(chatState));
            }

            sendPacket(m);
        }
    }


    /** Process an incoming message. */
    private Uri incoming(AbstractMessage<?> msg) {
        String sender = msg.getSender(true);

        // save to local storage
        ContentValues values = new ContentValues();
        values.put(Messages.MESSAGE_ID, msg.getId());
        values.put(Messages.PEER, sender);
        values.put(Messages.MIME, msg.getMime());

        // store to file if it's an image message
        byte[] content = msg.getBinaryContent();

        // message has a fetch url - store preview in cache (if any)
        // TODO abstract somehow
        if (msg.getFetchUrl() != null) {
            // use text content for database table
            try {
                content = msg.getTextContent().getBytes();
            }
            catch (Exception e) {
                // TODO i18n
                content = "(error)".getBytes();
            }
        }

        // TODO abstract somehow
        if (msg.getFetchUrl() == null && msg instanceof VCardMessage) {
            String filename = VCardMessage.buildMediaFilename(msg.getId(), msg.getMime());
            File file = null;
            try {
                file = MediaStorage.writeMedia(filename, content);
            }
            catch (IOException e) {
                Log.e(TAG, "unable to write to media storage", e);
            }
            // update uri
            if (file != null)
                msg.setLocalUri(Uri.fromFile(file));

            // use text content for database table
            try {
                content = msg.getTextContent().getBytes();
            }
            catch (Exception e) {
                // TODO i18n
                content = "(error)".getBytes();
            }
        }

        values.put(Messages.CONTENT, content);
        values.put(Messages.ENCRYPTED, msg.isEncrypted());
        values.put(Messages.ENCRYPT_KEY, msg.wasEncrypted() ? "" : null);
        values.put(Messages.FETCH_URL, msg.getFetchUrl());

        File previewFile = msg.getPreviewFile();
        if (previewFile != null)
            values.put(Messages.PREVIEW_PATH, previewFile.getAbsolutePath());

        values.put(Messages.UNREAD, true);
        values.put(Messages.DIRECTION, Messages.DIRECTION_IN);

        values.put(Messages.SERVER_TIMESTAMP, msg.getServerTimestamp());
        values.put(Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(Messages.LENGTH, msg.getLength());

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
        return MessagingPreferences.getOfflineMode(context);
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
            final Intent intent = new Intent(context, MessageCenterService.class);

            // get the URI from the preferences
            EndpointServer server = MessagingPreferences.getEndpointServer(context);
            intent.putExtra(EndpointServer.class.getName(), server.toString());
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
        EndpointServer server = MessagingPreferences.getEndpointServer(context);
        i.putExtra(EXTRA_SERVER, server.toString());
        context.startService(i);
    }

    /**
     * Tells the message center we are holding on to it, preventing shutdown for
     * inactivity.
     */
    public static void hold(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_HOLD);
        // include server uri if server needs to be started
        EndpointServer server = MessagingPreferences.getEndpointServer(context);
        i.putExtra(EXTRA_SERVER, server.toString());
        context.startService(i);
    }

    /**
     * Tells the message center we are releasing it, allowing shutdown for
     * inactivity.
     */
    public static void release(final Context context) {
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
        i.putExtra(EXTRA_STATUS, MessagingPreferences.getStatusMessageInternal(context));
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
    public static void sendTextMessage(final Context context, String userId, String text, String encryptKey, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.mime", PlainTextMessage.MIME_TYPE);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.body", text);
        i.putExtra("org.kontalk.message.encryptKey", encryptKey);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    /** Sends a binary message. */
    public static void sendBinaryMessage(final Context context, String userId, String mime, Uri localUri, String previewPath, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.media.uri", localUri.toString());
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
        context.startService(i);
    }

    public static void sendUploadedMedia(final Context context, String userId,
            String mime, Uri localUri, String previewPath, String fetchUrl, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.preview.uri", localUri.toString());
        i.putExtra("org.kontalk.message.preview.path", previewPath);
        i.putExtra("org.kontalk.message.fetch.url", fetchUrl);
        i.putExtra("org.kontalk.message.chatState", ChatState.active.name());
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
            catch (Exception e) {
                // nothing happens...
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
            Iterator<DiscoverInfo.Feature> features = query.getFeatures();
            while (features.hasNext()) {
                DiscoverInfo.Feature feat = features.next();

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
            Iterator<DiscoverItems.Item> items = query.getItems();
            while (items.hasNext()) {
                DiscoverItems.Item item = items.next();
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
            Iterator<DiscoverItems.Item> items = query.getItems();
            while (items.hasNext()) {
                DiscoverItems.Item item = items.next();
                String jid = item.getEntityID();
                // google push notifications
                if (("gcm.push." + mServer.getNetwork()).equals(jid)) {
                    mPushSenderId = item.getNode();

                    if (mPushNotifications) {
                        String oldSender = MessagingPreferences.getPushSenderId(MessageCenterService.this);

                        // store the new sender id
                        MessagingPreferences.setPushSenderId(MessageCenterService.this, mPushSenderId);

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

    /** Listener for presence stanzas. */
    private final class PresenceListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            try {
                Log.v(TAG, "presence: " + packet);
                Presence p = (Presence) packet;
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
                            long changed;
                            Date date = ((ReceivedServerReceipt)ext).getTimestamp();
                            if (date != null)
                                changed = date.getTime();
                            else
                                changed = System.currentTimeMillis();

                            // message has been delivered: check if we have previously stored the server id
                            if (msgId > 0) {
                                ContentValues values = new ContentValues(3);
                                values.put(Messages.MESSAGE_ID, ext.getId());
                                values.put(Messages.STATUS, Messages.STATUS_RECEIVED);
                                values.put(Messages.STATUS_CHANGED, changed);
                                cr.update(ContentUris.withAppendedId(Messages.CONTENT_URI, msgId),
                                    values, selectionOutgoing, null);

                                mWaitingReceipt.remove(id);
                            }
                            else {
                                Uri msg = Messages.getUri(ext.getId());
                                ContentValues values = new ContentValues(2);
                                values.put(Messages.STATUS, Messages.STATUS_RECEIVED);
                                values.put(Messages.STATUS_CHANGED, changed);
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
                        msgId = "incoming" + RandomString.generate(6);

                    String mime = null;
                    String sender = StringUtils.parseName(from);
                    String body = m.getBody();
                    byte[] content = body != null ? body.getBytes() : new byte[0];
                    long length = content.length;

                    // message decryption
                    boolean wasEncrypted = (m.getExtension(MessageEncrypted.ELEMENT_NAME, MessageEncrypted.NAMESPACE) != null);
                    boolean isEncrypted = wasEncrypted;
                    if (isEncrypted && content != null) {
                        // decrypt message
                        Coder coder = MessagingPreferences.getDecryptCoder(MessageCenterService.this, mMyUsername);
                        try {
                            // decode base64
                            content = Base64.decode(content, Base64.DEFAULT);
                            // length of raw encrypted message
                            length = content.length;
                            // decrypt
                            content = coder.decrypt(content);
                            length = content.length;
                            isEncrypted = false;
                        }
                        catch (Exception exc) {
                            // pass over the message even if encrypted
                            // UI will warn the user about that and wait
                            // for user decisions
                            Log.e(TAG, "decryption failed", exc);
                        }
                    }

                    // delayed deliver extension
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

                    // out of band data
                    File previewFile = null;
                    String fetchUrl = null;
                    PacketExtension _media = m.getExtension(OutOfBandData.ELEMENT_NAME, OutOfBandData.NAMESPACE);
                    if (_media != null && _media instanceof OutOfBandData) {
                        OutOfBandData media = (OutOfBandData) _media;
                        mime = media.getMime();
                        fetchUrl = media.getUrl();
                        length = -1;

                        // bits-of-binary for preview
                        PacketExtension _preview = m.getExtension(BitsOfBinary.ELEMENT_NAME, BitsOfBinary.NAMESPACE);
                        if (_preview != null && _preview instanceof BitsOfBinary) {
                            BitsOfBinary preview = (BitsOfBinary) _preview;
                            String previewMime = preview.getType();
                            if (previewMime == null)
                                previewMime = MediaStorage.THUMBNAIL_MIME;

                            String filename = ImageMessage.buildMediaFilename(msgId, previewMime);
                            try {
                                previewFile = MediaStorage.writeInternalMedia(MessageCenterService.this, filename, preview.getContents());
                            }
                            catch (IOException e) {
                                Log.w(TAG, "error storing thumbnail", e);
                            }
                        }
                    }

                    AbstractMessage<?> msg = null;

                    // plain text message
                    if (mime == null || PlainTextMessage.supportsMimeType(mime)) {
                        // TODO convert to global pool
                        msg = new PlainTextMessage(MessageCenterService.this, msgId, serverTimestamp, sender, content, isEncrypted);
                    }

                    // image message
                    else if (ImageMessage.supportsMimeType(mime)) {
                        // extra argument: mime (first parameter)
                        msg = new ImageMessage(MessageCenterService.this, mime, msgId, serverTimestamp, sender, content, isEncrypted);
                    }

                    // vcard message
                    else if (VCardMessage.supportsMimeType(mime)) {
                        msg = new VCardMessage(MessageCenterService.this, msgId, serverTimestamp, sender, content, isEncrypted);
                    }

                    // TODO else other mime types

                    if (msg != null) {
                        // set length
                        msg.setLength(length);

                        // remember encryption! :)
                        if (wasEncrypted)
                            msg.setWasEncrypted(true);

                        // set the fetch url (if any)
                        if (fetchUrl != null)
                            msg.setFetchUrl(fetchUrl);

                        // set preview path (if any)
                        if (previewFile != null)
                            msg.setPreviewFile(previewFile);

                        Uri msgUri = incoming(msg);
                        if (_ext != null) {
                            // send ack :)
                            ReceivedServerReceipt receipt = new ReceivedServerReceipt(msgId);
                            org.jivesoftware.smack.packet.Message ack = new org.jivesoftware.smack.packet.Message(m.getFrom(),
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
                    }
                }
            }
        }
    }

}

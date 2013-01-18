package org.kontalk.xmpp.service;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.jivesoftware.smackx.packet.LastActivity;
import org.kontalk.xmpp.BuildConfig;
import org.kontalk.xmpp.authenticator.Authenticator;
import org.kontalk.xmpp.client.EndpointServer;
import org.kontalk.xmpp.client.MessageEncrypted;
import org.kontalk.xmpp.client.Ping;
import org.kontalk.xmpp.client.RawPacket;
import org.kontalk.xmpp.client.ReceivedServerReceipt;
import org.kontalk.xmpp.client.SentServerReceipt;
import org.kontalk.xmpp.client.ServerReceipt;
import org.kontalk.xmpp.client.ServerReceiptRequest;
import org.kontalk.xmpp.client.StanzaGroupExtension;
import org.kontalk.xmpp.client.StanzaGroupExtensionProvider;
import org.kontalk.xmpp.crypto.Coder;
import org.kontalk.xmpp.message.PlainTextMessage;
import org.kontalk.xmpp.provider.MyMessages.Messages;
import org.kontalk.xmpp.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.xmpp.ui.MessagingNotification;
import org.kontalk.xmpp.ui.MessagingPreferences;
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
import android.os.MessageQueue;
import android.os.MessageQueue.IdleHandler;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;
import android.util.Log;


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
    public static final String ACTION_IDLE = "org.kontalk.action.IDLE";
    public static final String ACTION_RESTART = "org.kontalk.action.RESTART";
    public static final String ACTION_MESSAGE = "org.kontalk.action.MESSAGE";

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

    // use with org.kontalk.action.ROSTER
    public static final String EXTRA_USERLIST = "org.kontalk.roster.userList";
    public static final String EXTRA_JIDLIST = "org.kontalk.roster.JIDList";

    // use with org.kontalk.action.LAST_ACTIVITY
    public static final String EXTRA_SECONDS = "org.kontalk.last.seconds";

    /** Quit immediately and close all connections. */
    private static final int MSG_QUIT = 0;
    /** A simple packet to be sent. */
    private static final int MSG_PACKET = 1;
    /** A packet XML string to be sent. */
    private static final int MSG_PACKET_XML = 2;
    /** Forced restart. */
    private static final int MSG_RESTART = 3;
    /** Idle signal. */
    private static final int MSG_IDLE = 4;
    /** Ping signal (dummy message just to iterate the handler). */
    private static final int MSG_PING = 5;
    /** Outgoing message. */
    private static final int MSG_MESSAGE = 6;
    /** Roster match request. */
    private static final int MSG_ROSTER = 7;
    /** Presence packet. */
    private static final int MSG_PRESENCE = 8;
    /** Last activity iq. */
    private static final int MSG_LAST_ACTIVITY = 9;

    private LocalBroadcastManager mLocalBroadcastManager;   // created in onCreate

    /** Cached last used server. */
    private EndpointServer mServer;
    /** The connection helper instance. */
    private XMPPConnectionHelper mConnector;
    /** Main handler. */
    private ServiceHandler mServiceHandler;
    /** Main looper. */
    private Looper mLooper;
    /** My username (account name). */
    private String mMyUsername;

    /** Messages waiting for server receipt (packetId: internalStorageId). */
    private Map<String, Long> mWaitingReceipt = new HashMap<String, Long>();

    private static final class ServiceHandler extends Handler implements IdleHandler {
        /** A weak reference to the message center instance. */
        WeakReference<MessageCenterService> s;
        /** How much time to wait to idle the message center. */
        private final int IDLE_MSG_TIME = 60000;
        /** Reference counter. */
        private int mRefCount;
        /** Idle flag. */
        private volatile Boolean mIdle = false;

        public ServiceHandler(MessageCenterService service, Looper looper) {
            super(looper);
            s = new WeakReference<MessageCenterService>(service);

            // set idle handler
            Looper.myQueue().addIdleHandler(this);
        }

        @Override
        public void handleMessage(Message msg) {
            boolean consumed = false;
            MessageCenterService service = s.get();
            if (service != null) {
                Log.v(TAG, "processing message " + msg);

                // shutdown immediate
                if (msg.what == MSG_QUIT) {
                    if (service.mConnector != null)
                        service.mConnector.shutdown();

                    // quit the looper
                    getLooper().quit();
                    consumed = true;
                }

                // service restart
                else if (msg.what == MSG_RESTART) {
                    if (service.mConnector != null)
                        service.mConnector.shutdown();

                    // restart queue!
                    removeCallbacksAndMessages(null);

                    // reconnect immediately!
                    service.createConnection();
                    consumed = true;
                }
                else {
                    // be sure connection is valid
                    service.createConnection();
                    // handle message
                    consumed = handleMessage(service, msg);
                }
            }
            else {
                Log.v(TAG, "service has vanished - aborting");
                getLooper().quit();
                consumed = true;
            }

            if (!consumed)
                super.handleMessage(msg);
        }

        private boolean handleMessage(MessageCenterService service, Message msg) {
            switch (msg.what) {
                // send raw packet (Packet object)
                case MSG_PACKET: {
                    return handlePacket(service, (Packet) msg.obj);
                }

                // send raw packet (XML string)
                case MSG_PACKET_XML: {
                    return handlePacketXML(service, msg.obj);
                }

                // send plain text message
                case MSG_MESSAGE: {
                    return handleMessage(service, (Bundle) msg.obj);
                }

                case MSG_ROSTER: {
                    return handleRoster(service, (Bundle) msg.obj);
                }

                // presence packet
                case MSG_PRESENCE: {
                    return handlePresence(service, (Bundle) msg.obj);
                }

                // last activity iq
                case MSG_LAST_ACTIVITY: {
                    return handleLastActivity(service, (Bundle) msg.obj);
                }

                // idle message
                case MSG_IDLE: {
                    // we registered push notification - shutdown message center
                    /* TODO
                    if (service.mPushRegistrationId != null) {
                        Log.d(TAG, "shutting down message center due to inactivity");
                        MessageCenterServiceLegacy.idleMessageCenter(w.mContext);
                    }
                    */
                    MessageCenterService.idle(service);
                    return true;
                }
            }

            return false;
        }

        /**
         * Handles bare packets.
         * @message MSG_PACKET
         */
        private boolean handlePacket(MessageCenterService service, Packet packet) {
            service.mConnector.getConnection().sendPacket(packet);
            return true;
        }

        /**
         * Handles XML string packets.
         * @message MSG_PACKET_XML
         */
        private boolean handlePacketXML(MessageCenterService service, Object packet) {
            Connection conn = service.mConnector.getConnection();
            try {
                String[] data = (String[]) packet;
                for (String pack : data)
                    conn.sendPacket(new RawPacket(pack));
            }
            catch (ClassCastException e) {
                conn.sendPacket(new RawPacket((String) packet));
            }
            return true;
        }

        /**
         * Handles roster requests.
         * @message MSG_ROSTER
         */
        private boolean handleRoster(MessageCenterService service, Bundle data) {
            String id = data.getString(EXTRA_PACKET_ID);
            String[] list = data.getStringArray(EXTRA_USERLIST);
            int c = list.length;
            RosterPacket iq = new RosterPacket();
            iq.setPacketID(id);
            // iq default type is get

            for (int i = 0; i < c; i++)
                iq.addRosterItem(new RosterPacket.Item(list[i] + "@" + service.mServer.getNetwork(), null));

            service.mConnector.getConnection().sendPacket(iq);
            return true;
        }

        /**
         * Handles outgoing presence.
         * @message MSG_PRESENCE
         */
        private boolean handlePresence(MessageCenterService service, Bundle data) {
            String type = data.getString(EXTRA_TYPE);

            String id = data.getString(EXTRA_PACKET_ID);

            String to;
            String toUserid = data.getString(EXTRA_TO_USERID);
            if (toUserid != null)
                to = MessageUtils.toJID(toUserid, service.mServer.getNetwork());
            else
                to = data.getString(EXTRA_TO);

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
                String show = data.getString(EXTRA_SHOW);
                Presence p = new Presence(type != null ? Presence.Type.valueOf(type) : Presence.Type.available);
                p.setPacketID(id);
                p.setTo(to);
                if (data.containsKey(EXTRA_PRIORITY))
                    p.setPriority(data.getInt(EXTRA_PRIORITY, 0));
                p.setStatus(data.getString(EXTRA_STATUS));
                if (show != null)
                    p.setMode(Presence.Mode.valueOf(show));
                pack = p;
            }

            service.mConnector.getConnection().sendPacket(pack);
            return true;

        }

        /**
         * Handles last activity requests.
         * @message MSG_LAST_ACTIVITY
         */
        private boolean handleLastActivity(MessageCenterService service, Bundle data) {
            LastActivity p = new LastActivity();

            String to;
            String toUserid = data.getString(EXTRA_TO_USERID);
            if (toUserid != null)
                to = MessageUtils.toJID(toUserid, service.mServer.getNetwork());
            else
                to = data.getString(EXTRA_TO);

            p.setPacketID(data.getString(EXTRA_PACKET_ID));
            p.setTo(to);

            service.mConnector.getConnection().sendPacket(p);
            return true;
        }

        /**
         * Handles outgoing messages.
         * @message MSG_MESSAGE
         */
        private boolean handleMessage(MessageCenterService service, Bundle data) {
            Connection conn = service.mConnector.getConnection();

            // check if message is already pending
            long msgId = data.getLong("org.kontalk.message.msgId");
            if (service.mWaitingReceipt.containsValue(msgId)) {
                Log.v(TAG, "message already queued and waiting - dropping");
                return true;
            }

            org.jivesoftware.smack.packet.Message m = new org.jivesoftware.smack.packet.Message();
            m.setType(org.jivesoftware.smack.packet.Message.Type.chat);
            String to = data.getString("org.kontalk.message.to");
            if (to == null) {
                to = data.getString("org.kontalk.message.toUser");
                to += '@' + service.mServer.getNetwork();
            }
            if (to != null) m.setTo(to);

            if (msgId > 0) {
                String id = m.getPacketID();
                service.mWaitingReceipt.put(id, msgId);
            }

            String body = data.getString("org.kontalk.message.body");
            String key = data.getString("org.kontalk.message.encryptKey");

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

            m.setBody(body);
            m.addExtension(new ServerReceiptRequest());
            conn.sendPacket(m);

            return true;
        }

        @Override
        public boolean queueIdle() {
            // remove the idle message anyway
            removeMessages(MSG_IDLE);

            if (mRefCount <= 0 && getLooper().getThread().isAlive())
                sendMessageDelayed(obtainMessage(MSG_IDLE), IDLE_MSG_TIME);

            return true;
        }

        public void hold() {
            mRefCount++;
            post(new Runnable() {
                public void run() {
                    Looper.myQueue().removeIdleHandler(ServiceHandler.this);
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
                        Looper.myQueue().addIdleHandler(ServiceHandler.this);
                    }
                });
            }
        }

        /** Schedules the handler to exit as soon as possible. */
        public void idle() {
            mIdle = true;
            post(new Runnable() {
                public void run() {
                    Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
                        @Override
                        public boolean queueIdle() {
                            synchronized (mIdle) {
                                Context ctx = s.get();
                                if (ctx != null && mIdle)
                                    MessageCenterService.stop(ctx);
                            }
                            return false;
                        }
                    });
                }
            });
        }

    }

    @Override
    public void onCreate() {
        SmackAndroid.init(getApplicationContext());
        configure(ProviderManager.getInstance());

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(this, mLooper);
    }

    private void configure(ProviderManager pm) {
        pm.addIQProvider(Ping.ELEMENT_NAME, Ping.NAMESPACE, new Ping.Provider());
        pm.addExtensionProvider(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE, new StanzaGroupExtensionProvider());
        pm.addExtensionProvider(SentServerReceipt.ELEMENT_NAME, SentServerReceipt.NAMESPACE, new SentServerReceipt.Provider());
        pm.addExtensionProvider(ReceivedServerReceipt.ELEMENT_NAME, ReceivedServerReceipt.NAMESPACE, new ReceivedServerReceipt.Provider());
        pm.addExtensionProvider(ServerReceiptRequest.ELEMENT_NAME, ServerReceiptRequest.NAMESPACE, new ServerReceiptRequest.Provider());
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
        // send quit message to handler
        mServiceHandler.sendEmptyMessage(MSG_QUIT);
    }

    private void handleIntent(Intent intent) {
        boolean canSendMessage = false;

        if (intent != null) {
            Message msg = null;
            String action = intent.getAction();

            // proceed to start only if network is available
            canSendMessage = isNetworkConnectionAvailable(this) && !isOfflineMode(this);

            if (ACTION_PACKET.equals(action)) {
                Object data;
                String[] group = intent.getStringArrayExtra(EXTRA_PACKET_GROUP);
                if (group != null)
                    data = group;
                else
                    data = intent.getStringExtra(EXTRA_PACKET);

                msg = mServiceHandler.obtainMessage(MSG_PACKET_XML, data);
            }

            else if (ACTION_HOLD.equals(action)) {
                mServiceHandler.hold();
            }

            else if (ACTION_RELEASE.equals(action)) {
                mServiceHandler.release();
            }

            else if (ACTION_IDLE.equals(action)) {
                mServiceHandler.idle();
            }

            else if (ACTION_CONNECTED.equals(action)) {
                if (mConnector != null && mConnector.isConnected())
                    broadcast(ACTION_CONNECTED);
            }

            else if (ACTION_RESTART.equals(action)) {
                if (canSendMessage)
                    msg = mServiceHandler.obtainMessage(MSG_RESTART);
            }

            else if (ACTION_MESSAGE.equals(action)) {
                if (canSendMessage)
                    msg = mServiceHandler.obtainMessage(MSG_MESSAGE, intent.getExtras());
            }

            else if (ACTION_ROSTER.equals(action)) {
                if (canSendMessage)
                    msg = mServiceHandler.obtainMessage(MSG_ROSTER, intent.getExtras());
            }

            else if (ACTION_PRESENCE.equals(action)) {
                if (canSendMessage)
                    msg = mServiceHandler.obtainMessage(MSG_PRESENCE, intent.getExtras());
            }

            else if (ACTION_LAST_ACTIVITY.equals(action)) {
                if (canSendMessage)
                    msg = mServiceHandler.obtainMessage(MSG_LAST_ACTIVITY, intent.getExtras());
            }

            // no command means normal service start
            if (msg == null && canSendMessage)
                msg = mServiceHandler.obtainMessage(MSG_PING);

            if (msg != null)
                mServiceHandler.sendMessage(msg);
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
        if (mConnector == null || !mConnector.isConnected()) {
            // retrieve account name
            Account acc = Authenticator.getDefaultAccount(this);
            mMyUsername = (acc != null) ? acc.name : null;

            // fallback: get server from preferences
            if (mServer == null)
                mServer = MessagingPreferences.getEndpointServer(this);

            mConnector = new XMPPConnectionHelper(this, mServer, false);
            mConnector.setListener(this);
            mConnector.connect();
        }
    }

    @Override
    public void connectionClosed() {
        Log.v(TAG, "connection closed");
    }

    @Override
    public void connectionClosedOnError(Exception error) {
        Log.w(TAG, "connection closed with error", error);
        mServiceHandler.sendEmptyMessage(MSG_RESTART);
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
    public void created() {
        Log.v(TAG, "connection created.");
        Connection conn = mConnector.getConnection();
        PacketFilter filter;

        filter = new PacketTypeFilter(Ping.class);
        conn.addPacketListener(new PingListener(), filter);

        filter = new PacketTypeFilter(Presence.class);
        conn.addPacketListener(new PresenceListener(), filter);

        filter = new PacketTypeFilter(RosterPacket.class);
        conn.addPacketListener(new RosterListener(), filter);

        filter = new PacketTypeFilter(org.jivesoftware.smack.packet.Message.class);
        conn.addPacketListener(new MessageListener(), filter);

        filter = new PacketTypeFilter(LastActivity.class);
        conn.addPacketListener(new LastActivityListener(), filter);
    }

    @Override
    public void connected() {
        // not used.
    }

    @Override
    public void authenticated() {
        Log.v(TAG, "authenticated!");

        // send presence
        sendPresence();
        // resend failed and pending messages
        resendPendingMessages();
        // receipts will be sent while consuming incoming messages

        broadcast(ACTION_CONNECTED);
    }

    private void broadcast(String action) {
        mLocalBroadcastManager.sendBroadcast(new Intent(action));
    }

    /** Sends our presence. */
    private void sendPresence() {
        String status = MessagingPreferences.getStatusMessageInternal(this);
        Presence p = new Presence(Presence.Type.available);
        if (status != null)
            p.setStatus(status);

        mConnector.getConnection().sendPacket(p);
    }

    private void resendPendingMessages() {
        Cursor c = getContentResolver().query(Messages.CONTENT_URI,
            new String[] {
                Messages._ID,
                Messages.PEER,
                Messages.CONTENT,
                Messages.MIME,
                Messages.LOCAL_URI,
                Messages.ENCRYPT_KEY
            },
            Messages.DIRECTION + " = " + Messages.DIRECTION_OUT + " AND " +
            Messages.STATUS + " <> " + Messages.STATUS_SENT + " AND " +
            Messages.STATUS + " <> " + Messages.STATUS_RECEIVED + " AND " +
            Messages.STATUS + " <> " + Messages.STATUS_NOTDELIVERED,
            null, Messages._ID);

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String userId = c.getString(1);
            byte[] text = c.getBlob(2);
            String mime = c.getString(3);
            String fileUri = c.getString(4);
            String key = c.getString(5);

            Bundle b = new Bundle();
            b.putLong("org.kontalk.message.msgId", id);
            b.putString("org.kontalk.message.mime", mime);
            b.putString("org.kontalk.message.toUser", userId);
            b.putString("org.kontalk.message.encryptKey", key);

            // check if the message contains some large file to be sent
            if (fileUri != null) {
                b.putString("org.kontalk.message.media.uri", fileUri);
            }
            // we have a simple boring plain text message :(
            else {
                b.putString("org.kontalk.message.body", new String(text));
            }

            Log.v(TAG, "resending pending message " + id);
            mServiceHandler.sendMessage(mServiceHandler.obtainMessage(MSG_MESSAGE, b));
        }

        c.close();
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

    /** Tells the message center we are idle, taking necessary actions. */
    public static void idle(final Context context) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_IDLE);
        context.startService(i);
    }

    /** Broadcasts our presence to the server. */
    public static void updateStatus(final Context context) {
        // FIXME this is what sendPresence already does
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(ACTION_PRESENCE);
        i.putExtra(EXTRA_STATUS, MessagingPreferences.getStatusMessageInternal(context));
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
        context.startService(i);
    }

    /** Sends a binary message. */
    public static void sendBinaryMessage(final Context context, String userId, String mime, Uri localUri, long msgId) {
        Intent i = new Intent(context, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_MESSAGE);
        i.putExtra("org.kontalk.message.msgId", msgId);
        i.putExtra("org.kontalk.message.mime", mime);
        i.putExtra("org.kontalk.message.toUser", userId);
        i.putExtra("org.kontalk.message.media.uri", localUri.toString());
        context.startService(i);
    }

    private final class PingListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            IQ response = IQ.createResultIQ((IQ) packet);
            mServiceHandler.sendMessage(mServiceHandler.obtainMessage(MSG_PACKET, response));
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
                i.putExtra(EXTRA_TYPE, type != null ? type.toString() : Presence.Type.available.toString());
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
                i.putExtra(EXTRA_SHOW, mode != null ? mode.toString() : Presence.Mode.available.toString());
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

        @Override
        public void processPacket(Packet packet) {
            org.jivesoftware.smack.packet.Message m = (org.jivesoftware.smack.packet.Message) packet;
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

                }
            }

            // incoming message
            else {
                String msgId = null;
                if (_ext != null) {
                    ServerReceiptRequest req = (ServerReceiptRequest) _ext;
                    // send ack :)
                    ReceivedServerReceipt receipt = new ReceivedServerReceipt(req.getId());
                    org.jivesoftware.smack.packet.Message ack = new org.jivesoftware.smack.packet.Message(m.getFrom(),
                        org.jivesoftware.smack.packet.Message.Type.chat);
                    ack.addExtension(receipt);
                    mServiceHandler.sendMessage(mServiceHandler.obtainMessage(MSG_PACKET, ack));
                }

                if (msgId == null)
                    msgId = "incoming" + RandomString.generate(6);

                String from = m.getFrom();
                String sender = StringUtils.parseName(from);
                byte[] content = m.getBody().getBytes();
                long length = content.length;

                // message decryption
                boolean wasEncrypted = (m.getExtension(MessageEncrypted.ELEMENT_NAME, MessageEncrypted.NAMESPACE) != null);
                boolean isEncrypted = wasEncrypted;
                if (isEncrypted) {
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

                long now = System.currentTimeMillis();

                // delayed deliver extension
                PacketExtension _delay = m.getExtension("delay", "urn:xmpp.delay");
                long serverTimestamp = 0;
                if (_delay != null && _delay instanceof DelayInformation) {
                    serverTimestamp = ((DelayInformation) _delay).getStamp().getTime();
                }
                else
                    serverTimestamp = now;

                // save to local storage
                ContentValues values = new ContentValues();
                values.put(Messages.MESSAGE_ID, msgId);
                values.put(Messages.PEER, sender);
                values.put(Messages.MIME, PlainTextMessage.MIME_TYPE);
                values.put(Messages.CONTENT, content);
                values.put(Messages.ENCRYPTED, isEncrypted);
                values.put(Messages.ENCRYPT_KEY, wasEncrypted ? "" : null);
                values.put(Messages.UNREAD, true);
                values.put(Messages.DIRECTION, Messages.DIRECTION_IN);

                values.put(Messages.SERVER_TIMESTAMP, serverTimestamp);
                values.put(Messages.TIMESTAMP, now);
                values.put(Messages.LENGTH, length);
                try {
                    getContentResolver().insert(Messages.CONTENT_URI, values);
                }
                catch (SQLiteConstraintException econstr) {
                    // duplicated message, skip it
                }

                if (!sender.equalsIgnoreCase(MessagingNotification.getPaused()))
                    // update notifications (delayed)
                    MessagingNotification.delayedUpdateMessagesNotification(getApplicationContext(), true);
            }
        }
    }

}

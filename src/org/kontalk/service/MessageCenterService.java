package org.kontalk.service;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Iterator;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.RosterPacket;
import org.jivesoftware.smack.provider.ProviderManager;
import org.kontalk.BuildConfig;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.RawPacket;
import org.kontalk.client.StanzaGroupExtension;
import org.kontalk.client.StanzaGroupExtensionProvider;
import org.kontalk.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.ui.MessagingPreferences;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import android.util.Log;


/**
 * The Message Center Service.
 * Use {@link Intent}s to deliver commands (via {@link Context#startService}).
 * Service will broadcast intents when certain events occur.
 * @author Daniele Ricci
 * @version 2.0
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

    /** Broadcasted when we are connected to the server. */
    public static final String ACTION_CONNECTED = "org.kontalk.action.CONNECTED";
    /** Broadcasted when we are authenticated to the server. */
    public static final String ACTION_AUTHENTICATED = "org.kontalk.action.AUTHENTICATED";

    /**
     * Broadcasted when a presence stanza is received.
     * Send this intent to broadcast presence.
     */
    public static final String ACTION_PRESENCE = "org.kontalk.action.PRESENCE";

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
    public static final String EXTRA_TO = "org.kontalk.stanza.to";
    public static final String EXTRA_STATUS = "org.kontalk.presence.status";
    public static final String EXTRA_SHOW = "org.kontalk.presence.show";
    public static final String EXTRA_GROUP_ID = "org.kontalk.presence.groupId";
    public static final String EXTRA_GROUP_COUNT = "org.kontalk.presence.groupCount";

    // use with org.kontalk.action.ROSTER
    public static final String EXTRA_USERLIST = "org.kontalk.roster.userList";
    public static final String EXTRA_JIDLIST = "org.kontalk.roster.JIDList";

    /** A simple packet to be sent. */
    private static final int MSG_PACKET = 1;
    /** Forced restart. */
    private static final int MSG_RESTART = 2;
    /** Idle signal. */
    private static final int MSG_IDLE = 3;
    /** Ping signal (dummy message just to iterate the handler). */
    private static final int MSG_PING = 4;
    /** Outgoing message. */
    private static final int MSG_MESSAGE = 5;
    /** Roster match request. */
    private static final int MSG_ROSTER = 6;

    private LocalBroadcastManager mLocalBroadcastManager;   // created in onCreate

    /** Cached last used server. */
    private EndpointServer mServer;
    /** The connection helper instance. */
    private XMPPConnectionHelper mConnector;
    /** Main handler. */
    private ServiceHandler mServiceHandler;
    /** Main looper. */
    private Looper mLooper;

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

            // TODO this.mRefCount = refCount;

            // set idle handler
            if (this.mRefCount <= 0)
                Looper.myQueue().addIdleHandler(this);
        }

        @Override
        public void handleMessage(Message msg) {
            boolean consumed = false;
            MessageCenterService service = s.get();
            if (service != null) {
                Log.v(TAG, "processing message " + msg);
                service.createConnection();

                consumed = handleMessage(service, msg);
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
            Connection conn = service.mConnector.getConnection();

            switch (msg.what) {
                // send raw packet
                case MSG_PACKET: {
                    try {
                        String[] data = (String[]) msg.obj;
                        for (String pack : data)
                            conn.sendPacket(new RawPacket(pack));
                    }
                    catch (ClassCastException e) {
                        conn.sendPacket(new RawPacket((String) msg.obj));
                    }
                    return true;
                }

                // send message
                case MSG_MESSAGE: {
                    Bundle data = (Bundle) msg.obj;
                    org.jivesoftware.smack.packet.Message m = new org.jivesoftware.smack.packet.Message();
                    m.setType(org.jivesoftware.smack.packet.Message.Type.chat);
                    String to = data.getString("org.kontalk.message.to");
                    if (to == null) {
                        to = data.getString("org.kontalk.message.toUser");
                        to += '@' + service.mServer.getNetwork();
                    }
                    if (to != null) m.setTo(to);

                    m.setBody(data.getString("org.kontalk.message.body"));
                    conn.sendPacket(m);
                    return true;
                }

                case MSG_ROSTER: {
                    Bundle data = (Bundle) msg.obj;
                    String id = data.getString(EXTRA_PACKET_ID);
                    String[] list = data.getStringArray(EXTRA_USERLIST);
                    int c = list.length;
                    RosterPacket iq = new RosterPacket();
                    iq.setPacketID(id);
                    // iq default type is get

                    for (int i = 0; i < c; i++)
                        iq.addRosterItem(new RosterPacket.Item(list[i] + "@" + service.mServer.getNetwork(), null));

                    conn.sendPacket(iq);
                    return true;
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
                                    MessageCenterService.stop(s.get());
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
        configure(ProviderManager.getInstance());

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);

        HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(this, mLooper);
    }

    private void configure(ProviderManager pm) {
        pm.addExtensionProvider(StanzaGroupExtension.ELEMENT_NAME, StanzaGroupExtension.NAMESPACE, new StanzaGroupExtensionProvider());
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
        // quit looper
        mLooper.quit();
        // disconnect
        if (mConnector != null)
            mConnector.shutdown();
    }

    private void handleIntent(Intent intent) {
        boolean execStart = false;

        if (intent != null) {
            Message msg = null;
            String action = intent.getAction();

            if (ACTION_PACKET.equals(action)) {
                Object data;
                String[] group = intent.getStringArrayExtra(EXTRA_PACKET_GROUP);
                if (group != null)
                    data = group;
                else
                    data = intent.getStringExtra(EXTRA_PACKET);

                msg = mServiceHandler.obtainMessage(MSG_PACKET, data);
            }

            else if (ACTION_HOLD.equals(action)) {
                mServiceHandler.hold();

                // proceed to start only if network is available
                execStart = isNetworkConnectionAvailable(this) && !isOfflineMode(this);
            }

            else if (ACTION_RELEASE.equals(action)) {
                mServiceHandler.release();
            }

            else if (ACTION_IDLE.equals(action)) {
                mServiceHandler.idle();
            }

            else if (ACTION_RESTART.equals(action)) {
                // TODO
            }

            else if (ACTION_MESSAGE.equals(action)) {
                msg = mServiceHandler.obtainMessage(MSG_MESSAGE, intent.getExtras());
            }

            else if (ACTION_ROSTER.equals(action)) {
                msg = mServiceHandler.obtainMessage(MSG_ROSTER, intent.getExtras());
            }

            // no command means normal service start
            if (msg == null && execStart)
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
    private void createConnection() {
        if (mConnector == null) {
            // fallback: get server from preferences
            if (mServer == null)
                mServer = MessagingPreferences.getEndpointServer(this);

            mConnector = new XMPPConnectionHelper(this, mServer);
            mConnector.setListener(this);
            mConnector.connect();
        }
    }

    @Override
    public void connectionClosed() {
    }

    @Override
    public void connectionClosedOnError(Exception error) {
    }

    @Override
    public void reconnectingIn(int seconds) {
    }

    @Override
    public void reconnectionFailed(Exception error) {
    }

    @Override
    public void reconnectionSuccessful() {
        broadcast(ACTION_CONNECTED);
    }

    @Override
    public void created() {
        Log.v(TAG, "connection created.");
        Connection conn = mConnector.getConnection();
        PacketFilter filter;

        filter = new PacketTypeFilter(Presence.class);
        conn.addPacketListener(new PresenceListener(), filter);

        filter = new PacketTypeFilter(RosterPacket.class);
        conn.addPacketListener(new RosterListener(), filter);
    }

    @Override
    public void connected() {
        Log.v(TAG, "connected!");
        broadcast(ACTION_CONNECTED);
    }

    @Override
    public void authenticated() {
        Log.v(TAG, "authenticated!");
        broadcast(ACTION_AUTHENTICATED);
    }

    private void broadcast(String action) {
        mLocalBroadcastManager.sendBroadcast(new Intent(ACTION_AUTHENTICATED));
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

            Log.v(TAG, "broadcasting roster: " + i);
            mLocalBroadcastManager.sendBroadcast(i);
        }
    }

    private final class PresenceListener implements PacketListener {
        @Override
        public void processPacket(Packet packet) {
            Log.v(TAG, "packet received: " + packet.getClass().getName());
            Presence p = (Presence) packet;
            Intent i = new Intent(ACTION_PRESENCE);
            i.putExtra(EXTRA_FROM, p.getFrom());
            i.putExtra(EXTRA_TO, p.getTo());
            i.putExtra(EXTRA_STATUS, p.getStatus());
            i.putExtra(EXTRA_SHOW, p.getMode());
            i.putExtra(EXTRA_PACKET_ID, p.getPacketID());
            // TODO i.putExtra(EXTRA_STAMP, date);

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

}

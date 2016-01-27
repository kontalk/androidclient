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

package org.kontalk.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.service.msgcenter.MessageCenterService;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Display;
import android.view.WindowManager;


/**
 * Access to application preferences.
 * @author Daniele Ricci
 */
public final class Preferences {

    private static SharedPreferences sPreferences;
    private static Drawable sCustomBackground;
    private static String sBalloonTheme;

    public static void init(Context context) {
        sPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        // set the new default theme if this is the first upgrade
        String newTheme = context.getString(R.string.pref_default_balloons);
        if (!getBooleanOnce(context, "has_new_theme." + newTheme))
            sPreferences.edit().putString("pref_balloons", newTheme)
                .commit();
    }

    public static void setCachedCustomBackground(Drawable customBackground) {
        sCustomBackground = customBackground;
    }

    public static void setCachedBalloonTheme(String balloonTheme) {
        sBalloonTheme = balloonTheme;
    }

    public static void updateServerListLastUpdate(Preference pref, ServerList list) {
        Context context = pref.getContext();
        String timestamp = MessageUtils.formatTimeStampString(context, list.getDate().getTime(), true);
        pref.setSummary(context.getString(R.string.server_list_last_update, timestamp));
    }

    private static String getString(Context context, String key, String defaultValue) {
        return sPreferences.getString(key, defaultValue);
    }

    private static int getInt(Context context, String key, int defaultValue) {
        return sPreferences.getInt(key, defaultValue);
    }

    private static int getIntMinValue(Context context, String key, int minValue, int defaultValue) {
        String val = getString(context, key, null);
        int nval;
        try {
            nval = Integer.valueOf(val);
        }
        catch (Exception e) {
            nval = defaultValue;
        }
        return (nval < minValue) ? minValue : nval;
    }

    private static long getLong(Context context, String key, long defaultValue) {
        return sPreferences.getLong(key, defaultValue);
    }

    /** Retrieves a long and if >= 0 it sets it to -1. */
    private static long getLongOnce(Context context, String key) {
        long value = sPreferences.getLong(key, -1);
        if (value >= 0)
            sPreferences.edit().putLong(key, -1).commit();
        return value;
    }

    private static boolean getBoolean(Context context, String key, boolean defaultValue) {
        return sPreferences.getBoolean(key, defaultValue);
    }

    /** Retrieve a boolean and if false set it to true. */
    private static boolean getBooleanOnce(Context context, String key) {
        boolean value = sPreferences.getBoolean(key, false);
        if (!value)
            sPreferences.edit().putBoolean(key, true).commit();
        return value;
    }

    public static boolean setRingtone(Context context, String uri) {
        return sPreferences.edit()
            .putString("pref_ringtone", uri)
            .commit();
    }

    public static String getServerURI(Context context) {
        return getString(context, "pref_network_uri", null);
    }

    public static boolean setServerURI(Context context, String serverURI) {
        return sPreferences.edit()
            .putString("pref_network_uri", serverURI)
            .commit();
    }

    /** Returns a random server from the cached list or the user-defined server. */
    public static EndpointServer getEndpointServer(Context context) {
        String customUri = getServerURI(context);
        if (!TextUtils.isEmpty(customUri)) {
            try {
                return new EndpointServer(customUri);
            }
            catch (Exception e) {
                // custom is not valid - take one from list
            }
        }

        // return server stored in the default account
        return Authenticator.getDefaultServer(context);
    }

    /** Returns a server provider reflecting the current settings. */
    public static EndpointServer.EndpointServerProvider getEndpointServerProvider(Context context) {
        final String customUri = getServerURI(context);
        if (!TextUtils.isEmpty(customUri)) {
            return new EndpointServer.SingleServerProvider(customUri);
        }
        else {
            ServerList list = ServerListUpdater.getCurrentList(context);
            return new ServerList.ServerListProvider(list);
        }
    }

    public static boolean getEncryptionEnabled(Context context) {
        return getBoolean(context, "pref_encrypt", context
            .getResources().getBoolean(R.bool.pref_default_encrypt));
    }

    public static boolean getSyncSIMContacts(Context context) {
        return getBoolean(context, "pref_sync_sim_contacts", context
            .getResources().getBoolean(R.bool.pref_default_sync_sim_contacts));
    }

    public static boolean getSyncInvisibleContacts(Context context) {
        return getBoolean(context, "pref_sync_invisible_contacts", context
            .getResources().getBoolean(R.bool.pref_default_sync_invisible_contacts));
    }

    public static boolean getAutoAcceptSubscriptions(Context context) {
        return getBoolean(context, "pref_auto_accept_subscriptions", context
            .getResources().getBoolean(R.bool.pref_default_auto_accept_subscriptions));
    }

    public static boolean getPushNotificationsEnabled(Context context) {
        return getBoolean(context, "pref_push_notifications", context
            .getResources().getBoolean(R.bool.pref_default_push_notifications));
    }

    public static boolean getNotificationsEnabled(Context context) {
        return getBoolean(context, "pref_enable_notifications", context
            .getResources().getBoolean(R.bool.pref_default_enable_notifications));
    }

    public static String getNotificationVibrate(Context context) {
        return getString(context, "pref_vibrate", context
            .getString(R.string.pref_default_vibrate));
    }

    public static String getNotificationRingtone(Context context) {
        return getString(context, "pref_ringtone", context
            .getString(R.string.pref_default_ringtone));
    }

    public static boolean getNotificationLED(Context context) {
        return getBoolean(context, "pref_enable_notification_led",
            context.getResources().getBoolean(R.bool.pref_default_enable_notification_led));
    }

    public static int getNotificationLEDColor(Context context) {
        return getInt(context, "pref_notification_led_color",
            context.getResources().getInteger(R.integer.pref_default_notification_led_color));
    }

    public static boolean setNotificationLEDColor(Context context, int color) {
        return sPreferences.edit()
            .putInt("pref_notification_led_color", color)
            .commit();
    }

    public static int getImageCompression(Context context) {
        return Integer.parseInt(getString(context, "pref_image_resize", String
            .valueOf(context.getResources().getInteger(R.integer.pref_default_image_resize))));
    }

    public static boolean setLastCountryCode(Context context, int countryCode) {
        return sPreferences.edit()
            .putInt("pref_countrycode", countryCode)
            .commit();
    }

    public static int getLastCountryCode(Context context) {
        return getInt(context, "pref_countrycode", 0);
    }

    public static boolean getContactsListVisited(Context context) {
        return getBooleanOnce(context, "pref_contacts_visited");
    }

    public static long getLastSyncTimestamp(Context context) {
        return getLong(context, "pref_last_sync", -1);
    }

    public static boolean setLastSyncTimestamp(Context context, long timestamp) {
        return sPreferences.edit()
            .putLong("pref_last_sync", timestamp)
            .commit();
    }

    public static boolean setLastPushNotification(Context context, long timestamp) {
        return sPreferences.edit()
            .putLong("pref_last_push_notification", timestamp)
            .commit();
    }

    public static long getLastPushNotification(Context context) {
        return getLongOnce(context, "pref_last_push_notification");
    }

    /** TODO cache value */
    public static String getFontSize(Context context) {
        return getString(context, "pref_font_size", context
            .getString(R.string.pref_default_font_size));
    }

    public static String getBalloonTheme(Context context) {
        if (sBalloonTheme == null)
            sBalloonTheme = getString(context, "pref_balloons", context
                .getString(R.string.pref_default_balloons));
        return sBalloonTheme;
    }

    public static String getStatusMessage(Context context) {
        return getString(context, "pref_status_message", null);
    }

    public static boolean setStatusMessage(Context context, String message) {
        return sPreferences.edit()
            .putString("pref_status_message", message)
            .commit();
    }

    /** Loads and stores a cached version of the given conversation background. */
    public static File cacheConversationBackground(Context context, Uri uri) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = wm.getDefaultDisplay();
            int width;
            int height;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR2) {
                Point size = new Point();
                display.getSize(size);
                width = size.x;
                height = size.y;
            }
            else {
                width = display.getWidth();
                height = display.getHeight();
            }

            in = context.getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = MediaStorage.preloadBitmap(in, width, height);
            in.close();

            // open again
            in = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            in.close();

            Bitmap tn = ThumbnailUtils.extractThumbnail(bitmap, width, height);
            bitmap.recycle();

            // check for rotation data
            tn = MediaStorage.bitmapOrientation(context, uri, tn);

            File outFile = new File(context.getFilesDir(), "background.png");
            out = new FileOutputStream(outFile);
            tn.compress(Bitmap.CompressFormat.PNG, 90, out);
            tn.recycle();

            return outFile;
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // ignored
            }
            try {
                out.close();
            }
            catch (Exception e) {
                // ignored
            }
        }
    }

    public static Drawable getConversationBackground(Context context) {
        InputStream in = null;
        try {
            if (getBoolean(context, "pref_custom_background", false)) {
                if (sCustomBackground == null) {
                    String _customBg = getString(context, "pref_background_uri", null);
                    in = context.getContentResolver().openInputStream(Uri.parse(_customBg));

                    Bitmap bmap = BitmapFactory.decodeStream(in, null, null);
                    sCustomBackground = new BitmapDrawable(context.getResources(), bmap);
                }
                return sCustomBackground;
            }
        }
        catch (Exception e) {
            // ignored
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // ignored
            }
        }
        return null;
    }

    /**
     * Switches offline mode on or off.
     * @return offline mode status before the switch
     */
    public static boolean switchOfflineMode(Context context) {
        boolean old = sPreferences.getBoolean("offline_mode", false);
        // set flag again!
        boolean offline = !old;
        sPreferences.edit().putBoolean("offline_mode", offline).commit();

        if (offline) {
            // stop the message center and never start it again
            MessageCenterService.stop(context);
        }
        else {
            MessageCenterService.start(context);
        }

        return old;
    }

    /** Enable/disable offline mode. */
    public static void setOfflineMode(Context context, boolean enabled) {
        sPreferences.edit().putBoolean("offline_mode", enabled).commit();

        if (enabled) {
            // stop the message center and never start it again
            MessageCenterService.stop(context);
        }
        else {
            MessageCenterService.start(context);
        }
    }

    public static boolean getOfflineMode(Context context) {
        return getBoolean(context, "offline_mode", false);
    }

    public static boolean getOfflineModeUsed(Context context) {
        return getBoolean(context, "offline_mode_used", false);
    }

    public static boolean setOfflineModeUsed(Context context) {
        return sPreferences.edit()
            .putBoolean("offline_mode_used", true)
            .commit();
    }

    public static boolean getSendTyping(Context context) {
        return getBoolean(context, "pref_send_typing", context.getResources()
            .getBoolean(R.bool.pref_default_send_typing));
    }

    public static String getDialPrefix(Context context) {
        String pref = getString(context, "pref_remove_prefix", null);
        return (pref != null && !TextUtils.isEmpty(pref.trim())) ? pref: null;
    }

    public static String getPushSenderId(Context context) {
        return getString(context, "pref_push_sender", null);
    }

    public static boolean setPushSenderId(Context context, String senderId) {
        return sPreferences.edit()
            .putString("pref_push_sender", senderId)
            .commit();
    }

    public static boolean getAcceptAnyCertificate(Context context) {
        return getBoolean(context, "pref_accept_any_certificate", context.getResources()
            .getBoolean(R.bool.pref_default_accept_any_certificate));
    }

    public static int getIdleTimeMillis(Context context, int minValue) {
        return getIntMinValue(context, "pref_idle_time", minValue, context
            .getResources().getInteger(R.integer.pref_default_idle_time));
    }

    public static int getWakeupTimeMillis(Context context, int minValue) {
        return getIntMinValue(context, "pref_wakeup_time", minValue, context
            .getResources().getInteger(R.integer.pref_default_wakeup_time));
    }

    public static long getLastConnection(Context context) {
        return getLong(context, "pref_last_connection", -1);
    }

    // TODO why isn't this used?
    public static boolean setLastConnection(Context context) {
        return sPreferences.edit()
            .putLong("pref_last_connection", System.currentTimeMillis())
            .commit();
    }

    public static String getEnterKeyMode(Context context) {
        try {
            return getString(context, "pref_text_enter", context
                .getString(R.string.pref_default_text_enter));
        }
        catch (ClassCastException e) {
            // legacy mode
            return getBoolean(context, "pref_text_enter", false) ?
                "newline" : "default";
        }
    }

    public static boolean getShowBlockedUsers(Context context) {
        return getBoolean(context, "pref_show_blocked_users", context
            .getResources().getBoolean(R.bool.pref_default_show_blocked_users));
    }

    public static String getRosterVersion(Context context) {
        return getString(context, "roster_version", "");
    }

    public static boolean setRosterVersion(String version) {
        return sPreferences.edit()
            .putString("roster_version", version)
            .commit();
    }

    public static long getPingAlarmInterval(Context context, long defaultValue) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return (networkType != null) ?
            getLong(context, "ping_alarm_interval_" + networkType, defaultValue) :
            defaultValue;
    }

    public static boolean setPingAlarmInterval(Context context, long intervalMillis) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return networkType != null && sPreferences.edit()
            .putLong("ping_alarm_interval_" + networkType, intervalMillis)
            .commit();
    }

    public static long getPingAlarmBackoff(Context context, long defaultValue) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return (networkType != null) ?
            getLong(context, "ping_alarm_backoff_" + networkType, defaultValue) :
            defaultValue;
    }

    public static boolean setPingAlarmBackoff(Context context, long intervalMillis) {
        String networkType = SystemUtils.getCurrentNetworkName(context);
        return networkType != null && sPreferences.edit()
            .putLong("ping_alarm_backoff_" + networkType, intervalMillis)
            .commit();
    }

    /**
     * Saves the current registration progress data. Used for recoverying a
     * registration after a restart or in very low memory situations.
     */
    public static boolean saveRegistrationProgress(Context context, String name,
        String phoneNumber, PersonalKey key, String passphrase,
        byte[] importedPublicKey, byte[] importedPrivateKey, String serverUri,
        String sender, boolean force, Map<String, String> trustedKeys) {

        ByteArrayOutputStream trustedKeysOut = null;
        if (trustedKeys != null) {
            trustedKeysOut = new ByteArrayOutputStream();
            Properties prop = new Properties();
            prop.putAll(trustedKeys);
            try {
                prop.store(trustedKeysOut, null);
            }
            catch (IOException e) {
                // something went wrong
                // we can't have IOExceptions from byte buffers anyway
                trustedKeysOut = null;
            }
        }

        return sPreferences.edit()
            .putString("registration_name", name)
            .putString("registration_phone", phoneNumber)
            .putString("registration_key", key.toBase64())
            .putString("registration_importedpublickey", importedPublicKey != null ?
                Base64.encodeToString(importedPublicKey, Base64.NO_WRAP) : null)
            .putString("registration_importedprivatekey", importedPrivateKey != null ?
                Base64.encodeToString(importedPrivateKey, Base64.NO_WRAP) : null)
            .putString("registration_passphrase", passphrase)
            .putString("registration_server", serverUri)
            .putString("registration_sender", sender)
            .putBoolean("registration_force", force)
            .putString("registration_trustedkeys", trustedKeysOut != null ?
                Base64.encodeToString(trustedKeysOut.toByteArray(), Base64.NO_WRAP) : null)
            .commit();
    }

    @SuppressWarnings({"unchecked"})
    public static RegistrationProgress getRegistrationProgress(Context context) {
        String name = getString(context, "registration_name", null);
        if (name != null) {
            RegistrationProgress p = new RegistrationProgress();
            p.name = name;
            p.phone = getString(context, "registration_phone", null);
            String serverUri = getString(context, "registration_server", null);
            p.server = serverUri != null ? new EndpointServer(serverUri) : null;
            p.key = PersonalKey.fromBase64(getString(context, "registration_key", null));
            p.passphrase = getString(context, "registration_passphrase", null);

            String importedPublicKey = getString(context, "registration_importedpublickey", null);
            if (importedPublicKey != null)
                p.importedPublicKey = Base64.decode(importedPublicKey, Base64.NO_WRAP);
            String importedPrivateKey = getString(context, "registration_importedprivatekey", null);
            if (importedPrivateKey != null)
                p.importedPrivateKey = Base64.decode(importedPrivateKey, Base64.NO_WRAP);

            p.sender = getString(context, "registration_sender", null);
            p.force = getBoolean(context, "registration_force", false);

            String trustedKeys = getString(context, "registration_trustedkeys", null);
            if (trustedKeys != null) {
                ByteArrayInputStream trustedKeysProp =
                    new ByteArrayInputStream(Base64.decode(trustedKeys, Base64.NO_WRAP));
                try {
                    Properties prop = new Properties();
                    prop.load(trustedKeysProp);
                    p.trustedKeys = new HashMap<>((Map) prop);
                }
                catch (IOException ignored) {
                }
            }


            return p;
        }
        return null;
    }

    public static boolean clearRegistrationProgress(Context context) {
        return sPreferences.edit()
            .remove("registration_name")
            .remove("registration_phone")
            .remove("registration_key")
            .remove("registration_importedpublickey")
            .remove("registration_importedprivatekey")
            .remove("registration_passphrase")
            .remove("registration_server")
            .remove("registration_force")
            .remove("registration_trustedkeys")
            .commit();
    }

    public static final class RegistrationProgress {
        public String name;
        public String phone;
        public PersonalKey key;
        public String passphrase;
        public byte[] importedPublicKey;
        public byte[] importedPrivateKey;
        public EndpointServer server;
        public String sender;
        public boolean force;
        public Map<String, String> trustedKeys;
    }

    /** Recent statuses database helper. */
    private static final class RecentStatusDbHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "status.db";
        private static final int DATABASE_VERSION = 1;

        private static final String TABLE_STATUS = "status";
        private static final String SCHEMA_STATUS = "CREATE TABLE " + TABLE_STATUS + " (" +
            "_id INTEGER PRIMARY KEY," +
            "status TEXT UNIQUE," +
            "timestamp INTEGER" +
            ")";

        public RecentStatusDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_STATUS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // no upgrade for version 1
        }

        public Cursor query() {
            SQLiteDatabase db = getReadableDatabase();
            return db.query(TABLE_STATUS, new String[] { BaseColumns._ID, "status" },
                null, null, null, null, "timestamp DESC");
        }

        public void insert(String status) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues(2);
            v.put("status", status);
            v.put("timestamp", System.currentTimeMillis());
            db.replace(TABLE_STATUS, null, v);

            // delete old entries
            db.delete(TABLE_STATUS, "_id NOT IN (SELECT _id FROM " +
                TABLE_STATUS + " ORDER BY timestamp DESC LIMIT 10)", null);
        }
    }

    private static RecentStatusDbHelper recentStatusDb;

    private static void _recentStatusDbHelper(Context context) {
        if (recentStatusDb == null)
            recentStatusDb = new RecentStatusDbHelper(context.getApplicationContext());
    }

    /** Retrieves the list of recently used status messages. */
    public static Cursor getRecentStatusMessages(Context context) {
        _recentStatusDbHelper(context);
        return recentStatusDb.query();
    }

    public static void addRecentStatusMessage(Context context, String status) {
        _recentStatusDbHelper(context);
        recentStatusDb.insert(status);
        recentStatusDb.close();
    }

}

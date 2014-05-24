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

package org.kontalk.util;

import java.io.InputStream;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.ServerListUpdater;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;


/**
 * Access to application preferences.
 * @author Daniele Ricci
 */
public final class Preferences {

    private static Drawable sCustomBackground;
    private static String sBalloonTheme;

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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }

    private static int getInt(Context context, String key, int defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(key, defaultValue);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(key, defaultValue);
    }

    private static boolean getBoolean(Context context, String key, boolean defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(key, defaultValue);
    }

    /** Retrieve a boolean and if false set it to true. */
    private static boolean getBooleanOnce(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean value = prefs.getBoolean(key, false);
        if (!value)
            prefs.edit().putBoolean(key, true).commit();
        return value;
    }

    public static String getServerURI(Context context) {
        return getString(context, "pref_network_uri", null);
    }

    public static boolean setServerURI(Context context, String serverURI) {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    	return prefs.edit()
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

        ServerList list = ServerListUpdater.getCurrentList(context);
        return (list != null) ? list.random() : null;
    }

    public static boolean getEncryptionEnabled(Context context) {
        return getBoolean(context, "pref_encrypt", true);
    }

    public static boolean getSyncSIMContacts(Context context) {
        return getBoolean(context, "pref_sync_sim_contacts", false);
    }

    public static boolean getAutoAcceptSubscriptions(Context context) {
    	return getBoolean(context, "pref_auto_accept_subscriptions", false);
    }

    public static boolean getPushNotificationsEnabled(Context context) {
        return getBoolean(context, "pref_push_notifications", true);
    }

    public static boolean getNotificationsEnabled(Context context) {
        return getBoolean(context, "pref_enable_notifications", true);
    }

    public static String getNotificationVibrate(Context context) {
        return getString(context, "pref_vibrate", "never");
    }

    public static String getNotificationRingtone(Context context) {
        return getString(context, "pref_ringtone", null);
    }

    public static boolean setLastCountryCode(Context context, int countryCode) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
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
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putLong("pref_last_sync", timestamp)
            .commit();
    }

    /** TODO cache value */
    public static String getFontSize(Context context) {
        return getString(context, "pref_font_size", "medium");
    }

    public static int getBalloonResource(Context context, int direction) {
        if (sBalloonTheme == null)
            sBalloonTheme = getString(context, "pref_balloons", "classic");

        if ("iphone".equals(sBalloonTheme))
            return direction == Messages.DIRECTION_IN ?
                R.drawable.balloon_iphone_incoming :
                    R.drawable.balloon_iphone_outgoing;
        else if ("old_classic".equals(sBalloonTheme))
            return direction == Messages.DIRECTION_IN ?
                R.drawable.balloon_old_classic_incoming :
                    R.drawable.balloon_old_classic_outgoing;

        // all other cases
        return direction == Messages.DIRECTION_IN ?
            R.drawable.balloon_classic_incoming :
                R.drawable.balloon_classic_outgoing;
    }

    public static String getStatusMessage(Context context) {
        return getString(context, "pref_status_message", null);
    }

    public static boolean setStatusMessage(Context context, String message) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putString("pref_status_message", message)
            .commit();
    }

    public static Drawable getConversationBackground(Context context) {
        InputStream in = null;
        try {
            if (getBoolean(context, "pref_custom_background", false)) {
                if (sCustomBackground == null) {
                    String _customBg = getString(context, "pref_background_uri", null);
                    in = context.getContentResolver().openInputStream(Uri.parse(_customBg));

                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inSampleSize = 4;
                    Bitmap bmap = BitmapFactory.decodeStream(in, null, opt);
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

    public static boolean getBigUpgrade1(Context context) {
        return getBooleanOnce(context, "bigupgrade1");
    }

    /**
     * Switches offline mode on or off.
     * @return offline mode status before the switch
     */
    public static boolean switchOfflineMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean old = prefs.getBoolean("offline_mode", false);
        // set flag again!
        boolean offline = !old;
        prefs.edit().putBoolean("offline_mode", offline).commit();

        if (offline) {
            // stop the message center and never start it again
            MessageCenterService.stop(context);
        }
        else {
            MessageCenterService.start(context);
        }

        return old;
    }

    public static boolean getOfflineMode(Context context) {
        return getBoolean(context, "offline_mode", false);
    }

    public static boolean getOfflineModeUsed(Context context) {
        return getBoolean(context, "offline_mode_used", false);
    }

    public static boolean setOfflineModeUsed(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putBoolean("offline_mode_used", true)
            .commit();
    }

    public static boolean getSendTyping(Context context) {
        return getBoolean(context, "pref_send_typing", true);
    }

    public static String getDialPrefix(Context context) {
        String pref = getString(context, "pref_remove_prefix", null);
        return (pref != null && !TextUtils.isEmpty(pref.trim())) ? pref: null;
    }

    public static String getPushSenderId(Context context) {
        return getString(context, "pref_push_sender", null);
    }

    public static boolean setPushSenderId(Context context, String senderId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putString("pref_push_sender", senderId)
            .commit();
    }

    public static boolean getAcceptAnyCertificate(Context context) {
    	return getBoolean(context, "pref_accept_any_certificate", false);
    }

    public static int getIdleTimeMillis(Context context, int minValue, int defaultValue) {
        return getIntMinValue(context, "pref_idle_time", minValue, defaultValue);
    }

    public static int getWakeupTimeMillis(Context context, int minValue, int defaultValue) {
        return getIntMinValue(context, "pref_wakeup_time", minValue, defaultValue);
    }

    public static long getLastConnection(Context context) {
    	return getLong(context, "pref_last_connection", -1);
    }

    public static boolean setLastConnection(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putLong("pref_last_connection", System.currentTimeMillis())
            .commit();
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

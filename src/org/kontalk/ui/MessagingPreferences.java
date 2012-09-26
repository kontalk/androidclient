/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import java.io.InputStream;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.client.Protocol.UserStatusFlags;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PassKey;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.util.MessageUtils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


public final class MessagingPreferences extends PreferenceActivity {
    private static final String TAG = MessagingPreferences.class.getSimpleName();

    private static final int REQUEST_PICK_BACKGROUND = Activity.RESULT_FIRST_USER + 1;
    private static Drawable customBackground;
    private static String balloonTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        // push notifications checkbox
        final Preference pushNotifications = findPreference("pref_push_notifications");
        pushNotifications.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CheckBoxPreference pref = (CheckBoxPreference) preference;
                if (pref.isChecked())
                    MessageCenterService.enablePushNotifications(getApplicationContext());
                else
                    MessageCenterService.disablePushNotifications(getApplicationContext());

                return true;
            }
        });

        // message center restart
        final Preference restartMsgCenter = findPreference("pref_restart_msgcenter");
        restartMsgCenter.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.w(TAG, "manual message center restart requested");
                MessageCenterService.restartMessageCenter(getApplicationContext());
                Toast.makeText(MessagingPreferences.this, R.string.msg_msgcenter_restarted, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        // use custom background
        final Preference customBg = findPreference("pref_custom_background");
        customBg.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // discard reference to custom background drawable
                customBackground = null;
                return false;
            }
        });

        // set background
        final Preference setBackground = findPreference("pref_background_uri");
        setBackground.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQUEST_PICK_BACKGROUND);
                return true;
            }
        });

        //
        final Preference balloons = findPreference("pref_balloons");
        balloons.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                balloonTheme = (String) newValue;
                return true;
            }
        });

        // manual server address is handled in Application context

        // server list last update timestamp
        final Preference updateServerList = findPreference("pref_update_server_list");
        updateServerList.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final ServerListUpdater updater = new ServerListUpdater(MessagingPreferences.this);

                final ProgressDialog diag = new ProgressDialog(MessagingPreferences.this);
                diag.setCancelable(true);
                diag.setMessage(getString(R.string.serverlist_updating));
                diag.setIndeterminate(true);
                diag.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        updater.cancel();
                    }
                });

                updater.setListener(new ServerListUpdater.UpdaterListener() {
                    @Override
                    public void error(Throwable e) {
                        diag.cancel();
                        MessagingPreferences.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MessagingPreferences.this, R.string.serverlist_update_error,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void nodata() {
                        diag.cancel();
                        MessagingPreferences.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MessagingPreferences.this, R.string.serverlist_update_nodata,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void updated(final ServerList list) {
                        diag.dismiss();
                        MessagingPreferences.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateServerListLastUpdate(updateServerList, list);
                                // restart message center
                                MessageCenterService.restartMessageCenter(getApplicationContext());
                            }
                        });
                    }
                });

                diag.show();
                updater.start();
                return true;
            }
        });

        // update 'last update' string
        ServerList list = ServerListUpdater.getCurrentList(this);
        if (list != null)
            updateServerListLastUpdate(updateServerList, list);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_BACKGROUND) {
            if (resultCode == RESULT_OK) {
                // invalidate any previous reference
                customBackground = null;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit()
                    .putString("pref_background_uri", data.getDataString())
                    .commit();
            }
        }
        else
            super.onActivityResult(requestCode, resultCode, data);
    }

    private static void updateServerListLastUpdate(Preference pref, ServerList list) {
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

    private static String getServerURI(Context context) {
        return getString(context, "pref_network_uri", null);
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

    public static String getDefaultPassphrase(Context context) {
        return getString(context, "pref_passphrase", null);
    }

    /** Returns a {@link Coder} instance for encrypting contents. */
    public static Coder getEncryptCoder(String passphrase) {
        return new Coder(new PassKey(passphrase));
    }

    /** Returns a {@link Coder} instance for decrypting contents. */
    public static Coder getDecryptCoder(Context context, String myNumber) {
        String key = getDefaultPassphrase(context);
        if (key == null || key.length() == 0)
            key = myNumber;
        return new Coder(new PassKey(key));
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
        if (balloonTheme == null)
            balloonTheme = getString(context, "pref_balloons", "classic");

        if ("iphone".equals(balloonTheme))
            return direction == Messages.DIRECTION_IN ?
                R.drawable.balloon_iphone_incoming :
                    R.drawable.balloon_iphone_outgoing;

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
                if (customBackground == null) {
                    String _customBg = getString(context, "pref_background_uri", null);
                    in = context.getContentResolver().openInputStream(Uri.parse(_customBg));

                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inSampleSize = 4;
                    Bitmap bmap = BitmapFactory.decodeStream(in, null, opt);
                    customBackground = new BitmapDrawable(context.getResources(), bmap);
                }
                return customBackground;
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
            MessageCenterService.stopMessageCenter(context);
        }
        else {
            MessageCenterService.startMessageCenter(context);
        }

        return old;
    }

    public static boolean getOfflineMode(Context context) {
        return getBoolean(context, "offline_mode", false);
    }

    public static boolean getOfflineModeUsed(Context context) {
        return getBooleanOnce(context, "offline_mode_used");
    }

    /** Combines various settings into a group of UserStatus flags. */
    public static int getUserFlags(Context context) {
        int flags = 0;
        if (getBoolean(context, "pref_hide_presence", false))
            flags |= UserStatusFlags.FLAG_HIDE_PRESENCE_VALUE;

        // TODO other flags
        return flags;
    }

    public static void start(Activity context) {
        Intent intent = new Intent(context, MessagingPreferences.class);
        context.startActivityIfNeeded(intent, -1);
    }

}

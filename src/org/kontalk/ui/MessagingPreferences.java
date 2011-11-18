package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PassKey;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.util.MessageUtils;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;


public class MessagingPreferences extends PreferenceActivity {
    private static final String TAG = MessagingPreferences.class.getSimpleName();

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
                MessageCenterService.stopMessageCenter(getApplicationContext());
                MessageCenterService.startMessageCenter(getApplicationContext());
                return true;
            }
        });

        // manual server address
        // here we can't use OnPreferenceChangeListener because we need the
        // preference to be persisted.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if ("pref_network_uri".equals(key)) {
                    MessageCenterService.stopMessageCenter(getApplicationContext());
                    MessageCenterService.startMessageCenter(getApplicationContext());
                }
            }
        });

        // server list last update timestamp
        final Preference updateServerList = findPreference("pref_update_server_list");
        updateServerList.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.w(TAG, "updating server list");
                final ServerListUpdater updater = new ServerListUpdater(MessagingPreferences.this);

                final ProgressDialog diag = new ProgressDialog(MessagingPreferences.this);
                diag.setCancelable(true);
                diag.setMessage("Updating server list...");
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
                                Toast.makeText(MessagingPreferences.this, "Unable to download server list. Please retry later.",
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void nodata(Throwable e) {
                        diag.cancel();
                        MessagingPreferences.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MessagingPreferences.this, "No available server list found.",
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
                                MessageCenterService.stopMessageCenter(getApplicationContext());
                                MessageCenterService.startMessageCenter(getApplicationContext());
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

    private static void updateServerListLastUpdate(Preference pref, ServerList list) {
        Context context = pref.getContext();
        String timestamp = MessageUtils.formatTimeStampString(context, list.getDate().getTime(), true);
        pref.setSummary(context.getString(R.string.server_list_last_update, timestamp));
    }

    private static String getString(Context context, String key, String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }

    private static boolean getBoolean(Context context, String key, boolean defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(key, defaultValue);
    }

    private static String getServerURI(Context context) {
        return getString(context, "pref_network_uri", null);
    }

    /** Returns a random server from the cached list or the user-defined server. */
    public static EndpointServer getEndpointServer(Context context) {
        String customUri = getServerURI(context);
        if (!TextUtils.isEmpty(customUri))
            return new EndpointServer(customUri);

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

    public static boolean getLastSeenEnabled(Context context) {
        return getBoolean(context, "pref_show_last_seen", true);
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
}

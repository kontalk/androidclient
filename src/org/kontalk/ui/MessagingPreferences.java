package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PassKey;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.util.MessageUtils;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
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

        Preference restartMsgCenter = findPreference("pref_restart_msgcenter");
        restartMsgCenter.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.w(TAG, "manual message center restart requested");
                MessageCenterService.stopMessageCenter(getApplicationContext());
                MessageCenterService.startMessageCenter(getApplicationContext());
                return true;
            }
        });

        Preference markAllConfirmed = findPreference("pref_mark_all_confirmed");
        markAllConfirmed.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.w(TAG, "marking all incoming messages as confirmed");
                ContentValues values = new ContentValues(1);
                values.put(Messages.STATUS, Messages.STATUS_CONFIRMED);
                getContentResolver().update(Messages.CONTENT_URI, values,
                        Messages.DIRECTION + " = " + Messages.DIRECTION_IN + " AND " +
                        Messages.STATUS + " IS NULL",
                        null);

                Toast.makeText(MessagingPreferences.this, "Messages table updated!", Toast.LENGTH_SHORT)
                    .show();
                return true;
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
                            }
                        });
                    }
                });

                diag.show();
                updater.start();
                return true;
            }
        });

        ServerList list = ServerListUpdater.getCurrentList(this);
        if (list != null)
            updateServerListLastUpdate(updateServerList, list);
    }

    private static void updateServerListLastUpdate(Preference pref, ServerList list) {
        Context context = pref.getContext();
        String timestamp = MessageUtils.formatTimeStampString(context, list.getDate().getTime(), true);
        pref.setSummary(context.getString(R.string.server_list_last_update, timestamp));
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

    /** Returns true if the contacts list has already been checked against the server. */
    public static boolean getContactsChecked(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("pref_contacts_checked", false);
    }

    public static void setContactsChecked(Context context, boolean checked) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putBoolean("pref_contacts_checked", checked)
            .commit();
    }

    public static boolean getEncryptionEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("pref_encrypt", true);
    }

    private static String getString(Context context, String key, String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }

    public static String getDefaultPassphrase(Context context) {
        return getString(context, "pref_passphrase", null);
    }

    /** Returns a {@link Coder} instance for encrypting contents. */
    public static Coder getEncryptCoder(String passphrase) {
        /*String key = getString(context, "pref_passphrase", null);
        if (key == null || key.length() == 0)
            key = passphrase;
        if (key != null)
        */
        return new Coder(new PassKey(passphrase));
    }

    /** Returns a {@link Coder} instance for decrypting contents. */
    public static Coder getDecryptCoder(Context context, String myNumber) {
        String key = getString(context, "pref_passphrase", null);
        if (key == null || key.length() == 0)
            key = myNumber;
        return new Coder(new PassKey(key));
    }

    public static boolean getLastSeenEnabled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("pref_show_last_seen", false);
    }
}

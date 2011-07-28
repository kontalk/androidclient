package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PassKey;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.service.MessageCenterService;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
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

    }

    /** Default built-in server URI. */
    public static final String DEFAULT_SERVER_URI = "http://www.kontalk.org/messenger";

    public static String getServerURI(Context context) {
        return getString(context, "pref_network_uri", DEFAULT_SERVER_URI);
    }

    public static EndpointServer getEndpointServer(Context context) {
        return new EndpointServer(getServerURI(context));
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

    /** Returns a {@link Coder} instance for encrypting contents. */
    public static Coder getEncryptCoder(Context context, String passphrase) {
        String key = getString(context, "pref_passphrase", null);
        if (key == null || key.length() == 0)
            key = passphrase;
        if (key != null)
            return new Coder(new PassKey(key));
        return null;
    }

    /** Returns a {@link Coder} instance for decrypting contents. */
    public static Coder getDecryptCoder(Context context, String myNumber) {
        String key = getString(context, "pref_passphrase", null);
        if (key == null || key.length() == 0)
            key = myNumber;
        return new Coder(new PassKey(key));
    }
}

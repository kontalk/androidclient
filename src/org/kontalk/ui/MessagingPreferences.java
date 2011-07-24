package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.PassKey;
import org.kontalk.service.MessageCenterService;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;

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

    public static void setMyNumber(Context context, String number) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putString("pref_mynumber", number)
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
    public static Coder getCoder(Context context) {
        if (getEncryptionEnabled(context)) {
            String key = getString(context, "pref_passphrase", null);
            if (key == null || key.length() == 0)
                key = getString(context, "pref_mynumber", null);
            if (key != null)
                return new Coder(new PassKey(key));
        }
        return null;
    }
}

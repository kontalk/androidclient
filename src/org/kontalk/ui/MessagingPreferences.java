package org.kontalk.ui;

import org.kontalk.R;
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

    private static String getString(Context context, String key, String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }
}

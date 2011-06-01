package org.nuntius.ui;

import org.nuntius.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class MessagingPreferences extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }

    /** Default built-in server URI. */
    public static final String DEFAULT_SERVER_URI = "http://10.0.2.2/serverimpl1";

    public static String getServerURI(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("pref_network_uri", DEFAULT_SERVER_URI);
    }

    public static String getAuthToken(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("pref_auth_token", null);
    }

    public static void setAuthToken(Context context, String token) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putString("pref_auth_token", token)
            .commit();
    }
}

package org.kontalk;

import org.kontalk.service.MessageCenterService;
import org.kontalk.ui.MessagingNotification;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class Kontalk extends Application {
    private static final String TAG = Kontalk.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();

        MessageCenterService.startMessageCenter(this);
        MessagingNotification.updateMessagesNotification(this, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if ("pref_network_uri".equals(key)) {
                    // just restart the message center for now
                    Log.w(TAG, "network address changed");
                    MessageCenterService.stopMessageCenter(Kontalk.this);
                    MessageCenterService.startMessageCenter(Kontalk.this);
                }
            }
        });
    }
}

package org.nuntius;

import org.nuntius.service.MessageCenterService;
import org.nuntius.ui.MessagingNotification;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class Nuntius extends Application {

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
                    Log.w("NUNTIUS", "network address changed");
                    MessageCenterService.stopMessageCenter(Nuntius.this);
                    MessageCenterService.startMessageCenter(Nuntius.this);
                }
            }
        });
    }
}

package org.nuntius;

import org.nuntius.service.MessageCenterService;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.Log;

public class Nuntius extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if ("pref_network_uri".equals(key)) {
                    // just restart the message center for now
                    Log.w("NUNTIUS", "network address changed");
                    stopService(new Intent(Nuntius.this, MessageCenterService.class));
                    MessageCenterService.startMessageCenter(Nuntius.this);
                }
            }
        });
    }
}

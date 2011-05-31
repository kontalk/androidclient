package org.nuntius.ui;

import org.nuntius.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class MessagingPreferenceActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
    }
}

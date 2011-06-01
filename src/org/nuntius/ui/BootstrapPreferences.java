package org.nuntius.ui;

import org.nuntius.R;

import android.os.Bundle;
import android.preference.PreferenceActivity;


/**
 * Preference activity for some bootstrap preferences.
 * @author Daniele Ricci
 * @version 1.0
 */
public class BootstrapPreferences extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.bootstrap_preferences);
    }

}

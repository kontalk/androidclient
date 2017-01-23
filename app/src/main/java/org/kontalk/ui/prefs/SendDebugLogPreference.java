/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.ui.prefs;

import java.io.File;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.preference.Preference;
import android.util.AttributeSet;

import org.kontalk.Log;
import org.kontalk.R;


/**
 * Preference for sending the debug log.
 * @author Daniele Ricci
 */
public class SendDebugLogPreference extends Preference {

    public SendDebugLogPreference(Context context) {
        super(context);
        init();
    }

    public SendDebugLogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SendDebugLogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SendDebugLogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                sendDebugLog(getContext());
                return true;
            }
        });
    }

    void sendDebugLog(Context context) {
        File file = Log.getLogFile();
        if (file != null && file.isFile()) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_EMAIL, new String[] { context.getString(R.string.mailto) });
            i.putExtra(Intent.EXTRA_SUBJECT, "Kontalk debug log");
            i.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
            context.startActivity(i);
        }
    }

}

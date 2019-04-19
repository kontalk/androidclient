/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.util.SystemUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Preference for copying the messages database to the external storage.
 * @author Daniele Ricci
 */
public class CopyDatabasePreference extends Preference {

    public CopyDatabasePreference(Context context) {
        super(context);
        init();
    }

    public CopyDatabasePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CopyDatabasePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CopyDatabasePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                copyDatabase(getContext());
                return true;
            }
        });
    }

    void copyDatabase(Context context) {
        MessagesProvider.lockForImport(context);

        InputStream dbIn = null;
        OutputStream dbOut = null;
        try {
            File dbOutFile = new File(Environment.getExternalStorageDirectory(), "kontalk-messages.db");

            dbIn = new FileInputStream(MessagesProvider.getDatabaseUri(context));
            dbOut = new FileOutputStream(dbOutFile);
            SystemUtils.copy(dbIn, dbOut);
            Toast.makeText(context, context
                .getString(R.string.msg_copy_database_success, dbOutFile.toString()), Toast.LENGTH_LONG)
                .show();
        }
        catch (IOException e) {
            Toast.makeText(context, context
                .getString(R.string.msg_copy_database_failed, e.toString()), Toast.LENGTH_LONG)
                .show();
        }
        finally {
            SystemUtils.closeStream(dbIn);
            SystemUtils.closeStream(dbOut);
        }

        MessagesProvider.unlockForImport(context);
    }

}

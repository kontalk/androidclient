/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.util.DataUtils;
import org.kontalk.util.MediaStorage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;


/**
 * Preference for copying the messages database to the external storage.
 * @author Daniele Ricci
 */
public class CopyDatabasePreference extends Preference {
    private static final String TAG = Kontalk.TAG;

    public static final int REQUEST_COPY_DATABASE = Activity.RESULT_FIRST_USER + 4;

    private static final String DBFILE_MIME = "application/x-sqlite3";
    public static final String DBFILE_NAME = "kontalk-messages.db";

    private Fragment mFragment;

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
                requestFile(getContext());
                return true;
            }
        });
    }

    public void setParentFragment(Fragment fragment) {
        mFragment = fragment;
    }

    void requestFile(Context context) {
        try {
            if (MediaStorage.isStorageAccessFrameworkAvailable()) {
                MediaStorage.createFile(mFragment, DBFILE_MIME, DBFILE_NAME,
                    REQUEST_COPY_DATABASE);
                return;
            }
        }
        catch (ActivityNotFoundException e) {
            Log.w(TAG, "Storage Access Framework not working properly");
            ReportingManager.logException(e);
        }

        // also used as a fallback if SAF is not working properly
        Context ctx = getContext();
        if (ctx != null) {
            new FolderChooserDialog.Builder(ctx)
                .tag(getClass().getName())
                .initialPath(Environment.getExternalStorageDirectory().toString())
                .show(mFragment.getParentFragmentManager());
        }
    }

    public static void copyDatabase(Context context, File dbOutFile) {
        OutputStream dbOut = null;
        try {
            dbOut = new FileOutputStream(dbOutFile);
            copyDatabase(context, dbOut, dbOut.toString());
        }
        catch (IOException e) {
            Toast.makeText(context, context
                .getString(R.string.msg_copy_database_failed, e.toString()), Toast.LENGTH_LONG)
                .show();
        }
        finally {
            DataUtils.close(dbOut);
        }
    }

    public static void copyDatabase(Context context, Uri dbOutFile) {
        OutputStream dbOut = null;
        try {
            dbOut = context.getContentResolver().openOutputStream(dbOutFile);
            copyDatabase(context, dbOut, dbOutFile.toString());
        }
        catch (IOException e) {
            Toast.makeText(context, context
                .getString(R.string.msg_copy_database_failed, e.toString()), Toast.LENGTH_LONG)
                .show();
        }
        finally {
            DataUtils.close(dbOut);
        }
    }

    private static void copyDatabase(Context context, OutputStream dbOut, String filename) {
        MessagesProvider.lockForImport(context);

        InputStream dbIn = null;
        try {
            dbIn = new FileInputStream(MessagesProvider.getDatabaseUri(context));
            DataUtils.copy(dbIn, dbOut);
            Toast.makeText(context, context
                .getString(R.string.msg_copy_database_success, filename), Toast.LENGTH_LONG)
                .show();
        }
        catch (IOException e) {
            Toast.makeText(context, context
                .getString(R.string.msg_copy_database_failed, e.toString()), Toast.LENGTH_LONG)
                .show();
        }
        finally {
            DataUtils.close(dbIn);
            DataUtils.close(dbOut);
        }

        MessagesProvider.unlockForImport(context);
    }

}

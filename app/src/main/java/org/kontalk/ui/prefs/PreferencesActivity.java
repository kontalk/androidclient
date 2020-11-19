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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.crypto.PersonalKeyPack;


/**
 * Preferences activity.
 * TODO convert to fragments layout
 * @author Daniele Ricci
 */
public final class PreferencesActivity extends BasePreferencesActivity
        implements RootPreferenceFragment.Callback, FolderChooserDialog.FolderCallback {
    private static final String TAG_NESTED = "nested";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.preferences_screen);
        setupToolbar(true, false);

        if (savedInstanceState == null) {
            Fragment fragment = new PreferencesFragment();
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, fragment)
                .commit();
        }
    }

    /** Not used (manual implementation in fragments). */
    @Override
    protected boolean isNormalUpNavigation() {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return getSupportFragmentManager().findFragmentById(R.id.container)
                .onOptionsItemSelected(item);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // this if statement is necessary to navigate through nested and main fragments
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            super.onBackPressed();
        }
        else {
            getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onNestedPreferenceSelected(int key) {
        Fragment fragment;
        switch (key) {
            case R.xml.preferences_account:
                fragment = new AccountFragment();
                break;
            case R.xml.preferences_network:
                fragment = new NetworkFragment();
                break;
            case R.xml.preferences_network_push:
                fragment = new NetworkPushFragment();
                break;
            case R.xml.preferences_messaging:
                fragment = new MessagingFragment();
                break;
            case R.xml.privacy_preferences:
                fragment = new PrivacyPreferences();
                break;
            case R.xml.preferences_appearance:
                fragment = new AppearanceFragment();
                break;
            case R.xml.preferences_media:
                fragment = new MediaFragment();
                break;
            case R.xml.preferences_location:
                fragment = new LocationFragment();
                break;
            case R.xml.preferences_notification:
                fragment = new NotificationFragment();
                break;
            case R.xml.preferences_maintenance:
                fragment = new MaintenanceFragment();
                break;
            default:
                throw new IllegalArgumentException("unknown preference screen: " + key);
        }

        getSupportFragmentManager().beginTransaction()
            .replace(R.id.container, fragment, TAG_NESTED)
            .addToBackStack(TAG_NESTED).commit();
    }

    @Override
    public void onFolderSelection(@NonNull FolderChooserDialog folderChooserDialog, @NonNull File folder) {
        final String tag = folderChooserDialog.getTag();
        if (tag.equals(CopyDatabasePreference.class.getName())) {
            CopyDatabasePreference.copyDatabase(this,
                new File(folder, CopyDatabasePreference.DBFILE_NAME));
        }
        else if (tag.equals(AccountFragment.class.getName())) {
            try {
                AccountFragment f = (AccountFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.container);
                f.exportPersonalKey(this,
                    new FileOutputStream(new File(folder, PersonalKeyPack.KEYPACK_FILENAME)));
            }
            catch (FileNotFoundException e) {
                Log.e(PreferencesFragment.TAG, "error exporting keys", e);
                Toast.makeText(this,
                    R.string.err_keypair_export_write,
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onFolderChooserDismissed(@NonNull FolderChooserDialog dialog) {
    }

    public static void start(Activity context) {
        Intent intent = new Intent(context, PreferencesActivity.class);
        context.startActivityIfNeeded(intent, -1);
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;

import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyPack;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.PushServiceManager;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;


/**
 * PreferencesFragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public final class PreferencesFragment extends RootPreferenceFragment {
    static final String TAG = Kontalk.TAG;

    private static final int REQUEST_PICK_BACKGROUND = Activity.RESULT_FIRST_USER + 1;
    private static final int REQUEST_PICK_RINGTONE = Activity.RESULT_FIRST_USER + 2;
    private static final int REQUEST_CREATE_KEYPACK = Activity.RESULT_FIRST_USER + 3;

    // this is used after when exiting to SAF for exporting
    private String mPassphrase;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mPassphrase = savedInstanceState.getString("passphrase");
        }

        // upgrade from old version: pref_text_enter becomes string
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        try {
            prefs.getString("pref_text_enter", null);
        }
        catch (ClassCastException e) {
            // legacy mode
            prefs.edit()
                .putString("pref_text_enter",
                    prefs.getBoolean("pref_text_enter", false) ?
                        "newline" : "default")
                .commit();
        }

        addPreferencesFromResource(R.xml.preferences);

        // push notifications checkbox
        final Preference pushNotifications = findPreference("pref_push_notifications");
        pushNotifications.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context ctx = getActivity();
                CheckBoxPreference pref = (CheckBoxPreference) preference;
                if (pref.isChecked())
                    MessageCenterService.enablePushNotifications(ctx.getApplicationContext());
                else
                    MessageCenterService.disablePushNotifications(ctx.getApplicationContext());

                return true;
            }
        });

        // notification LED color
        final Preference notificationLed = findPreference("pref_notification_led_color");
        notificationLed.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Resources res = getResources();
                int[] ledColors = new int[]{
                    res.getColor(android.R.color.white), res.getColor(R.color.blue_light),
                    res.getColor(R.color.purple_light), res.getColor(R.color.green_light),
                    res.getColor(R.color.yellow_light), res.getColor(R.color.red_light),
                };

                new ColorChooserDialog.Builder((PreferencesActivity) getActivity(),
                    R.string.pref_notification_led_color)
                    .customColors(ledColors, null)
                    .preselect(Preferences.getNotificationLEDColor(getContext()))
                    .allowUserColorInput(false)
                    .dynamicButtonColor(false)
                    .show();
                return true;
            }
        });

        // message center restart
        final Preference restartMsgCenter = findPreference("pref_restart_msgcenter");
        restartMsgCenter.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.w(TAG, "manual message center restart requested");
                Context ctx = getActivity();
                MessageCenterService.restart(ctx.getApplicationContext());
                Toast.makeText(ctx, R.string.msg_msgcenter_restarted, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        // change passphrase
        final Preference changePassphrase = findPreference("pref_change_passphrase");
        changePassphrase.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (Authenticator.isUserPassphrase(getActivity())) {

                    OnPassphraseRequestListener action = new OnPassphraseRequestListener() {
                        public void onValidPassphrase(String passphrase) {
                            askNewPassphrase();
                        }

                        public void onInvalidPassphrase() {
                            new AlertDialogWrapper.Builder(getActivity())
                                .setMessage(R.string.err_password_invalid)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        }
                    };
                    askCurrentPassphrase(action);
                }

                else {
                    askNewPassphrase();
                }


                return true;
            }
        });

        // regenerate key pair
        final Preference regenKeyPair = findPreference("pref_regenerate_keypair");
        regenKeyPair.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialogWrapper.Builder(getActivity())
                    .setTitle(R.string.pref_regenerate_keypair)
                    .setMessage(R.string.pref_regenerate_keypair_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Context ctx = getActivity();
                            Toast.makeText(ctx, R.string.msg_generating_keypair,
                                Toast.LENGTH_LONG).show();

                            MessageCenterService.regenerateKeyPair(ctx.getApplicationContext());
                        }
                    })
                    .show();

                return true;
            }
        });

        // export key pair
        final Preference exportKeyPair = findPreference("pref_export_keypair");
        exportKeyPair.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                // TODO check for external storage presence

                final OnPassphraseChangedListener action = new OnPassphraseChangedListener() {
                    public void onPassphraseChanged(String passphrase) {
                        mPassphrase = passphrase;
                        if (MediaStorage.isStorageAccessFrameworkAvailable()) {
                            MediaStorage.createFile(PreferencesFragment.this,
                                PersonalKeyPack.KEYPACK_MIME,
                                PersonalKeyPack.KEYPACK_FILENAME,
                                REQUEST_CREATE_KEYPACK);
                        }
                        else {
                            PreferencesActivity ctx = (PreferencesActivity) getActivity();
                            if (ctx != null) {
                                new FolderChooserDialog.Builder(ctx)
                                    .initialPath(PersonalKeyPack.DEFAULT_KEYPACK.getParent())
                                    .show();
                            }
                        }
                    }
                };

                // passphrase was never set by the user
                // encrypt it with a user-defined passphrase first
                if (!Authenticator.isUserPassphrase(getActivity())) {
                    askNewPassphrase(action);
                }

                else {
                    OnPassphraseRequestListener action2 = new OnPassphraseRequestListener() {
                        public void onValidPassphrase(String passphrase) {
                            action.onPassphraseChanged(passphrase);
                        }

                        public void onInvalidPassphrase() {
                            new AlertDialogWrapper.Builder(getActivity())
                                .setMessage(R.string.err_password_invalid)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        }
                    };

                    askCurrentPassphrase(action2);
                }

                return true;
            }
        });

        // delete account
        final Preference deleteAccount = findPreference("pref_delete_account");
        deleteAccount.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialogWrapper.Builder(getActivity())
                    .setTitle(R.string.pref_delete_account)
                    .setMessage(R.string.msg_delete_account)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // progress dialog
                            final Dialog progress = new LockedDialog
                                .Builder(getActivity())
                                .content(R.string.msg_delete_account_progress)
                                .progress(true, 0)
                                .show();

                            // stop the message center first
                            Context ctx = getActivity();
                            MessageCenterService.stop(ctx.getApplicationContext());

                            AccountManagerCallback<Boolean> callback = new AccountManagerCallback<Boolean>() {
                                public void run(AccountManagerFuture<Boolean> future) {
                                    // dismiss progress
                                    progress.dismiss();
                                    // exit now
                                    getActivity().finish();
                                }
                            };
                            Authenticator.removeDefaultAccount(ctx, callback);
                        }
                    })
                    .show();

                return true;
            }
        });

        // use custom background
        final Preference customBg = findPreference("pref_custom_background");
        customBg.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // discard reference to custom background drawable
                Preferences.setCachedCustomBackground(null);
                return false;
            }
        });

        // set background
        final Preference setBackground = findPreference("pref_background_uri");
        setBackground.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQUEST_PICK_BACKGROUND);
                return true;
            }
        });

        // set balloon theme
        final Preference balloons = findPreference("pref_balloons");
        balloons.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Preferences.setCachedBalloonTheme((String) newValue);
                return true;
            }
        });

        // set ringtone
        final Preference setRingtone = findPreference("pref_ringtone");
        setRingtone.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(preference.getContext());

                String _currentRingtone = prefs.getString(preference.getKey(),
                    getString(R.string.pref_default_ringtone));
                Uri currentRingtone = !TextUtils.isEmpty(_currentRingtone) ? Uri.parse(_currentRingtone) : null;

                final Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentRingtone);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));

                i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
                i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, preference.getTitle());

                startActivityForResult(i, REQUEST_PICK_RINGTONE);
                return true;
            }
        });

        // manual server address is handled in Application context
        // we just handle validation here

        // server list last update timestamp
        final Preference updateServerList = findPreference("pref_update_server_list");
        updateServerList.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context ctx = getActivity();
                final ServerListUpdater updater = new ServerListUpdater(ctx);

                final MaterialDialog diag = new MaterialDialog.Builder(ctx)
                    .cancelable(true)
                    .content(R.string.serverlist_updating)
                    .progress(true, 0)
                    .cancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            updater.cancel();
                        }
                    })
                    .build();

                updater.setListener(new ServerListUpdater.UpdaterListener() {
                    @Override
                    public void error(Throwable e) {
                        diag.cancel();
                        message(R.string.serverlist_update_error);
                    }

                    @Override
                    public void networkNotAvailable() {
                        diag.cancel();
                        message(R.string.serverlist_update_nonetwork);
                    }

                    @Override
                    public void offlineModeEnabled() {
                        diag.cancel();
                        message(R.string.serverlist_update_offline);
                    }

                    @Override
                    public void noData() {
                        diag.cancel();
                        message(R.string.serverlist_update_nodata);
                    }

                    @Override
                    public void updated(final ServerList list) {
                        diag.dismiss();
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Preferences.updateServerListLastUpdate(updateServerList, list);
                                // restart message center
                                MessageCenterService.restart(getActivity().getApplicationContext());
                            }
                        });
                    }

                    private void message(final int textId) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getActivity(), textId,
                                    Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });

                diag.show();
                updater.start();
                return true;
            }
        });

        // update 'last update' string
        ServerList list = ServerListUpdater.getCurrentList(getActivity());
        if (list != null)
            Preferences.updateServerListLastUpdate(updateServerList, list);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Activity ctx = getActivity();
                ctx.finish();
                startActivity(new Intent(ctx, ConversationsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("passphrase", mPassphrase);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_BACKGROUND) {
            if (resultCode == Activity.RESULT_OK) {
                Context ctx = getActivity();
                if (ctx != null) {
                    // invalidate any previous reference
                    Preferences.setCachedCustomBackground(null);
                    // resize and cache image
                    // TODO do this in background (might take some time)
                    try {
                        File image = Preferences.cacheConversationBackground(ctx, data.getData());
                        // save to preferences
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
                        prefs.edit()
                            .putString("pref_background_uri", Uri.fromFile(image).toString())
                            .commit();
                    }
                    catch (Exception e) {
                        Toast.makeText(ctx, R.string.err_custom_background,
                            Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
        else if (requestCode == REQUEST_PICK_RINGTONE) {
            if (resultCode == Activity.RESULT_OK) {
                Context ctx = getActivity();
                if (ctx != null) {
                    Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    Preferences.setRingtone(ctx, uri != null ? uri.toString() : "");
                }
            }
        }
        else if (requestCode == REQUEST_CREATE_KEYPACK) {
            if (resultCode == Activity.RESULT_OK) {
                Context ctx = getActivity();
                if (ctx != null && data != null && data.getData() != null) {
                    try {
                        OutputStream out = ctx.getContentResolver().openOutputStream(data.getData());
                        exportPersonalKey(ctx, out);
                    }
                    catch (FileNotFoundException e) {
                        Log.e(TAG, "error exporting keys", e);
                        Toast.makeText(ctx, R.string.err_keypair_export_write,
                            Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private interface OnPassphraseChangedListener {
        void onPassphraseChanged(String passphrase);
    }

    private interface OnPassphraseRequestListener {
        void onValidPassphrase(String passphrase);

        void onInvalidPassphrase();
    }

    private void askCurrentPassphrase(final OnPassphraseRequestListener action) {
        new MaterialDialog.Builder(getActivity())
            .title(R.string.title_passphrase)
            .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .input(0, 0, true, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(MaterialDialog dialog, CharSequence input) {
                    String passphrase = input.toString();
                    // user-entered passphrase is hashed, so compare with SHA-1 version
                    String hashed = MessageUtils.sha1(passphrase);
                    if (hashed.equals(Kontalk.get(getActivity())
                        .getCachedPassphrase())) {
                        action.onValidPassphrase(passphrase);
                    }
                    else {
                        action.onInvalidPassphrase();
                    }
                }
            })
            .negativeText(android.R.string.cancel)
            .positiveText(android.R.string.ok)
            .show();
    }

    private void askNewPassphrase() {
        askNewPassphrase(null);
    }

    private void askNewPassphrase(final OnPassphraseChangedListener action) {
        new PasswordInputDialog.Builder(getActivity())
            .setMinLength(PersonalKey.MIN_PASSPHRASE_LENGTH)
            .title(R.string.pref_change_passphrase)
            .positiveText(android.R.string.ok, new PasswordInputDialog.OnPasswordInputListener() {
                public void onClick(DialogInterface dialog, int which, String password) {
                    Context ctx = getActivity();
                    String oldPassword = Kontalk.get(getActivity()).getCachedPassphrase();
                    try {

                        // user-entered passphrase must be hashed
                        String hashed = MessageUtils.sha1(password);
                        Authenticator.changePassphrase(ctx, oldPassword, hashed, true);
                        Kontalk.get(ctx).invalidatePersonalKey();

                        if (action != null)
                            action.onPassphraseChanged(password);
                    }
                    catch (Exception e) {
                        Toast.makeText(ctx,
                            R.string.err_change_passphrase, Toast.LENGTH_LONG)
                            .show();
                    }
                }
            })
            .negativeText(android.R.string.cancel)
            .show();
    }

    void exportPersonalKey(Context ctx, OutputStream out) {
        try {
            Kontalk.get(ctx).exportPersonalKey(out, mPassphrase);
            mPassphrase = null;

            Toast.makeText(ctx,
                R.string.msg_keypair_exported,
                Toast.LENGTH_LONG).show();
        }
        catch (Exception e) {
            Log.e(TAG, "error exporting keys", e);
            Toast.makeText(ctx, R.string.err_keypair_export_other,
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void setupPreferences() {
        super.setupPreferences();

        // disable push notifications if GCM is not available on the device
        if (!PushServiceManager.getInstance(getActivity()).isServiceAvailable()) {
            final CheckBoxPreference push = (CheckBoxPreference) findPreference("pref_push_notifications");
            push.setEnabled(false);
            push.setChecked(false);
            push.setSummary(R.string.pref_title_disabled_push_notifications);
        }

        final Preference manualServer = findPreference("pref_network_uri");
        manualServer.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String value = newValue.toString().trim();
                if (value.length() > 0 && !EndpointServer.validate(value)) {
                    new AlertDialogWrapper.Builder(getActivity())
                        .setTitle(R.string.pref_network_uri)
                        .setMessage(R.string.err_server_invalid_format)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                    return false;
                }
                return true;
            }
        });

    }

}

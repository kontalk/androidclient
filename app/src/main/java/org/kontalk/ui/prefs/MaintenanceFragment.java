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

import java.io.FileNotFoundException;
import java.io.OutputStream;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;

import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.text.InputType;
import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyPack;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.LockedDialog;
import org.kontalk.ui.PasswordInputDialog;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;


/**
 * Maintenance settings fragment.
 */
public class MaintenanceFragment extends RootPreferenceFragment {
    static final String TAG = Kontalk.TAG;

    private static final int REQUEST_CREATE_KEYPACK = Activity.RESULT_FIRST_USER + 3;

    // this is used after when exiting to SAF for exporting
    String mPassphrase;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mPassphrase = savedInstanceState.getString("passphrase");
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_maintenance);

        // message center restart
        final Preference restartMsgCenter = findPreference("pref_restart_msgcenter");
        restartMsgCenter.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
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
        changePassphrase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                if (Authenticator.isUserPassphrase(getActivity())) {

                    OnPassphraseRequestListener action = new OnPassphraseRequestListener() {
                        public void onValidPassphrase(String passphrase) {
                            askNewPassphrase();
                        }

                        public void onInvalidPassphrase() {
                            new MaterialDialog.Builder(getActivity())
                                .content(R.string.err_password_invalid)
                                .positiveText(android.R.string.ok)
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
        regenKeyPair.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new MaterialDialog.Builder(getActivity())
                        .title(R.string.pref_regenerate_keypair)
                        .content(R.string.pref_regenerate_keypair_confirm)
                        .negativeText(android.R.string.cancel)
                        .positiveText(android.R.string.ok)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                OnPassphraseChangedListener action = new OnPassphraseChangedListener() {
                                    @Override
                                    public void onPassphraseChanged(String passphrase) {
                                        Context ctx = getContext();
                                        Toast.makeText(ctx, R.string.msg_generating_keypair,
                                            Toast.LENGTH_LONG).show();

                                        MessageCenterService.regenerateKeyPair(ctx.getApplicationContext(), passphrase);
                                    }
                                };

                                if (Authenticator.isUserPassphrase(getActivity())) {
                                    // passphrase was set by the user before
                                    // ask for a new one
                                    askNewPassphrase(action);
                                }
                                else {
                                    action.onPassphraseChanged(null);
                                }
                            }
                        })
                        .show();

                return true;
            }
        });

        // export key pair
        final Preference exportKeyPair = findPreference("pref_export_keypair");
        exportKeyPair.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                // TODO check for external storage presence

                final OnPassphraseChangedListener action = new OnPassphraseChangedListener() {
                    public void onPassphraseChanged(String passphrase) {
                        mPassphrase = passphrase;
                        try {
                            if (MediaStorage.isStorageAccessFrameworkAvailable()) {
                                MediaStorage.createFile(MaintenanceFragment.this,
                                    PersonalKeyPack.KEYPACK_MIME,
                                    PersonalKeyPack.KEYPACK_FILENAME,
                                    REQUEST_CREATE_KEYPACK);
                                return;
                            }
                        }
                        catch (ActivityNotFoundException e) {
                            Log.w(TAG, "Storage Access Framework not working properly");
                            ReportingManager.logException(e);
                        }

                        // also used as a fallback if SAF is not working properly
                        PreferencesActivity ctx = (PreferencesActivity) getActivity();
                        if (ctx != null) {
                            new FolderChooserDialog.Builder(ctx)
                                    .initialPath(PersonalKeyPack.DEFAULT_KEYPACK.getParent())
                                    .show(getFragmentManager());
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
                            new MaterialDialog.Builder(getActivity())
                                .content(R.string.err_password_invalid)
                                .positiveText(android.R.string.ok)
                                .show();
                        }
                    };

                    askCurrentPassphrase(action2);
                }

                return true;
            }
        });

        // register device
        final Preference registerDevice = findPreference("pref_register_device");
        registerDevice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // TODO ask password?
                MessageCenterService.uploadPrivateKey(getContext(),
                    Kontalk.get(getContext()).getCachedPassphrase());
                return true;
            }
        });

        // delete account
        final Preference deleteAccount = findPreference("pref_delete_account");
        deleteAccount.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new MaterialDialog.Builder(getActivity())
                        .title(R.string.pref_delete_account)
                        .content(R.string.msg_delete_account)
                        .negativeText(android.R.string.cancel)
                        .positiveText(android.R.string.ok)
                        .positiveColorRes(R.color.button_danger)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
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
    }

    @Override
    public void onResume() {
        super.onResume();

        ((PreferencesActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_maintenance);
    }

    interface OnPassphraseChangedListener {
        void onPassphraseChanged(String passphrase);
    }

    interface OnPassphraseRequestListener {
        void onValidPassphrase(String passphrase);

        void onInvalidPassphrase();
    }

    void askCurrentPassphrase(final OnPassphraseRequestListener action) {
        new MaterialDialog.Builder(getActivity())
                .title(R.string.title_passphrase)
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                .input(0, 0, true, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
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

    void askNewPassphrase() {
        askNewPassphrase(null);
    }

    void askNewPassphrase(final OnPassphraseChangedListener action) {
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

    public void exportPersonalKey(Context ctx, OutputStream out) {
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("passphrase", mPassphrase);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CREATE_KEYPACK) {
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

}

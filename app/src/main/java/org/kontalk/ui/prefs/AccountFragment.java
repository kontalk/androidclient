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

import java.io.FileNotFoundException;
import java.io.OutputStream;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.folderselector.FolderChooserDialog;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.util.SHA1;

import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

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
import org.kontalk.service.msgcenter.event.ConnectedEvent;
import org.kontalk.service.msgcenter.event.PrivateKeyUploadedEvent;
import org.kontalk.service.msgcenter.event.UploadPrivateKeyRequest;
import org.kontalk.service.registration.RegistrationService;
import org.kontalk.ui.LockedDialog;
import org.kontalk.ui.PasswordInputDialog;
import org.kontalk.ui.RegisterDeviceActivity;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;


/**
 * Account settings fragment.
 */
public class AccountFragment extends RootPreferenceFragment {
    static final String TAG = Kontalk.TAG;

    private static final int REQUEST_CREATE_KEYPACK = Activity.RESULT_FIRST_USER + 3;

    // this is used after when exiting to SAF for exporting
    String mPassphrase;

    // created on demand
    MaterialDialog mUploadPrivateKeyProgress;

    private EventBus mServiceBus = MessageCenterService.bus();

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        if (savedInstanceState != null) {
            mPassphrase = savedInstanceState.getString("passphrase");
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_account);

        // load account information
        String displayName = Authenticator.getDefaultDisplayName(getContext());
        String phoneNumber = Authenticator.getDefaultAccountName(getContext());
        final Preference accountInfo = findPreference("pref_account_info");
        accountInfo.setTitle(displayName);
        accountInfo.setSummary(RegistrationService.formatForDisplay(phoneNumber));

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

                                        // FIXME should wait for ConnectedEvent
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
                                MediaStorage.createFile(AccountFragment.this,
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
                                    .show(getParentFragmentManager());
                        }
                    }
                };

                askOrSetPassphrase(action);

                return true;
            }
        });

        // register device
        final Preference registerDevice = findPreference("pref_register_device");
        registerDevice.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final OnPassphraseChangedListener action = new OnPassphraseChangedListener() {
                    public void onPassphraseChanged(String passphrase) {
                        uploadPrivateKey(passphrase);
                    }
                };

                askOrSetPassphrase(action);

                return true;
            }
        });

        // delete account
        // TODO should be a custom class and a big red glowing text :)
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
                .setTitle(R.string.pref_account_settings);
    }

    @Override
    public void onStop() {
        super.onStop();
        mServiceBus.unregister(this);
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
                        String hashed = SHA1.hex(passphrase);
                        if (hashed.equals(Kontalk.get()
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
                        String oldPassword = Kontalk.get().getCachedPassphrase();
                        try {

                            // user-entered passphrase must be hashed
                            String hashed = SHA1.hex(password);
                            Authenticator.changePassphrase(ctx, oldPassword, hashed, true);
                            Kontalk.get().invalidatePersonalKey();

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

    void askOrSetPassphrase(final OnPassphraseChangedListener action) {
        final Context context = getContext();

        // passphrase was never set by the user
        // encrypt it with a user-defined passphrase first
        if (!Authenticator.isUserPassphrase(context)) {
            askNewPassphrase(action);
        }

        else {
            OnPassphraseRequestListener action2 = new OnPassphraseRequestListener() {
                public void onValidPassphrase(String passphrase) {
                    action.onPassphraseChanged(passphrase);
                }

                public void onInvalidPassphrase() {
                    new MaterialDialog.Builder(context)
                        .content(R.string.err_password_invalid)
                        .positiveText(android.R.string.ok)
                        .show();
                }
            };

            askCurrentPassphrase(action2);
        }
    }

    void uploadPrivateKey(final String passphrase) {
        final Context context = getContext();
        if (context == null)
            return;

        // check for network
        if (!SystemUtils.isNetworkConnectionAvailable(context)) {
            Toast.makeText(getActivity(), R.string.register_device_nonetwork,
                Toast.LENGTH_LONG).show();
            return;
        }

        // check for offline mode
        if (Preferences.getOfflineMode()) {
            Toast.makeText(getActivity(), R.string.register_device_offline,
                Toast.LENGTH_LONG).show();
            return;
        }

        mPassphrase = passphrase;

        // listen for events to receive the token to display to the user
        mServiceBus.register(this);

        mUploadPrivateKeyProgress = new MaterialDialog.Builder(context)
            .progress(true, 0)
            .content(R.string.register_device_requesting)
            .cancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    mServiceBus.unregister(AccountFragment.this);
                    mUploadPrivateKeyProgress = null;
                }
            })
            .show();

        MessageCenterService.start(context.getApplicationContext());
    }

    // FIXME ConnectedEvent will also be used by key pair regeneration
    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    public void onConnected(ConnectedEvent event) {
        Context context = getContext();
        if (context != null) {
            mServiceBus.post(new UploadPrivateKeyRequest(mPassphrase));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onPrivateKeyUploaded(PrivateKeyUploadedEvent event) {
        mServiceBus.unregister(this);

        final Context context = getContext();
        if (context == null)
            return;

        if (mUploadPrivateKeyProgress != null) {
            mUploadPrivateKeyProgress.dismiss();
            mUploadPrivateKeyProgress = null;
        }

        if (event.token == null) {
            Toast.makeText(context, R.string.register_device_request_error, Toast.LENGTH_LONG).show();
        }
        else {
            RegisterDeviceActivity.start(context, event.token, event.server);
        }
    }

    public void exportPersonalKey(Context ctx, OutputStream out) {
        try {
            Kontalk.get().exportPersonalKey(out, mPassphrase);
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

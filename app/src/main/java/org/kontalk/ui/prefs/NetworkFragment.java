/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.service.msgcenter.PushServiceManager;
import org.kontalk.util.Preferences;

/**
 * Network settings fragment.
 */
public class NetworkFragment extends RootPreferenceFragment {

    private ServerListUpdater mServerlistUpdater;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_network);

        mHandler = new Handler();

        // push notifications checkbox
        final Preference pushNotifications = findPreference("pref_push_notifications");
        pushNotifications.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
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

        // manual server address is handled in Application context
        // we just handle validation here

        // server list last update timestamp
        final Preference updateServerList = findPreference("pref_update_server_list");
        updateServerList.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Context ctx = getActivity();
                mServerlistUpdater = new ServerListUpdater(ctx);

                final DialogHelperFragment diag = DialogHelperFragment
                        .newInstance(DialogHelperFragment.DIALOG_SERVERLIST_UPDATER);
                final Context appCtx = getContext().getApplicationContext();

                mServerlistUpdater.setListener(new ServerListUpdater.UpdaterListener() {
                    @Override
                    public void error(Throwable t) {
                        try {
                            ReportingManager.logException(t);
                            message(R.string.serverlist_update_error);
                            diag.dismiss();
                        }
                        catch (Exception e) {
                            // did our best
                        }
                    }

                    @Override
                    public void networkNotAvailable() {
                        try {
                            message(R.string.serverlist_update_nonetwork);
                            diag.dismiss();
                        }
                        catch (Exception e) {
                            // did our best
                        }
                    }

                    @Override
                    public void offlineModeEnabled() {
                        try {
                            message(R.string.serverlist_update_offline);
                            diag.dismiss();
                        }
                        catch (Exception e) {
                            // did our best
                        }
                    }

                    @Override
                    public void noData() {
                        try {
                            message(R.string.serverlist_update_nodata);
                            diag.dismiss();
                        }
                        catch (Exception e) {
                            // did our best
                        }
                    }

                    @Override
                    public void updated(final ServerList list) {
                        Preferences.updateServerListLastUpdate(updateServerList, list);
                        // restart message center
                        MessageCenterService.restart(appCtx);
                        try {
                            diag.dismiss();
                        }
                        catch (Exception e) {
                            // did our best
                        }
                    }

                    private void message(final int textId) {
                        try {
                            diag.getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity(), textId,
                                            Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        catch (Exception e) {
                            // did our best
                        }
                    }
                });

                diag.show(getFragmentManager(), DialogHelperFragment.class.getSimpleName());
                mServerlistUpdater.start();
                return true;
            }
        });

        // update 'last update' string
        ServerList list = ServerListUpdater.getCurrentList(getActivity());
        if (list != null)
            Preferences.updateServerListLastUpdate(updateServerList, list);
    }

    @Override
    public void onResume() {
        super.onResume();

        ((PreferencesActivity) getActivity()).getSupportActionBar()
                .setTitle(R.string.pref_network_settings);
    }

    @Override
    protected void setupPreferences() {
        // disable push notifications if GCM is not available on the device
        if (!PushServiceManager.getInstance(getActivity()).isServiceAvailable()) {
            final CheckBoxPreference push = (CheckBoxPreference) findPreference("pref_push_notifications");
            push.setEnabled(false);
            push.setChecked(false);
            push.setSummary(R.string.pref_title_disabled_push_notifications);
        }

        final Preference manualServer = findPreference("pref_network_uri");
        manualServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
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

    private void cancelServerlistUpdater() {
        if (mServerlistUpdater != null) {
            mServerlistUpdater.cancel();
            mServerlistUpdater = null;
        }
    }

    public static final class DialogHelperFragment extends DialogFragment {
        public static final int DIALOG_SERVERLIST_UPDATER = 1;

        public static DialogHelperFragment newInstance(int dialogId) {
            DialogHelperFragment f = new DialogHelperFragment();
            Bundle args = new Bundle();
            args.putInt("id", dialogId);
            f.setArguments(args);
            return f;
        }

        public DialogHelperFragment() {
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }

        @Override
        public void onDestroyView() {
            if (getDialog() != null && getRetainInstance())
                getDialog().setOnDismissListener(null);
            super.onDestroyView();
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            int id = args.getInt("id");

            switch (id) {
                case DIALOG_SERVERLIST_UPDATER:
                    return new MaterialDialog.Builder(getContext())
                            .cancelable(true)
                            .content(R.string.serverlist_updating)
                            .progress(true, 0)
                            .cancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    ((NetworkFragment) getTargetFragment())
                                            .cancelServerlistUpdater();
                                }
                            })
                            .build();
            }

            return super.onCreateDialog(savedInstanceState);
        }
    }

}

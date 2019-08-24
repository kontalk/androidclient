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

import com.afollestad.materialdialogs.MaterialDialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.Preferences;


/**
 * Network settings fragment.
 */
public class NetworkFragment extends RootPreferenceFragment {

    ServerListUpdater mServerlistUpdater;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences_network);

        // manual server address is handled in Application context
        // we just handle validation here
        final Preference manualServer = findPreference("pref_network_uri");
        manualServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String value = newValue.toString().trim();
                if (value.length() > 0 && !EndpointServer.validate(value)) {
                    new MaterialDialog.Builder(getActivity())
                        .title(R.string.pref_network_uri)
                        .content(R.string.err_server_invalid_format)
                        .positiveText(android.R.string.ok)
                        .show();
                    return false;
                }
                return true;
            }
        });

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

        final Preference pushNotifications = findPreference("pref_push_notifications_parent");
        PushNotificationsPreference.setState(pushNotifications);
    }

    @Override
    protected void setupPreferences() {
        setupPreferences("pref_push_notifications_parent", R.xml.preferences_network_push);
    }

    private void setupPreferences(String pref, final int xml) {
        final Preference preference = findPreference(pref);
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                invokeCallback(xml);
                return true;
            }
        });
    }

    void cancelServerlistUpdater() {
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

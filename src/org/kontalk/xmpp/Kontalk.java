/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.xmpp;

import org.kontalk.xmpp.authenticator.Authenticator;
import org.kontalk.xmpp.provider.MessagesProvider;
import org.kontalk.xmpp.service.DownloadService;
import org.kontalk.xmpp.service.MessageCenterService;
import org.kontalk.xmpp.service.NetworkStateReceiver;
import org.kontalk.xmpp.service.SystemBootStartup;
import org.kontalk.xmpp.sync.SyncAdapter;
import org.kontalk.xmpp.ui.ComposeMessage;
import org.kontalk.xmpp.ui.MessagingNotification;
import org.kontalk.xmpp.ui.SearchActivity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;


/**
 * The Application.
 * @author Daniele Ricci

 */
public class Kontalk extends Application {
    private static final String TAG = Kontalk.class.getSimpleName();

    /** Supported client protocol revision. */
    public static final int CLIENT_PROTOCOL = 4;

    private Handler mHandler;
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefChangedListener;

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();

        // update notifications from locally unread messages
        MessagingNotification.updateMessagesNotification(this, false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mPrefChangedListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                // no account - abort
                if (Authenticator.getDefaultAccount(Kontalk.this) == null)
                    return;

                // manual server address
                if ("pref_network_uri".equals(key)) {
                    // just restart the message center for now
                    android.util.Log.w(TAG, "network address changed");
                    MessageCenterService.restart(Kontalk.this);
                }

                // hide presence flag / encrypt user data flag
                else if ("pref_hide_presence".equals(key) || "pref_encrypt_userdata".equals(key)) {
                    MessageCenterService.updateStatus(Kontalk.this);
                }

                // changing remove prefix
                else if ("pref_remove_prefix".equals(key)) {
                    SyncAdapter.requestSync(getApplicationContext(), true);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(mPrefChangedListener);

        // TODO listen for changes to phone numbers

        // register account change listener
        Account account = Authenticator.getDefaultAccount(this);
        if (account != null) {
            final OnAccountsUpdateListener listener = new OnAccountsUpdateListener() {
                @Override
                public void onAccountsUpdated(Account[] accounts) {
                    Account my = null;
                    for (int i = 0; i < accounts.length; i++) {
                        if (accounts[i].type.equals(Authenticator.ACCOUNT_TYPE)) {
                            my = accounts[i];
                            break;
                        }
                    }

                    // account removed!!! Shutdown everything.
                    if (my == null) {
                        Log.w(TAG, "my account has been removed, shutting down");
                        // delete all messages
                        MessagesProvider.deleteDatabase(Kontalk.this);
                        MessageCenterService.stop(Kontalk.this);
                    }
                }
            };
            AccountManager.get(this).addOnAccountsUpdatedListener(listener, mHandler, true);
        }

        // enable/disable components
        setServicesEnabled(this, account != null);
    }

    /** Enable/disable application components when account is added or removed. */
    public static void setServicesEnabled(Context context, boolean enabled) {
        PackageManager pm = context.getPackageManager();
        enableService(context, pm, ComposeMessage.class, enabled);
        enableService(context, pm, SearchActivity.class, enabled);
        enableService(context, pm, MessageCenterService.class, enabled);
        enableService(context, pm, DownloadService.class, enabled);
        enableService(context, pm, SystemBootStartup.class, enabled);
        enableService(context, pm, NetworkStateReceiver.class, enabled);
    }

    private static void enableService(Context context, PackageManager pm, Class<?> klass, boolean enabled) {
        pm.setComponentEnabledSetting(new ComponentName(context, klass),
            enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }
}

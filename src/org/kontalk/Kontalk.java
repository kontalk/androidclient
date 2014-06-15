/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk;

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import org.apache.http.impl.conn.IdleConnectionHandler;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.service.DownloadService;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.NetworkStateReceiver;
import org.kontalk.service.SystemBootStartup;
import org.kontalk.service.UploadService;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.ui.SearchActivity;
import org.kontalk.util.Preferences;
import org.spongycastle.openpgp.PGPException;

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
    public static final String TAG = Kontalk.class.getSimpleName();

    /** Supported client protocol revision. */
    public static final int CLIENT_PROTOCOL = 4;

    private Handler mHandler;
    private SharedPreferences.OnSharedPreferenceChangeListener mPrefChangedListener;
    private PersonalKey mDefaultKey;

    /**
     * Passphrase to decrypt the personal private key.
     * This should be asked to the user and stored in memory - otherwise use
     * a dummy password if user doesn't want to remember it (or optionally do
     * not encrypt the private key).
     * For the moment, this is random-generated and stored as the account
     * password in Android Account Manager.
     */
    private String mKeyPassphrase;

    /**
     * Keep-alive reference counter.
     * This is used throughout the activities to keep track of application
     * usage. Please note that this is not to be confused with
     * {@link IdleConnectionHandler} reference counter, since this counter here
     * is used only by {@link NetworkStateReceiver} and a few others to check
     * if the Message Center should be started or not.<br>
     * Call {@link #hold} to increment the counter, {@link #release} to
     * decrement it.
     */
    private int mRefCounter;

    static {
        // register security provider
        PGP.registerProvider();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandler = new Handler();

        // init preferences
        Preferences.init(this);

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
                        // stop message center
                        MessageCenterService.stop(Kontalk.this);
                        // invalidate cached personal key
                        invalidatePersonalKey();
                    }
                }
            };

            AccountManager am = AccountManager.get(this);

            // register listener to handle account removal
            am.addOnAccountsUpdatedListener(listener, mHandler, true);
        }

        // enable/disable components
        setServicesEnabled(this, account != null);
    }

    public PersonalKey getPersonalKey() throws PGPException, IOException, CertificateException {
        try {
            if (mDefaultKey == null)
                mDefaultKey = Authenticator.loadDefaultPersonalKey(this, getCachedPassphrase());
        }
        catch (NoSuchProviderException e) {
            // this shouldn't happen, so crash the application
            throw new RuntimeException("no such crypto provider!?", e);
        }

        return mDefaultKey;
    }

    public void exportPersonalKey()
    		throws CertificateException, PGPException, IOException, NoSuchProviderException {

    	Authenticator.exportDefaultPersonalKey(this, getCachedPassphrase(), true);
    }

    /** Invalidates the cached personal key. */
    public void invalidatePersonalKey() {
        mDefaultKey = null;
    }

    private void ensureCachedPassphrase() {
    	if (mKeyPassphrase == null) {
            AccountManager am = AccountManager.get(this);
            Account account = Authenticator.getDefaultAccount(am);
            // cache passphrase from account
            mKeyPassphrase = am.getPassword(account);
    	}
    }

    public String getCachedPassphrase()  {
    	ensureCachedPassphrase();
        return mKeyPassphrase;
    }

    /** Enable/disable application components when account is added or removed. */
    public static void setServicesEnabled(Context context, boolean enabled) {
        PackageManager pm = context.getPackageManager();
        enableService(context, pm, ComposeMessage.class, enabled);
        enableService(context, pm, SearchActivity.class, enabled);
        enableService(context, pm, MessageCenterService.class, enabled);
        enableService(context, pm, DownloadService.class, enabled);
        enableService(context, pm, UploadService.class, enabled);
        enableService(context, pm, SystemBootStartup.class, enabled);
        enableService(context, pm, NetworkStateReceiver.class, enabled);
    }

    private static void enableService(Context context, PackageManager pm, Class<?> klass, boolean enabled) {
        pm.setComponentEnabledSetting(new ComponentName(context, klass),
            enabled ? PackageManager.COMPONENT_ENABLED_STATE_DEFAULT : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /** Increments the reference counter. */
    public void hold() {
        mRefCounter++;
    }

    /** Decrements the reference counter. */
    public void release() {
        if (mRefCounter > 0)
            mRefCounter--;
    }

    /** Returns true if the reference counter is greater than zero. */
    public boolean hasReference() {
        return mRefCounter > 0;
    }

}

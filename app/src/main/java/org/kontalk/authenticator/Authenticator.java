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

package org.kontalk.authenticator;

import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.openpgp.PGPException;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.NetworkErrorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.util.Base64;
import android.widget.Toast;

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyExporter;
import org.kontalk.provider.Keyring;
import org.kontalk.ui.MainActivity;
import org.kontalk.ui.NumberValidation;
import org.kontalk.util.DataUtils;


/**
 * The authenticator.
 * @author Daniele Ricci
 * @version 1.0
 */
public class Authenticator extends AbstractAccountAuthenticator {

    public static final String ACCOUNT_TYPE = BuildConfig.ACCOUNT_TYPE;
    public static final String DATA_PRIVATEKEY = "org.kontalk.key.private";
    public static final String DATA_PUBLICKEY = "org.kontalk.key.public";
    public static final String DATA_BRIDGECERT = "org.kontalk.key.bridgeCert";
    public static final String DATA_NAME = "org.kontalk.key.name";
    public static final String DATA_USER_PASSPHRASE = "org.kontalk.userPassphrase";
    public static final String DATA_SERVER_URI = "org.kontalk.server";
    public static final String DATA_SERVER_LIST = "org.kontalk.serverList";
    public static final String DATA_SERVICE_TERMS_URL = "org.kontalk.serviceTermsURL";

    @SuppressWarnings("WeakerAccess")
    final Context mContext;
    private final Handler mHandler;

    public Authenticator(Context context) {
        super(context);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    public static MyAccount getDefaultAccount(Context context) {
        AccountManager am = AccountManager.get(context);
        Account account = getDefaultSystemAccount(am);
        return account != null ? new MyAccount(account, am) : null;
    }

    @Nullable
    private static Account getDefaultSystemAccount(AccountManager m) {
        Account[] accs = m.getAccountsByType(ACCOUNT_TYPE);
        return (accs.length > 0) ? accs[0] : null;
    }

    public static MyAccount fromSystemAccount(Context context, Account systemAccount) {
        return new MyAccount(systemAccount, AccountManager.get(context));
    }

    static String getDisplayName(AccountManager am, Account account) {
        return account != null ? am.getUserData(account, DATA_NAME) : null;
    }

    static EndpointServer getServer(AccountManager am, Account account) {
        if (account != null) {
            String uri = am.getUserData(account, DATA_SERVER_URI);
            return uri != null ? new EndpointServer(uri) : null;
        }
        return null;
    }

    /** @deprecated Still used in {@link org.kontalk.Kontalk#onCreate()}. */
    @Deprecated
    public static String getDefaultServiceTermsURL(Context context) {
        AccountManager am = AccountManager.get(context);
        Account account = getDefaultSystemAccount(am);
        return account != null ? getServiceTermsURL(am, account) : null;
    }

    static String getServiceTermsURL(AccountManager am, Account account) {
        return am.getUserData(account, DATA_SERVICE_TERMS_URL);
    }

    /** @deprecated Still used in {@link org.kontalk.Kontalk#onCreate()}. */
    @Deprecated
    public static ServerList getDefaultServerList(Context context) {
        AccountManager am = AccountManager.get(context);
        Account account = getDefaultSystemAccount(am);
        return account != null ? getServerList(am, account) : null;
    }

    static ServerList getServerList(AccountManager am, Account account) {
        String serverListData = am.getUserData(account, DATA_SERVER_LIST);
        if (serverListData != null) {
            Properties props = DataUtils.unserializeProperties(serverListData);
            try {
                return ServerList.fromProperties(props);
            }
            catch (IOException e) {
                // this isn't right...
                return null;
            }
        }
        return null;
    }

    static PersonalKey loadPersonalKey(AccountManager am, Account account)
            throws PGPException, IOException, CertificateException {

        String passphrase = am.getPassword(account);
        String privKeyData = am.getUserData(account, DATA_PRIVATEKEY);
        String pubKeyData = am.getUserData(account, DATA_PUBLICKEY);
        String bridgeCertData = am.getUserData(account, DATA_BRIDGECERT);

        if (privKeyData != null && pubKeyData != null && bridgeCertData != null) {
            return PersonalKey
                .load(Base64.decode(privKeyData, Base64.DEFAULT),
                    Base64.decode(pubKeyData, Base64.DEFAULT),
                    passphrase,
                    Base64.decode(bridgeCertData, Base64.DEFAULT)
                );
        }
        else {
            return null;
        }
    }

    public static void exportDefaultPersonalKey(Context ctx, OutputStream dest, String exportPassphrase, boolean bridgeCertificate)
            throws CertificateException, PGPException,
                IOException, KeyStoreException, NoSuchAlgorithmException {

        AccountManager m = AccountManager.get(ctx);
        Account acc = getDefaultSystemAccount(m);
        String passphrase = m.getPassword(acc);

        String privKeyData = m.getUserData(acc, DATA_PRIVATEKEY);
        byte[] privateKey = Base64.decode(privKeyData, Base64.DEFAULT);
        byte[] bridgeCert = null;

        if (bridgeCertificate) {
            // bridge certificate is just plain data
            String bridgeCertData = m.getUserData(acc, DATA_BRIDGECERT);
            bridgeCert = Base64.decode(bridgeCertData, Base64.DEFAULT);
        }

        String pubKeyData = m.getUserData(acc, DATA_PUBLICKEY);
        byte[] publicKey = Base64.decode(pubKeyData, Base64.DEFAULT);

        // trusted keys
        Map<String, Keyring.TrustedFingerprint> trustedKeys = Keyring.getTrustedKeys(ctx);

        String displayName = m.getUserData(acc, DATA_NAME);

        PersonalKeyExporter exp = new PersonalKeyExporter();
        exp.save(privateKey, publicKey, dest, passphrase, exportPassphrase,
            bridgeCert, trustedKeys, acc.name, displayName);
    }

    public static byte[] getPrivateKeyExportData(Context ctx, String passphrase, String exportPassphrase)
            throws PGPException, IOException {
        AccountManager m = AccountManager.get(ctx);
        Account acc = getDefaultSystemAccount(m);

        return getPrivateKeyExportData(m, acc, passphrase, exportPassphrase);
    }

    private static byte[] getPrivateKeyExportData(AccountManager m, Account acc, String passphrase, String exportPassphrase)
            throws PGPException, IOException {
        String privKeyData = m.getUserData(acc, DATA_PRIVATEKEY);
        byte[] privateKey = Base64.decode(privKeyData, Base64.DEFAULT);

        // custom export passphrase -- re-encrypt private key
        if (exportPassphrase != null) {
            privateKey = PGP.copySecretKeyRingWithNewPassword(privateKey,
                    passphrase, exportPassphrase)
                    .getEncoded();
        }

        return privateKey;
    }

    public static void setDefaultPersonalKey(Context ctx, byte[] publicKeyData, byte[] privateKeyData,
            byte[] bridgeCertData, String passphrase) {
        AccountManager am = AccountManager.get(ctx);
        Account acc = getDefaultSystemAccount(am);

        // password is optional when updating just the public key
        if (passphrase != null)
            am.setPassword(acc, passphrase);

        // private key data is optional when updating just the public key
        if (privateKeyData != null)
            am.setUserData(acc, Authenticator.DATA_PRIVATEKEY, Base64.encodeToString(privateKeyData, Base64.NO_WRAP));

        am.setUserData(acc, Authenticator.DATA_PUBLICKEY, Base64.encodeToString(publicKeyData, Base64.NO_WRAP));
        am.setUserData(acc, Authenticator.DATA_BRIDGECERT, Base64.encodeToString(bridgeCertData, Base64.NO_WRAP));
    }

    /**
     * Set a new passphrase for the default account.
     * Please note that this method does not invalidate the cached key or passphrase.
     */
    public static void changePassphrase(Context ctx, String oldPassphrase, String newPassphrase, boolean fromUser)
            throws PGPException, IOException {

        AccountManager am = AccountManager.get(ctx);
        Account acc = getDefaultSystemAccount(am);

        // get old secret key ring
        String privKeyData = am.getUserData(acc, DATA_PRIVATEKEY);
        byte[] privateKeyData = Base64.decode(privKeyData, Base64.DEFAULT);

        byte[] newPrivateKeyData = PGP.changePassphrase(privateKeyData, oldPassphrase, newPassphrase);

        // replace key data in AccountManager
        am.setUserData(acc, DATA_PRIVATEKEY, Base64.encodeToString(newPrivateKeyData, Base64.NO_WRAP));
        am.setUserData(acc, DATA_USER_PASSPHRASE, String.valueOf(fromUser));

        // replace password for account
        am.setPassword(acc, newPassphrase);
    }

    public static void setPassphrase(Context ctx, String passphrase, boolean fromUser) {
        AccountManager am = AccountManager.get(ctx);
        Account acc = getDefaultSystemAccount(am);
        am.setUserData(acc, DATA_USER_PASSPHRASE, String.valueOf(fromUser));
        // replace password for account
        am.setPassword(acc, passphrase);
    }

    public static boolean isUserPassphrase(Context ctx) {
        AccountManager am = AccountManager.get(ctx);
        Account acc = getDefaultSystemAccount(am);
        return Boolean.parseBoolean(am.getUserData(acc, DATA_USER_PASSPHRASE));
    }

    public static void removeDefaultAccount(Context ctx, AccountManagerCallback<Boolean> callback) {
        AccountManager am = AccountManager.get(ctx);
        Account account = getDefaultSystemAccount(am);

        // there is something wrong with this, isn't it? [cit.]
        if (account == null)
            return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            final boolean result = am.removeAccountExplicitly(account);
            callback.run(new AccountManagerFuture<Boolean>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    return false;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public Boolean getResult() throws OperationCanceledException, IOException, AuthenticatorException {
                    return result;
                }

                @Override
                public Boolean getResult(long timeout, TimeUnit unit) throws OperationCanceledException, IOException, AuthenticatorException {
                    return result;
                }
            });
        }
        else {
            am.removeAccount(getDefaultSystemAccount(am), callback, null);
        }
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response,
            String accountType, String authTokenType,
            String[] requiredFeatures, Bundle options) throws NetworkErrorException {

        final Bundle bundle = new Bundle();

        if (getDefaultAccount(mContext) != null) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, R.string.only_one_account_supported,
                            Toast.LENGTH_LONG).show();
                }
            });
            bundle.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_CANCELED);
        }
        else {
            final Intent intent = new Intent(mContext, NumberValidation.class);
            intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
            bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        }

        return bundle;
    }

    /**
     * System is requesting to confirm our credentials - this usually means that
     * something has changed (e.g. new SIM card). We request the user to insert
     * his/her personal key passphrase - which might not have been set, in that
     * case that's unfortunate because it's an unrecoverable situation.
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response,
            Account account, Bundle options) throws NetworkErrorException {

        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT,
            MainActivity.passwordRequest(mContext));
        return bundle;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response,
            String accountType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response,
            Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {

        final Bundle bundle = new Bundle();
        bundle.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_UNSUPPORTED_OPERATION);
        bundle.putString(AccountManager.KEY_ERROR_MESSAGE, "This authenticator does not support authentication tokens.");
        return bundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response,
            Account account, String[] features) throws NetworkErrorException {
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response,
            Account account, String authTokenType, Bundle options)
            throws NetworkErrorException {
        throw new UnsupportedOperationException();
    }

}

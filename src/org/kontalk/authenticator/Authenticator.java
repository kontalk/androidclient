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

package org.kontalk.authenticator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;

import org.kontalk.R;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.ui.NumberValidation;
import org.spongycastle.openpgp.PGPException;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;


/**
 * The authenticator.
 * @author Daniele Ricci
 * @version 1.0
 */
public class Authenticator extends AbstractAccountAuthenticator {
    private static final String TAG = Authenticator.class.getSimpleName();

    public static final String ACCOUNT_TYPE = "org.kontalk.account";
    public static final String DATA_PRIVATEKEY = "org.kontalk.key.private";
    public static final String DATA_PUBLICKEY = "org.kontalk.key.public";
    public static final String DATA_BRIDGECERT = "org.kontalk.key.bridgeCert";
    public static final String DATA_NAME = "org.kontalk.key.name";

    /** @deprecated This was obviously deprecated from the beginning. */
    @Deprecated
    public static final String DATA_AUTHTOKEN = "org.kontalk.token";

    public static final String PUBLIC_KEY_FILENAME = "kontalk-public.pgp";
    public static final String PRIVATE_KEY_FILENAME = "kontalk-private.pgp";
    public static final String BRIDGE_CERT_FILENAME = "kontalk-login.crt";
    public static final String BRIDGE_KEY_FILENAME = "kontalk-login.key";

    private final Context mContext;
    private final Handler mHandler;

    public Authenticator(Context context) {
        super(context);
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    public static Account getDefaultAccount(Context ctx) {
        return getDefaultAccount(AccountManager.get(ctx));
    }

    public static Account getDefaultAccount(AccountManager m) {
        Account[] accs = m.getAccountsByType(ACCOUNT_TYPE);
        return (accs.length > 0) ? accs[0] : null;
    }

    public static String getDefaultAccountName(Context ctx) {
        Account acc = getDefaultAccount(ctx);
        return (acc != null) ? acc.name : null;
    }

    public static boolean hasPersonalKey(AccountManager am, Account account) {
        return am.getUserData(account, DATA_PRIVATEKEY) != null &&
            am.getUserData(account, DATA_PUBLICKEY) != null &&
            am.getUserData(account, DATA_BRIDGECERT) != null;
    }

    public static PersonalKey loadDefaultPersonalKey(Context ctx, String passphrase)
            throws PGPException, IOException, CertificateException, NoSuchProviderException {
        AccountManager m = AccountManager.get(ctx);
        Account acc = getDefaultAccount(m);

        String privKeyData = m.getUserData(acc, DATA_PRIVATEKEY);
        String pubKeyData = m.getUserData(acc, DATA_PUBLICKEY);
        String bridgeCertData = m.getUserData(acc, DATA_BRIDGECERT);

        if (privKeyData != null && pubKeyData != null && bridgeCertData != null)
	        return PersonalKey
	            .load(Base64.decode(privKeyData, Base64.DEFAULT),
	                  Base64.decode(pubKeyData, Base64.DEFAULT),
	                  passphrase,
	                  Base64.decode(bridgeCertData, Base64.DEFAULT)
	            );

        else
        	return null;
    }

    public static void exportDefaultPersonalKey(Context ctx, String passphrase, boolean bridgeCertificate)
    		throws CertificateException, NoSuchProviderException, PGPException, IOException {

    	AccountManager m = AccountManager.get(ctx);
	    Account acc = getDefaultAccount(m);

        String privKeyData = m.getUserData(acc, DATA_PRIVATEKEY);
        String pubKeyData = m.getUserData(acc, DATA_PUBLICKEY);

        File path = Environment.getExternalStorageDirectory();
        FileOutputStream out;

        byte[] publicKey = Base64.decode(pubKeyData, Base64.DEFAULT);
        byte[] privateKey = Base64.decode(privKeyData, Base64.DEFAULT);

        if (bridgeCertificate) {
        	// bridge certificate is just plain data
            String bridgeCertData = m.getUserData(acc, DATA_BRIDGECERT);
            byte[] bridgeCert = Base64.decode(bridgeCertData, Base64.DEFAULT);

            // export bridge certificate
            out = new FileOutputStream(new File(path, BRIDGE_CERT_FILENAME));
            out.write(bridgeCert);
            out.close();

            // export bridge private key
        	PrivateKey bridgeKey = PGP.convertPrivateKey(privateKey, passphrase);
            out = new FileOutputStream(new File(path, BRIDGE_KEY_FILENAME));
            out.write(bridgeKey.getEncoded());
            out.close();
        }

        // export public key
        out = new FileOutputStream(new File(path, PUBLIC_KEY_FILENAME));
        out.write(publicKey);
        out.close();

        // export private key
        out = new FileOutputStream(new File(path, PRIVATE_KEY_FILENAME));
        out.write(privateKey);
        out.close();
    }

    public static void setDefaultPersonalKey(Context ctx, byte[] publicKeyData, byte[] privateKeyData, byte[] bridgeCertData) {
        AccountManager am = AccountManager.get(ctx);
        Account acc = getDefaultAccount(am);

        // private key data is optional when updating just the public key
        if (privateKeyData != null)
        	am.setUserData(acc, Authenticator.DATA_PRIVATEKEY, Base64.encodeToString(privateKeyData, Base64.NO_WRAP));

        am.setUserData(acc, Authenticator.DATA_PUBLICKEY, Base64.encodeToString(publicKeyData, Base64.NO_WRAP));
        am.setUserData(acc, Authenticator.DATA_BRIDGECERT, Base64.encodeToString(bridgeCertData, Base64.NO_WRAP));
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
     * something has changed (e.g. new SIM card), so we simply delete the
     * account for safety.
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response,
            Account account, Bundle options) throws NetworkErrorException {

        Log.v(TAG, "confirming credentials");
        // remove account
        AccountManager man = AccountManager.get(mContext);
        man.removeAccount(account, null, null);

        final Bundle bundle = new Bundle();
        bundle.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
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

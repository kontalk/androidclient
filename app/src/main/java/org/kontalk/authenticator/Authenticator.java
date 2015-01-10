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

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.spongycastle.bcpg.ArmoredOutputStream;
import org.spongycastle.bcpg.HashAlgorithmTags;
import org.spongycastle.openpgp.PGPEncryptedData;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.openpgp.PGPSecretKeyRing;
import org.spongycastle.openpgp.operator.KeyFingerPrintCalculator;
import org.spongycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.spongycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.spongycastle.openpgp.operator.PGPDigestCalculator;
import org.spongycastle.openpgp.operator.PGPDigestCalculatorProvider;
import org.spongycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.spongycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.spongycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder;
import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemWriter;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.Toast;

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyImporter;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.ui.NumberValidation;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.XMPPUtils;

import static org.kontalk.crypto.PersonalKeyImporter.BRIDGE_CERTPACK_FILENAME;
import static org.kontalk.crypto.PersonalKeyImporter.BRIDGE_CERT_FILENAME;
import static org.kontalk.crypto.PersonalKeyImporter.BRIDGE_KEY_FILENAME;
import static org.kontalk.crypto.PersonalKeyImporter.PRIVATE_KEY_FILENAME;
import static org.kontalk.crypto.PersonalKeyImporter.PUBLIC_KEY_FILENAME;


/**
 * The authenticator.
 * @author Daniele Ricci
 * @version 1.0
 */
public class Authenticator extends AbstractAccountAuthenticator {

    public static final String ACCOUNT_TYPE = BuildConfig.ACCOUNT_TYPE;
    public static final String ACCOUNT_TYPE_LEGACY = "org.kontalk.legacy.account";
    public static final String DATA_PRIVATEKEY = "org.kontalk.key.private";
    public static final String DATA_PUBLICKEY = "org.kontalk.key.public";
    public static final String DATA_BRIDGECERT = "org.kontalk.key.bridgeCert";
    public static final String DATA_NAME = "org.kontalk.key.name";
    public static final String DATA_USER_PASSPHRASE = "org.kontalk.userPassphrase";
    public static final String DATA_SERVER_URI = "org.kontalk.server";

    /** @deprecated This was obviously deprecated from the beginning. */
    @Deprecated
    public static final String DATA_AUTHTOKEN = "org.kontalk.token";

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

    public static boolean isSelfJID(Context ctx, String bareJid) {
        String name = getDefaultAccountName(ctx);
        return name != null &&
            XMPPUtils.createLocalJID(ctx, MessageUtils.sha1(name))
                .equalsIgnoreCase(bareJid);
    }

    public static String getDefaultDisplayName(Context context) {
        AccountManager am = AccountManager.get(context);
        Account account = getDefaultAccount(am);
        return getDisplayName(am, account);
    }

    public static String getDisplayName(AccountManager am, Account account) {
        return account != null ? am.getUserData(account, DATA_NAME) : null;
    }

    public static EndpointServer getDefaultServer(Context context) {
        AccountManager am = AccountManager.get(context);
        Account account = getDefaultAccount(am);
        return getServer(am, account);
    }

    public static EndpointServer getServer(AccountManager am, Account account) {
        if (account != null) {
            String uri = am.getUserData(account, DATA_SERVER_URI);
            return uri != null ? new EndpointServer(uri) : null;
        }
        return null;
    }

    public static boolean hasPersonalKey(AccountManager am, Account account) {
        return account != null &&
            am.getUserData(account, DATA_PRIVATEKEY) != null &&
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

    public static void exportDefaultPersonalKey(Context ctx, String passphrase, String exportPassphrase, boolean bridgeCertificate)
            throws CertificateException, NoSuchProviderException, PGPException,
                IOException, KeyStoreException, NoSuchAlgorithmException {

        // TODO move all this stuff to a PersonalKeyExporter

        // put everything in a zip file
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(PersonalKeyImporter.DEFAULT_KEYPACK));

        AccountManager m = AccountManager.get(ctx);
        Account acc = getDefaultAccount(m);

        String privKeyData = m.getUserData(acc, DATA_PRIVATEKEY);
        byte[] privateKey = Base64.decode(privKeyData, Base64.DEFAULT);

        // custom export passphrase -- re-encrypt private key
        if (exportPassphrase != null) {
            privateKey = PGP.copySecretKeyRingWithNewPassword(privateKey,
                passphrase, exportPassphrase)
                .getEncoded();
        }

        OutputStream out;
        ByteArrayOutputStream stream;

        if (bridgeCertificate) {
            // bridge certificate is just plain data
            String bridgeCertData = m.getUserData(acc, DATA_BRIDGECERT);
            byte[] bridgeCert = Base64.decode(bridgeCertData, Base64.DEFAULT);

            // export bridge certificate
            zip.putNextEntry(new ZipEntry(BRIDGE_CERT_FILENAME));
            stream = new ByteArrayOutputStream();
            PemWriter writer = new PemWriter(new OutputStreamWriter(stream));
            writer.writeObject(new PemObject(X509Bridge.PEM_TYPE_CERTIFICATE, bridgeCert));
            writer.close();
            stream.writeTo(zip);
            zip.closeEntry();

            // export bridge private key
            zip.putNextEntry(new ZipEntry(BRIDGE_KEY_FILENAME));
            PrivateKey bridgeKey = PGP.convertPrivateKey(privateKey, exportPassphrase);
            stream = new ByteArrayOutputStream();
            writer = new PemWriter(new OutputStreamWriter(stream));
            writer.writeObject(new PemObject(X509Bridge.PEM_TYPE_PRIVATE_KEY, bridgeKey.getEncoded()));
            writer.close();
            stream.writeTo(zip);
            zip.closeEntry();

            // certificate pack in PKCS#12
            zip.putNextEntry(new ZipEntry(BRIDGE_CERTPACK_FILENAME));
            X509Certificate certificate = X509Bridge.load(bridgeCert);
            KeyStore pkcs12 = X509Bridge.exportCertificate(certificate, bridgeKey);
            pkcs12.store(zip, exportPassphrase.toCharArray());
            zip.closeEntry();
        }

        String pubKeyData = m.getUserData(acc, DATA_PUBLICKEY);
        byte[] publicKey = Base64.decode(pubKeyData, Base64.DEFAULT);

        // export public key
        zip.putNextEntry(new ZipEntry(PUBLIC_KEY_FILENAME));
        stream = new ByteArrayOutputStream();
        out = new ArmoredOutputStream(stream);
        out.write(publicKey);
        out.close();
        stream.writeTo(zip);
        zip.closeEntry();

        // export private key
        zip.putNextEntry(new ZipEntry(PRIVATE_KEY_FILENAME));
        stream = new ByteArrayOutputStream();
        out = new ArmoredOutputStream(stream);
        out.write(privateKey);
        out.close();
        stream.writeTo(zip);
        zip.closeEntry();

        // finalize the zip file
        zip.close();
    }

    public static void setDefaultPersonalKey(Context ctx, byte[] publicKeyData, byte[] privateKeyData,
            byte[] bridgeCertData, String passphrase) {
        AccountManager am = AccountManager.get(ctx);
        Account acc = getDefaultAccount(am);

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
        Account acc = getDefaultAccount(am);

        // get old secret key ring
        String privKeyData = am.getUserData(acc, DATA_PRIVATEKEY);
        byte[] privateKeyData = Base64.decode(privKeyData, Base64.DEFAULT);
        KeyFingerPrintCalculator fpr = new BcKeyFingerprintCalculator();
        PGPSecretKeyRing oldSecRing = new PGPSecretKeyRing(privateKeyData, fpr);

        // old decryptor
        PGPDigestCalculatorProvider calcProv = new JcaPGPDigestCalculatorProviderBuilder().build();
        PBESecretKeyDecryptor oldDecryptor = new JcePBESecretKeyDecryptorBuilder(calcProv)
                .setProvider(PGP.PROVIDER)
                .build(oldPassphrase.toCharArray());

        // new encryptor
        PGPDigestCalculator sha1Calc = new JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1);
        PBESecretKeyEncryptor newEncryptor = new JcePBESecretKeyEncryptorBuilder(PGPEncryptedData.AES_256, sha1Calc)
                .setProvider(PGP.PROVIDER).build(newPassphrase.toCharArray());

        // create new secret key ring
        PGPSecretKeyRing newSecRing = PGPSecretKeyRing.copyWithNewPassword(oldSecRing, oldDecryptor, newEncryptor);

        // replace key data in AccountManager
        byte[] newPrivateKeyData = newSecRing.getEncoded();
        am.setUserData(acc, DATA_PRIVATEKEY, Base64.encodeToString(newPrivateKeyData, Base64.NO_WRAP));

        am.setUserData(acc, DATA_USER_PASSPHRASE, String.valueOf(fromUser));

        // replace password for account
        am.setPassword(acc, newPassphrase);
    }

    public static boolean isUserPassphrase(Context ctx) {
        AccountManager am = AccountManager.get(ctx);
        Account acc = getDefaultAccount(am);
        return Boolean.parseBoolean(am.getUserData(acc, DATA_USER_PASSPHRASE));
    }

    public static void removeDefaultAccount(Context ctx, AccountManagerCallback<Boolean> callback) {
        AccountManager am = AccountManager.get(ctx);
        am.removeAccount(getDefaultAccount(am), callback, null);
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

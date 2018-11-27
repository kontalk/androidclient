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

package org.kontalk.service.registration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.util.Map;
import java.util.zip.ZipInputStream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smackx.iqregister.packet.Registration;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPException;
import org.spongycastle.operator.OperatorCreationException;

import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;

import org.kontalk.BuildConfig;
import org.kontalk.Log;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.Account;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.SmackInitializer;
import org.kontalk.crypto.PGPUidMismatchException;
import org.kontalk.crypto.PGPUserID;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyImporter;
import org.kontalk.provider.Keyring;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.XMPPConnectionHelper;
import org.kontalk.service.msgcenter.SQLiteRosterStore;
import org.kontalk.service.registration.event.AcceptTermsRequest;
import org.kontalk.service.registration.event.AccountCreatedEvent;
import org.kontalk.service.registration.event.ImportKeyError;
import org.kontalk.service.registration.event.ImportKeyRequest;
import org.kontalk.service.registration.event.KeyReceivedEvent;
import org.kontalk.service.registration.event.LoginTestEvent;
import org.kontalk.service.registration.event.PassphraseInputEvent;
import org.kontalk.service.registration.event.RetrieveKeyError;
import org.kontalk.service.registration.event.RetrieveKeyRequest;
import org.kontalk.service.registration.event.TermsAcceptedEvent;
import org.kontalk.service.registration.event.VerificationRequest;
import org.kontalk.util.EventBusIndex;
import org.kontalk.util.Preferences;
import org.kontalk.util.XMPPUtils;


/**
 * A service that manages the registration process. It takes care of:
 * <ul>
 * <li>Selecting a server from a provided list of servers</li>
 * <li>Requesting registration instructions and support to each one</li>
 * <li>Post events for UI reactions</li>
 * <li>Store and keep registration state even after application restarts</li>
 * <li>Create the account</li>
 * </ul>
 * @author Daniele Ricci
 */
public class RegistrationService extends Service implements XMPPConnectionHelper.ConnectionHelperListener {
    private static final String TAG = RegistrationService.class.getSimpleName();

    private static final EventBus BUS;

    static {
        BUS = EventBus.builder()
            // TODO .logger(...)
            .addIndex(new EventBusIndex())
            .throwSubscriberException(BuildConfig.DEBUG)
            .logNoSubscriberMessages(BuildConfig.DEBUG)
            .build();
    }

    /**
     * A list of possible states this service can be.
     * Maybe a state a machine here...?
     */
    public enum State {
        /** Doing nothing. */
        IDLE,
        /** Connecting to a server (no key). */
        CONNECTING,
        /** Waiting for a passphrase from UI. */
        WAITING_PASSPHRASE,
        /** Processing an imported key. */
        IMPORTING_KEY,
        /** Doing a login test with the key. */
        TESTING_KEY,
        /** Creating account. */
        CREATING_ACCOUNT,
    }

    /** Possible processes, that is, workflows for states. */
    public enum Workflow {
        REGISTRATION,
        IMPORT_KEY,
        RETRIEVE_KEY,
    }

    /** Posted as a sticky event on the bus. */
    public static final class CurrentState {
        public Workflow workflow;
        public State state;
        public EndpointServer server;
        public String phoneNumber;
        public String displayName;
        public String challenge;

        // these two are filled with imported key data if available
        // otherwise they will be filled with output of PersonalKey.store()
        public byte[] privateKey;
        public byte[] publicKey;
        public PersonalKey key;
        public String passphrase;

        public Map<String, Keyring.TrustedFingerprint> trustedKeys;

        CurrentState() {
            state = State.IDLE;
        }

        CurrentState(@NonNull CurrentState cs) {
            this.workflow = cs.workflow;
            this.state = cs.state;
            this.server = cs.server;
            this.phoneNumber = cs.phoneNumber;
            this.displayName = cs.displayName;
            this.challenge = cs.challenge;
            this.privateKey = cs.privateKey;
            this.publicKey = cs.publicKey;
            this.key = cs.key;
            this.passphrase = cs.passphrase;
            this.trustedKeys = cs.trustedKeys;
        }
    }

    public static final class NoPhoneNumberFoundException extends Exception {
    }

    private HandlerThread mServiceHandler;
    private Handler mInternalHandler;

    private XMPPConnectionHelper mConnector;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!BUS.isRegistered(this)) {
            BUS.register(this);
        }
        return START_STICKY;
    }

    private void configure() {
        SmackInitializer.initializeRegistration();
    }

    private void unconfigure() {
        SmackInitializer.deinitializeRegistration();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        configure();

        updateState(State.IDLE);

        // internal handler
        mServiceHandler = new HandlerThread(NumberValidator.class.getSimpleName()) {
            @Override
            protected void onLooperPrepared() {
                mInternalHandler = new Handler(getLooper());
            }
        };
        mServiceHandler.start();
    }

    @Override
    public void onDestroy() {
        BUS.unregister(this);
        unconfigure();

        final HandlerThread serviceHandler = mServiceHandler;
        mInternalHandler.post(new Runnable() {
            public void run() {
                try {
                    serviceHandler.quit();
                    reset();
                }
                catch (Exception e) {
                    // ignored
                }
            }
        });

        mInternalHandler = null;
        mServiceHandler = null;

        // TODO save state if we are still to do something
        // TODO Preferences.saveRegistrationProgress(...)
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private CurrentState updateState(State state) {
        return updateState(state, null, null);
    }

    private CurrentState updateState(State state, Workflow workflow) {
        return updateState(state, workflow, null);
    }

    private CurrentState updateState(State state, Workflow workflow, EndpointServer server) {
        CurrentState cstate = currentState();
        // always produce a new object
        if (cstate == null) {
            cstate = new CurrentState();
        }
        else {
            cstate = new CurrentState(cstate);
        }

        cstate.state = state;
        if (workflow != null) {
            cstate.workflow = workflow;
        }
        if (server != null) {
            cstate.server = server;
        }

        BUS.postSticky(cstate);
        return cstate;
    }

    /** Full registration procedure. */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onVerificationRequest(VerificationRequest request) {
        // begin by requesting instructions for service terms url
        reset();

        CurrentState cstate = updateState(State.CONNECTING, Workflow.REGISTRATION,
            request.serverProvider.next());
        cstate.phoneNumber = request.phoneNumber;
        cstate.displayName = request.displayName;

        /*
        // connect to the provided server
        mConnector = new XMPPConnectionHelper(this, request.server, true);
        mConnector.setRetryEnabled(false);

        try {
            initConnectionAnonymous();

            // send instructions form
            IQ form = createInstructionsForm();
            final XMPPConnection conn = mConnector.getConnection();
            conn.sendIqRequestAsync(form).onSuccess(new SuccessCallback<IQ>() {
                @Override
                public void onSuccess(IQ result) {
                    DataForm response = result.getExtension("x", "jabber:x:data");
                    if (response != null && response.hasField("accept-terms")) {
                        FormField termsUrlField = response.getField("terms");
                        if (termsUrlField != null) {
                            String termsUrl = termsUrlField.getFirstValue();
                            if (termsUrl != null) {
                                Log.d(TAG, "server request terms acceptance: " + termsUrl);
                                BUS.post(new AcceptTermsRequest(termsUrl));
                                return;
                            }
                        }
                    }

                    // no terms, just proceed
                    // TODO BUS.post(blabla...)
                }
            }).onError(new ExceptionCallback<Exception>() {
                @Override
                public void processException(Exception exception) {
                    // TODO properly parse errors from the server
                    BUS.post(new ImportKeyError(new IllegalStateException("Server did not reply with instructions.")));
                }
            });
        }
        catch (Exception e) {
            BUS.post(new RetrieveKeyError(e));
        }
        */
    }

    /** Import existing key from a personal keypack. */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onImportKeyRequest(ImportKeyRequest request) {
        // begin by requesting instructions for service terms url
        reset();

        CurrentState cstate = updateState(State.IMPORTING_KEY, Workflow.IMPORT_KEY);

        // start by validating the key
        PersonalKeyImporter importer;
        try {
            importer = createImporter(request.in, request.passphrase);
            importer.load();

            ByteArrayOutputStream privateKeyBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream publicKeyBuf = new ByteArrayOutputStream();

            cstate.key = importer.createPersonalKey(privateKeyBuf, publicKeyBuf);
            if (cstate.key == null)
                throw new PGPException("unable to load imported personal key.");

            Map<String, String> accountInfo = importer.getAccountInfo();
            if (accountInfo != null) {
                String phoneNumber = accountInfo.get("phoneNumber");
                if (!TextUtils.isEmpty(phoneNumber)) {
                    cstate.phoneNumber = phoneNumber;
                }
            }

            // personal key corrupted or too old
            if (cstate.phoneNumber == null) {
                throw new NoPhoneNumberFoundException();
            }

            PGPUserID uid = verifyImportedKey(cstate.key, cstate.phoneNumber);

            // use server from the key only if we didn't set our own
            cstate.server = (request.server != null) ? request.server :
                new EndpointServer(XmppStringUtils.parseDomain(uid.getEmail()));

            cstate.displayName = uid.getName();
            cstate.passphrase = request.passphrase;
            // copy over the parsed keys (imported keys may be armored)
            cstate.privateKey = privateKeyBuf.toByteArray();
            cstate.publicKey = publicKeyBuf.toByteArray();

            try {
                cstate.trustedKeys = importer.getTrustedKeys();
            }
            catch (Exception e) {
                // this is not a critical error so we can just ignore it
                Log.w(TAG, "unable to load trusted keys from key pack", e);
                ReportingManager.logException(e);
            }
        }
        catch (PGPUidMismatchException e) {
            Log.w(TAG, "uid mismatch!");
            BUS.post(new ImportKeyError(e));
            return;
        }
        catch (PGPException e) {
            // PGP specific error
            BUS.post(new ImportKeyError(e));
            return;
        }
        catch (GeneralSecurityException e) {
            BUS.post(new ImportKeyError(e));
            return;
        }
        catch (IOException e) {
            BUS.post(new ImportKeyError(e));
            return;
        }
        catch (OperatorCreationException e) {
            // serious device problem
            throw new RuntimeException(e);
        }
        catch (NoPhoneNumberFoundException e) {
            BUS.post(new ImportKeyError(e));
            return;
        }
        finally {
            try {
                request.in.close();
            }
            catch (IOException ignored) {
            }
        }

        updateState(State.CONNECTING);

        // connect to the provided server
        mConnector = new XMPPConnectionHelper(this, cstate.server, true);
        mConnector.setRetryEnabled(false);

        try {
            initConnectionAnonymous();

            // send instructions form
            IQ form = createInstructionsForm();
            final XMPPConnection conn = mConnector.getConnection();
            IQ result = conn.createStanzaCollectorAndSend(new StanzaIdFilter(form.getStanzaId()), form)
                .nextResultOrThrow();

            DataForm response = result.getExtension("x", "jabber:x:data");
            if (response != null && response.hasField("accept-terms")) {
                FormField termsUrlField = response.getField("terms");
                if (termsUrlField != null) {
                    String termsUrl = termsUrlField.getFirstValue();
                    if (termsUrl != null) {
                        Log.d(TAG, "server request terms acceptance: " + termsUrl);
                        BUS.post(new AcceptTermsRequest(termsUrl));
                        return;
                    }
                }
            }

            // no terms, just proceed
            loginTestWithImportedKey();
        }
        catch (Exception e) {
            BUS.post(new RetrieveKeyError(e));
        }
    }

    /**
     * Request a private key to the server.
     * Used when registering from another device.
     */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onRetrieveKeyRequest(RetrieveKeyRequest request) {
        reset();

        updateState(State.CONNECTING, Workflow.RETRIEVE_KEY, request.server);

        // connect to the provided server
        mConnector = new XMPPConnectionHelper(this, request.server, true);
        mConnector.setRetryEnabled(false);

        try {
            initConnectionAnonymous();

            // prepare private key request form
            IQ form = createPrivateKeyRequest(request.privateKeyToken);

            // send request packet
            final XMPPConnection conn = mConnector.getConnection();
            IQ result = conn.createStanzaCollectorAndSend(new StanzaIdFilter(form.getStanzaId()), form)
                .nextResultOrThrow();

            ExtensionElement _accountData = result.getExtension(Account.ELEMENT_NAME, Account.NAMESPACE);
            if (_accountData instanceof Account) {
                Account accountData = (Account) _accountData;

                byte[] privateKeyData = accountData.getPrivateKeyData();
                byte[] publicKeyData = accountData.getPublicKeyData();
                if (privateKeyData != null && privateKeyData.length > 0 &&
                    publicKeyData != null && publicKeyData.length > 0) {

                    CurrentState cstate = currentState();
                    cstate.privateKey = privateKeyData;
                    cstate.publicKey = publicKeyData;
                    cstate.phoneNumber = request.phoneNumber;

                    BUS.post(new KeyReceivedEvent(privateKeyData, publicKeyData));
                    updateState(State.WAITING_PASSPHRASE);
                    return;
                }
            }

            // unexpected response
            BUS.post(new RetrieveKeyError(new IllegalStateException("Server did not reply with key.")));
        }
        catch (Exception e) {
            BUS.post(new RetrieveKeyError(e));
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onKeyReceived(KeyReceivedEvent event) {
        disconnect();
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onRetrieveKeyError(RetrieveKeyError event) {
        reset();
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onPassphraseInput(PassphraseInputEvent event) {
        CurrentState cstate = currentState();
        cstate.passphrase = event.passphrase;
        switch (cstate.workflow) {
            case RETRIEVE_KEY: {
                if (cstate.state == State.WAITING_PASSPHRASE) {
                    // proceed with import
                    loadRetrievedKey();
                }
                break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onTermsAccepted(TermsAcceptedEvent event) {
        CurrentState cstate = currentState();
        switch (cstate.workflow) {
            case REGISTRATION: {
                // TODO
                break;
            }
            case IMPORT_KEY: {
                loginTestWithImportedKey();
                break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onLoginTest(LoginTestEvent event) {
        if (event.exception == null) {
            createAccount();
        }
    }

    private void loadRetrievedKey() {
        CurrentState cstate = updateState(State.IMPORTING_KEY);

        try {
            ByteArrayOutputStream privateKeyBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream publicKeyBuf = new ByteArrayOutputStream();
            cstate.key = PersonalKeyImporter.importPersonalKey(cstate.privateKey,
                cstate.publicKey, cstate.passphrase, privateKeyBuf, publicKeyBuf);
            PGPUserID uid = verifyImportedKey(cstate.key, cstate.phoneNumber);

            // use server from the key only if we didn't set our own
            cstate.server = (cstate.server != null) ? cstate.server :
                new EndpointServer(XmppStringUtils.parseDomain(uid.getEmail()));

            cstate.displayName = uid.getName();
            // copy over the parsed keys (it should be the same, but you never know...)
            cstate.privateKey = privateKeyBuf.toByteArray();
            cstate.publicKey = publicKeyBuf.toByteArray();

            loginTestWithImportedKey();
        }
        catch (PGPUidMismatchException e) {
            Log.w(TAG, "uid mismatch!");
            BUS.post(new RetrieveKeyError(e));
        }
        catch (PGPException e) {
            // PGP specific error
            BUS.post(new RetrieveKeyError(e));
        }
        catch (GeneralSecurityException e) {
            BUS.post(new RetrieveKeyError(e));
        }
        catch (IOException e) {
            BUS.post(new RetrieveKeyError(e));
        }
        catch (OperatorCreationException e) {
            // serious device problem
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private PGPUserID verifyImportedKey(PersonalKey key, String phoneNumber) throws PGPException {
        String uidStr = key.getUserId(null);
        PGPUserID uid = PGPUserID.parse(uidStr);
        if (uid == null)
            throw new PGPException("malformed user ID: " + uidStr);

        // check that uid matches phone number
        String email = uid.getEmail();
        String numberHash = XMPPUtils.createLocalpart(phoneNumber);
        String localpart = XmppStringUtils.parseLocalpart(email);
        if (!numberHash.equalsIgnoreCase(localpart))
            throw new PGPUidMismatchException("email does not match phone number: " + email);
        return uid;
    }

    private void createAccount() {
        CurrentState cstate = updateState(State.CREATING_ACCOUNT);

        // if we are retrieving a key from the server, delete the custom address
        if (cstate.workflow == Workflow.RETRIEVE_KEY) {
            Preferences.setServerURI(null);
        }

        final android.accounts.Account account = new android.accounts
            .Account(cstate.phoneNumber, Authenticator.ACCOUNT_TYPE);

        // workaround for bug in AccountManager (http://stackoverflow.com/a/11698139/1045199)
        // procedure will continue in removeAccount callback
        try {
            AccountManager.get(this).removeAccount(account,
                new AccountRemovalCallback(this, account, cstate.passphrase,
                    cstate.privateKey, cstate.publicKey, cstate.key.getBridgeCertificate().getEncoded(),
                    cstate.displayName, cstate.server.toString(), cstate.trustedKeys),
                null);
        }
        catch (CertificateEncodingException e) {
            // cannot happen
            throw new RuntimeException("unable to build X.509 bridge certificate", e);
        }
    }

    private PersonalKeyImporter createImporter(InputStream in, String passphrase) {
        return new PersonalKeyImporter(new ZipInputStream(in), passphrase);
    }

    /** Run a test login with the imported key. */
    private void loginTestWithImportedKey() {
        try {
            CurrentState cstate = updateState(State.TESTING_KEY);

            // connect to server
            initConnection(cstate.key, true);
            mConnector.shutdown();
            // login successful!!!
            BUS.post(new LoginTestEvent());
        }
        catch (Exception e) {
            BUS.post(new LoginTestEvent(e));
        }
    }

    private void initConnectionAnonymous() throws XMPPException, SmackException,
        PGPException, KeyStoreException, NoSuchProviderException,
        NoSuchAlgorithmException, CertificateException,
        IOException, InterruptedException {

        initConnection(null, false);
    }

    private void initConnection(PersonalKey key, boolean loginTest) throws XMPPException, SmackException,
        PGPException, KeyStoreException, NoSuchProviderException,
        NoSuchAlgorithmException, CertificateException,
        IOException, InterruptedException {

        if (!mConnector.isConnected() || mConnector.isServerDirty()) {
            // this will force the connector to recreate the connection
            mConnector.setServer(mConnector.getServer());
            mConnector.setListener(this);
            mConnector.connectOnce(key, loginTest);
        }
    }

    private IQ createInstructionsForm() {
        Registration iq = new Registration();
        iq.setTo(mConnector.getConnection().getXMPPServiceDomain());
        iq.setType(IQ.Type.get);
        return iq;
    }

    private IQ createPrivateKeyRequest(String privateKeyToken) {
        Registration iq = new Registration();
        iq.setType(IQ.Type.get);
        iq.setTo(mConnector.getConnection().getXMPPServiceDomain());

        Account account = new Account();
        account.setPrivateKeyToken(privateKeyToken);
        iq.addExtension(account);

        return iq;
    }

    void reset() {
        disconnect();
        BUS.removeStickyEvent(CurrentState.class);
        updateState(State.IDLE);
    }

    private void disconnect() {
        if (mConnector != null) {
            mConnector.shutdown();
        }
    }

    @Override
    public void created(XMPPConnection connection) {

    }

    @Override
    public void aborted(Exception e) {

    }

    @Override
    public void reconnectingIn(int seconds) {

    }

    @Override
    public void authenticationFailed() {

    }

    @Override
    public void connected(XMPPConnection connection) {

    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed) {

    }

    @Override
    public void connectionClosed() {

    }

    @Override
    public void connectionClosedOnError(Exception e) {

    }

    public static EventBus bus() {
        return BUS;
    }

    public static CurrentState currentState() {
        return BUS.getStickyEvent(CurrentState.class);
    }

    public static void start(Context context) {
        context.startService(new Intent(context, RegistrationService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RegistrationService.class));
    }

    private static class AccountRemovalCallback implements AccountManagerCallback<Boolean> {
        private Context context;
        private final android.accounts.Account account;
        private final String passphrase;
        private final byte[] privateKeyData;
        private final byte[] publicKeyData;
        private final byte[] bridgeCertData;
        private final String name;
        private final String serverUri;
        private final Map<String, Keyring.TrustedFingerprint> trustedKeys;

        AccountRemovalCallback(Context context, android.accounts.Account account,
            String passphrase, byte[] privateKeyData, byte[] publicKeyData,
            byte[] bridgeCertData, String name, String serverUri,
            Map<String, Keyring.TrustedFingerprint> trustedKeys) {
            this.context = context.getApplicationContext();
            this.account = account;
            this.passphrase = passphrase;
            this.privateKeyData = privateKeyData;
            this.publicKeyData = publicKeyData;
            this.bridgeCertData = bridgeCertData;
            this.name = name;
            this.serverUri = serverUri;
            this.trustedKeys = trustedKeys;
        }

        @Override
        public void run(AccountManagerFuture<Boolean> result) {
            // store trusted keys
            if (trustedKeys != null) {
                Keyring.setTrustedKeys(context, trustedKeys);
            }

            AccountManager am = (AccountManager) context
                .getSystemService(Context.ACCOUNT_SERVICE);

            // account userdata
            Bundle data = new Bundle();
            data.putString(Authenticator.DATA_PRIVATEKEY, Base64.encodeToString(privateKeyData, Base64.NO_WRAP));
            data.putString(Authenticator.DATA_PUBLICKEY, Base64.encodeToString(publicKeyData, Base64.NO_WRAP));
            data.putString(Authenticator.DATA_BRIDGECERT, Base64.encodeToString(bridgeCertData, Base64.NO_WRAP));
            data.putString(Authenticator.DATA_NAME, name);
            data.putString(Authenticator.DATA_SERVER_URI, serverUri);

            // this is the password to the private key
            am.addAccountExplicitly(account, passphrase, data);

            // put data once more (workaround for Android bug http://stackoverflow.com/a/11698139/1045199)
            am.setUserData(account, Authenticator.DATA_PRIVATEKEY, data.getString(Authenticator.DATA_PRIVATEKEY));
            am.setUserData(account, Authenticator.DATA_PUBLICKEY, data.getString(Authenticator.DATA_PUBLICKEY));
            am.setUserData(account, Authenticator.DATA_BRIDGECERT, data.getString(Authenticator.DATA_BRIDGECERT));
            am.setUserData(account, Authenticator.DATA_NAME, data.getString(Authenticator.DATA_NAME));
            am.setUserData(account, Authenticator.DATA_SERVER_URI, serverUri);

            // Set contacts sync for this account.
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);

            // clear old roster information
            SQLiteRosterStore.purge(context);

            BUS.post(new AccountCreatedEvent(account));
        }
    }

}

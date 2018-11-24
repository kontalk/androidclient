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

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.iqregister.packet.Registration;
import org.spongycastle.openpgp.PGPException;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.support.annotation.Nullable;

import org.kontalk.BuildConfig;
import org.kontalk.client.Account;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.SmackInitializer;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.service.XMPPConnectionHelper;
import org.kontalk.service.registration.event.ImportKeyRequest;
import org.kontalk.service.registration.event.KeyReceivedEvent;
import org.kontalk.service.registration.event.RetrieveKeyError;
import org.kontalk.service.registration.event.RetrieveKeyRequest;
import org.kontalk.service.registration.event.VerificationRequest;
import org.kontalk.util.EventBusIndex;


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
    private static final String TAG = RegistrationService.TAG;

    private static final EventBus BUS;

    static {
        BUS = EventBus.builder()
            // TODO .logger(...)
            .addIndex(new EventBusIndex())
            .throwSubscriberException(BuildConfig.DEBUG)
            .logNoSubscriberMessages(BuildConfig.DEBUG)
            .build();
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
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // TODO use events also for internal processing

    /** Full registration procedure. */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onVerificationRequest(VerificationRequest request) {
        // TODO begin by requesting instructions
    }

    /** Import existing key from a personal keypack. */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onImportKeyRequest(ImportKeyRequest request) {
        // TODO begin by requesting instructions for service terms url
    }

    /**
     * Request a private key to the server.
     * Used when registering from another device.
     */
    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onRetrieveKeyRequest(RetrieveKeyRequest request) {
        reset();
        // connect to the provided server
        mConnector = new XMPPConnectionHelper(this, request.server, true);
        mConnector.setRetryEnabled(false);

        try {
            initConnectionAnonymous();

            // prepare private key request form
            Stanza form = createPrivateKeyRequest(request.privateKeyToken);

            XMPPConnection conn = mConnector.getConnection();
            conn.addAsyncStanzaListener(new StanzaListener() {
                public void processStanza(Stanza packet) {
                    IQ iq = (IQ) packet;
                    if (iq.getType() == IQ.Type.result) {
                        ExtensionElement _accountData = iq.getExtension(Account.ELEMENT_NAME, Account.NAMESPACE);
                        if (_accountData instanceof Account) {
                            Account accountData = (Account) _accountData;

                            byte[] privateKeyData = accountData.getPrivateKeyData();
                            byte[] publicKeyData = accountData.getPublicKeyData();
                            if (privateKeyData != null && privateKeyData.length > 0 &&
                                publicKeyData != null && publicKeyData.length > 0) {

                                BUS.post(new KeyReceivedEvent(privateKeyData, publicKeyData));
                                return;
                            }
                        }
                    }

                    // TODO properly parse errors from the server
                    BUS.post(new RetrieveKeyError(new IllegalStateException("Server did not reply with key.")));
                }
            }, new StanzaIdFilter(form.getStanzaId()));

            // send request packet
            conn.sendStanza(form);
        }
        catch (Exception e) {
            BUS.post(new RetrieveKeyError(e));
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onKeyReceived(KeyReceivedEvent event) {
        reset();
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onRetrieveKeyError(RetrieveKeyError event) {
        reset();
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
            mConnector.setListener(this);
            mConnector.connectOnce(key, loginTest);
        }
    }

    private Stanza createPrivateKeyRequest(String privateKeyToken) {
        Registration iq = new Registration();
        iq.setType(IQ.Type.get);
        iq.setTo(mConnector.getConnection().getXMPPServiceDomain());

        Account account = new Account();
        account.setPrivateKeyToken(privateKeyToken);
        iq.addExtension(account);

        return iq;
    }

    void reset() {
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

    public static void start(Context context) {
        context.startService(new Intent(context, RegistrationService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, RegistrationService.class));
    }

}

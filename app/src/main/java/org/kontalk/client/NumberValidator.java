/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.client;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smackx.iqregister.packet.Registration;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.FormField;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.spongycastle.openpgp.PGPException;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.kontalk.crypto.PGP.PGPKeyPairRing;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.service.XMPPConnectionHelper;
import org.kontalk.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.service.msgcenter.PGPKeyPairRingProvider;
import org.kontalk.util.MessageUtils;


/**
 * A basic worker thread for doing number validation procedures.
 * It handles all the steps defined in phone number validation, from the
 * validation request to the received SMS and finally the authentication token
 * request.
 * @author Daniele Ricci
 * @version 1.0
 */
public class NumberValidator implements Runnable, ConnectionHelperListener {
    private static final String TAG = NumberValidator.class.getSimpleName();

    /** Initialization */
    public static final int STEP_INIT = 0;
    /** Validation step (sending phone number and waiting for SMS) */
    public static final int STEP_VALIDATION = 1;
    /** Requesting authentication token */
    public static final int STEP_AUTH_TOKEN = 2;
    /** Login test for imported key */
    public static final int STEP_LOGIN_TEST = 3;

    public static final int ERROR_THROTTLING = 1;
    public static final int ERROR_USER_EXISTS = 2;

    private final EndpointServer.EndpointServerProvider mServerProvider;
    private final String mName;
    private final String mPhone;
    private boolean mForce;
    private boolean mFallback;
    private PersonalKey mKey;
    private PGPKeyPairRing mKeyRing;
    private X509Certificate mBridgeCert;
    private String mPassphrase;
    private final Object mKeyLock = new Object();

    private byte[] mImportedPrivateKey;
    private byte[] mImportedPublicKey;

    private final XMPPConnectionHelper mConnector;
    private NumberValidatorListener mListener;
    private volatile int mStep;
    private CharSequence mValidationCode;

    private Thread mThread;

    private HandlerThread mServiceHandler;
    private Handler mInternalHandler;

    public NumberValidator(Context context, EndpointServer.EndpointServerProvider serverProvider,
        String name, String phone, PersonalKey key, String passphrase) {
        mServerProvider = serverProvider;
        mName = name;
        mPhone = phone;
        mKey = key;
        mPassphrase = passphrase;

        mConnector = new XMPPConnectionHelper(context.getApplicationContext(), mServerProvider.next(), true);
        mConnector.setRetryEnabled(false);

        configure();
    }

    private void configure() {
        SmackInitializer.initializeRegistration();
    }

    private void unconfigure() {
        SmackInitializer.deinitializeRegistration();
    }

    public void setKey(PersonalKey key) {
        synchronized (mKeyLock) {
            mKey = key;
            mKeyLock.notifyAll();
        }
    }

    public void setForce(boolean force) {
        mForce = force;
    }

    public void setFallback(boolean fallback) {
        mFallback = fallback;
    }

    @Override
    public PGPKeyPairRingProvider getKeyPairRingProvider() {
        // not supported
        return null;
    }

    public void importKey(byte[] privateKeyData, byte[] publicKeyData) {
        mImportedPrivateKey = privateKeyData;
        mImportedPublicKey = publicKeyData;
    }

    public EndpointServer getServer() {
        return mConnector.getServer();
    }

    public static boolean isMissedCall(String senderId) {
        // very quick way to check if we are using missed call based verification
        return senderId != null && senderId.endsWith("???");
    }

    public static int getChallengeLength(String senderId) {
        int count = 0;
        if (senderId != null) {
            for (int i = senderId.length()-1; i >= 0; i--) {
                if (senderId.charAt(i) == '?')
                    count++;
                else
                    break;
            }
        }
        return count;
    }

    public synchronized void start() {
        if (mThread != null) throw new IllegalArgumentException("already started");

        // internal handler
        mServiceHandler = new HandlerThread(NumberValidator.class.getSimpleName()) {
            @Override
            protected void onLooperPrepared() {
                mInternalHandler = new Handler(getLooper());
            }
        };
        mServiceHandler.start();

        // validator thread
        mThread = new Thread(this);
        mThread.start();
    }

    @Override
    public void run() {
        try {
            // begin!
            if (mStep == STEP_INIT) {
                synchronized (mKeyLock) {
                    if (mKey == null && (mImportedPrivateKey == null || mImportedPublicKey == null)) {
                        Log.v(TAG, "waiting for key generator");
                        try {
                            // wait endlessly?
                            mKeyLock.wait();
                        }
                        catch (InterruptedException e) {
                            mStep = STEP_INIT;
                            return;
                        }
                        Log.v(TAG, "key generation completed " + mKey);
                    }
                }

                // request number validation via sms
                mStep = STEP_VALIDATION;
                try {
                    initConnection();
                }
                catch (Exception e) {
                    EndpointServer server = mServerProvider.next();
                    if (server != null) {
                        Log.w(TAG, "connection error - trying next server in list", e);
                        // run again with new server
                        mStep = STEP_INIT;
                        mConnector.setServer(server);
                        run();
                        return;
                    }
                    else {
                        // last server to try, no chance for connection
                        throw e;
                    }
                }

                final AbstractXMPPConnection conn = mConnector.getConnection();

                Stanza form = createRegistrationForm();

                // setup listener for form response
                conn.addAsyncStanzaListener(new StanzaListener() {
                    public void processPacket(Stanza packet) {
                        int reason = 0;
                        IQ iq = (IQ) packet;

                        // whatever we received, close the connection now
                        conn.disconnect();

                        if (iq.getType() == IQ.Type.result) {
                            DataForm response = iq.getExtension("x", "jabber:x:data");
                            if (response != null) {
                                // ok! message will be sent
                                List<FormField> iter = response.getFields();
                                for (FormField field : iter) {
                                    if (field.getVariable().equals("from")) {
                                        String smsFrom = field.getValues().get(0);
                                        Log.d(TAG, "using sender id: " + smsFrom);
                                        mListener.onValidationRequested(NumberValidator.this, smsFrom);

                                        // prevent error handling
                                        return;
                                    }
                                }
                            }
                        }

                        else if (iq.getType() == IQ.Type.error) {
                            XMPPError error = iq.getError();

                            if (error.getCondition() == XMPPError.Condition.conflict) {
                                reason = ERROR_USER_EXISTS;

                            }

                            else if (error.getCondition() == XMPPError.Condition.service_unavailable) {

                                if (error.getType() == XMPPError.Type.WAIT) {
                                    reason = ERROR_THROTTLING;

                                }

                                else {
                                    // no registration support - try the next server

                                    EndpointServer server = mServerProvider.next();
                                    if (server != null) {
                                        // run again with new server
                                        mStep = STEP_INIT;
                                        mConnector.setServer(server);
                                        run();
                                        return;
                                    }
                                    else {
                                        // last server to try, no chance for registration
                                        mListener.onServerCheckFailed(NumberValidator.this);
                                        // onValidationFailed will not be called
                                        reason = -1;
                                    }
                                }
                            }

                        }

                        // validation failed :(
                        if (reason >= 0)
                            mListener.onValidationFailed(NumberValidator.this, reason);

                        mStep = STEP_INIT;
                    }
                }, new StanzaIdFilter(form.getStanzaId()));

                // send registration form
                conn.sendStanza(form);
            }

            // sms received, request authentication token
            else if (mStep == STEP_AUTH_TOKEN) {
                Log.d(TAG, "requesting authentication token");

                // generate keyring immediately
                // needed for connection
                if (mKey != null) {
                    String userId = MessageUtils.sha1(mPhone);
                    mKeyRing = mKey.storeNetwork(userId, mConnector.getNetwork(),
                        mName, mPassphrase);
                }
                else {
                    mKeyRing = PGPKeyPairRing.load(mImportedPrivateKey, mImportedPublicKey);
                }

                // bridge certificate for connection
                mBridgeCert = X509Bridge.createCertificate(mKeyRing.publicKey,
                    mKeyRing.secretKey.getSecretKey(), mPassphrase);

                // connect to server
                initConnection();

                // prepare final verification form
                Stanza form = createValidationForm();

                XMPPConnection conn = mConnector.getConnection();
                conn.addAsyncStanzaListener(new StanzaListener() {
                    public void processPacket(Stanza packet) {
                        IQ iq = (IQ) packet;
                        if (iq.getType() == IQ.Type.result) {
                            DataForm response = iq.getExtension("x", "jabber:x:data");
                            if (response != null) {
                                String publicKey = null;

                                // ok! message will be sent
                                List<FormField> iter = response.getFields();
                                for (FormField field : iter) {
                                    if ("publickey".equals(field.getVariable())) {
                                        publicKey = field.getValues().get(0);
                                    }
                                }

                                if (!TextUtils.isEmpty(publicKey)) {
                                    byte[] publicKeyData;
                                    byte[] privateKeyData;
                                    try {
                                        publicKeyData = Base64.decode(publicKey, Base64.DEFAULT);
                                        privateKeyData = mKeyRing.secretKey.getEncoded();
                                    }
                                    catch (Exception e) {
                                        // TODO that easy?
                                        publicKeyData = null;
                                        privateKeyData = null;
                                    }

                                    mListener.onAuthTokenReceived(NumberValidator.this, privateKeyData, publicKeyData);

                                    // prevent error handling
                                    return;
                                }
                            }
                        }

                        // validation failed :(
                        // TODO check for service-unavailable errors (meaning
                        // we must call onServerCheckFailed()
                        mListener.onAuthTokenFailed(NumberValidator.this, -1);
                        mStep = STEP_INIT;
                    }
                }, new StanzaIdFilter(form.getStanzaId()));

                // send registration form
                conn.sendStanza(form);
            }

            // try imported key by performing a login test
            else if (mStep == STEP_LOGIN_TEST) {
                if (mImportedPrivateKey == null || mImportedPublicKey == null)
                    throw new AssertionError("requesting a login test with no imported key!");

                // generate keyring immediately
                // needed for connection
                mKeyRing = PGPKeyPairRing.load(mImportedPrivateKey, mImportedPublicKey);

                // bridge certificate for connection
                mBridgeCert = X509Bridge.createCertificate(mKeyRing.publicKey,
                    mKeyRing.secretKey.getSecretKey(), mPassphrase);

                try {
                    // connect to server
                    initConnection();
                }
                catch (Exception e) {
                    // login test failed, run again normally
                    mStep = STEP_INIT;
                    // mark server as dirty
                    mConnector.setServer(mConnector.getServer());
                    run();
                    return;
                }

                // login successful!!!
                if (mListener != null)
                    mListener.onAuthTokenReceived(this,
                        mKeyRing.secretKey.getEncoded(), mKeyRing.publicKey.getEncoded());
            }
        }
        catch (Throwable e) {
            if (mListener != null)
                mListener.onError(this, e);

            mStep = STEP_INIT;
        }
    }

    /**
     * Shuts down this thread gracefully.
     */
    public synchronized void shutdown() {
        Log.w(TAG, "shutting down");
        try {
            if (mThread != null) {
                mInternalHandler.post(new Runnable() {
                    public void run() {
                        try {
                            mConnector.getConnection().disconnect();
                            mServiceHandler.quit();
                            // null everything
                            mServiceHandler = null;
                            mInternalHandler = null;
                        }
                        catch (Exception e) {
                            // ignored
                        }
                    }
                });
                mThread.interrupt();
                mThread.join();
                mThread = null;
            }

            unconfigure();
        }
        catch (Exception e) {
            // ignored
        }
        Log.w(TAG, "exiting");
    }

    /** Forcibly inputs the validation code. */
    public void manualInput(CharSequence code) {
        mValidationCode = code;
        mStep = STEP_AUTH_TOKEN;
        // next start call will trigger the next condition
        mThread = null;
    }

    public void testImport() {
        mStep = STEP_LOGIN_TEST;
        // next start call will trigger the next condition
        mThread = null;
    }

    private void initConnection() throws XMPPException, SmackException,
            PGPException, KeyStoreException, NoSuchProviderException,
            NoSuchAlgorithmException, CertificateException,
            IOException {

        if (!mConnector.isConnected() || mConnector.isServerDirty()) {
            mConnector.setListener(this);
            PersonalKey key = null;
            if (mImportedPrivateKey != null && mImportedPublicKey != null) {
                PGPKeyPairRing ring = PGPKeyPairRing.load(mImportedPrivateKey, mImportedPublicKey);
                key = PersonalKey.load(ring.secretKey, ring.publicKey, mPassphrase, mBridgeCert);
            }
            else if (mKey != null) {
                key = mKey.copy(mBridgeCert);
            }

            mConnector.connectOnce(key, mStep == STEP_LOGIN_TEST);
        }
    }

    private Stanza createRegistrationForm() {
        Registration iq = new Registration();
        iq.setType(IQ.Type.set);
        iq.setTo(mConnector.getConnection().getServiceName());
        Form form = new Form(DataForm.Type.submit);

        FormField type = new FormField("FORM_TYPE");
        type.setType(FormField.Type.hidden);
        type.addValue(Registration.NAMESPACE);
        form.addField(type);

        FormField phone = new FormField("phone");
        phone.setLabel("Phone number");
        phone.setType(FormField.Type.text_single);
        phone.addValue(mPhone);
        form.addField(phone);

        if (mForce) {
            FormField force = new FormField("force");
            force.setLabel("Force registration");
            force.setType(FormField.Type.bool);
            force.addValue(String.valueOf(mForce));
            form.addField(force);
        }

        if (mFallback) {
            FormField fallback = new FormField("fallback");
            fallback.setLabel("Fallback");
            fallback.setType(FormField.Type.bool);
            fallback.addValue(String.valueOf(mFallback));
            form.addField(fallback);
        }

        iq.addExtension(form.getDataFormToSend());
        return iq;
    }

    private Stanza createValidationForm() throws IOException {
        Registration iq = new Registration();
        iq.setType(IQ.Type.set);
        iq.setTo(mConnector.getConnection().getServiceName());
        Form form = new Form(DataForm.Type.submit);

        FormField type = new FormField("FORM_TYPE");
        type.setType(FormField.Type.hidden);
        type.addValue("http://kontalk.org/protocol/register#code");
        form.addField(type);

        FormField code = new FormField("code");
        code.setLabel("Validation code");
        code.setType(FormField.Type.text_single);
        code.addValue(mValidationCode.toString());
        form.addField(code);

        iq.addExtension(form.getDataFormToSend());
        return iq;
    }

    public synchronized void setListener(NumberValidatorListener listener) {
        mListener = listener;
    }

    public abstract interface NumberValidatorListener {
        /** Called if an exception get thrown. */
        public void onError(NumberValidator v, Throwable e);

        /** Called if the server doesn't support registration/auth tokens. */
        public void onServerCheckFailed(NumberValidator v);

        /** Called on confirmation that the validation SMS is being sent. */
        public void onValidationRequested(NumberValidator v, String sender);

        /** Called if phone number validation failed. */
        public void onValidationFailed(NumberValidator v, int reason);

        /** Called on receiving of authentication token. */
        public void onAuthTokenReceived(NumberValidator v, byte[] privateKey, byte[] publicKey);

        /** Called if validation code has not been verified. */
        public void onAuthTokenFailed(NumberValidator v, int reason);
    }

    /**
     * Converts pretty much any phone number into E.164 format.
     * @param myNumber used to take the country code if not found in the number
     * @param lastResortCc manual country code last resort
     * @throws IllegalArgumentException if no country code is available.
     */
    public static String fixNumber(Context context, String number, String myNumber, int lastResortCc)
            throws NumberParseException {

        final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String myRegionCode = tm.getSimCountryIso();
        if (myRegionCode != null)
            myRegionCode = myRegionCode.toUpperCase(Locale.US);

        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        try {
            if (myNumber != null) {
                PhoneNumber myNum = util.parse(myNumber, myRegionCode);
                // use region code found in my number
                myRegionCode = util.getRegionCodeForNumber(myNum);
            }
        }
        catch (NumberParseException e) {
            // ehm :)
        }

        PhoneNumber parsedNum;
        try {
            parsedNum = util.parse(number, myRegionCode);
        }
        catch (NumberParseException e) {
            // parse failed with default region code, try last resort
            if (lastResortCc > 0) {
                myRegionCode = util.getRegionCodeForCountryCode(lastResortCc);
                parsedNum = util.parse(number, myRegionCode);
            }
            else
                throw e;
        }

        // a NumberParseException would have been thrown at this point
        return util.format(parsedNum, PhoneNumberFormat.E164);
    }

    /** Returns the (parsed) number stored in this device SIM card. */
    public static PhoneNumber getMyNumber(Context context) {
        try {
            final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final String regionCode = tm.getSimCountryIso().toUpperCase(Locale.US);
            return PhoneNumberUtil.getInstance().parse(tm.getLine1Number(), regionCode);
        }
        catch (Exception e) {
            return null;
        }
    }

    /** Returns the localized region name for the given region code. */
    public static String getRegionDisplayName(String regionCode, Locale language) {
        return (regionCode == null || regionCode.equals("ZZ") ||
                regionCode.equals(PhoneNumberUtil.REGION_CODE_FOR_NON_GEO_ENTITY))
            ? "" : new Locale("", regionCode).getDisplayCountry(language);
    }

    @Override
    public void connectionClosed() {
        // not used
    }

    @Override
    public void connectionClosedOnError(Exception e) {
        // not used
    }

    @Override
    public void reconnectingIn(int seconds) {
        // not used
    }

    @Override
    public void reconnectionFailed(Exception e) {
        // not used
    }

    @Override
    public void reconnectionSuccessful() {
        // not used
    }

    @Override
    public void aborted(Exception e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void created(XMPPConnection conn) {
        // not used
    }

    @Override
    public void connected(XMPPConnection conn) {
        // not used
    }

    @Override
    public void authenticated(XMPPConnection conn, boolean resumed) {
        // not used
    }

    @Override
    public void authenticationFailed() {
        // not used
    }

}

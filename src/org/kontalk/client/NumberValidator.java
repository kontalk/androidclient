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

package org.kontalk.client;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.util.Iterator;
import java.util.Locale;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketIDFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Registration;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.Form;
import org.jivesoftware.smackx.FormField;
import org.jivesoftware.smackx.packet.DataForm;
import org.jivesoftware.smackx.provider.DataFormProvider;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PGP.PGPKeyPairRing;
import org.kontalk.service.XMPPConnectionHelper;
import org.kontalk.service.XMPPConnectionHelper.ConnectionHelperListener;
import org.kontalk.util.MessageUtils;
import org.spongycastle.openpgp.PGPException;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;


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

    public static final int ERROR_THROTTLING = 1;

    private final EndpointServer mServer;
    private final String mName;
    private final String mPhone;
    private PersonalKey mKey;
    private PGPKeyPairRing mKeyRing;
    private String mPassphrase;
    private volatile Object mKeyLock = new Object();

    private final XMPPConnectionHelper mConnector;
    private NumberValidatorListener mListener;
    private volatile int mStep;
    private CharSequence mValidationCode;

    private final Context mContext;
    private Thread mThread;

    private HandlerThread mServiceHandler;
    private Handler mInternalHandler;

    public NumberValidator(Context context, EndpointServer server, String name, String phone, PersonalKey key, String passphrase) {
        mContext = context.getApplicationContext();
        mServer = server;
        mName = name;
        mPhone = phone;
        mKey = key;
        mPassphrase = passphrase;

        mConnector = new XMPPConnectionHelper(mContext, mServer, true);
        mConnector.setRetryEnabled(false);

        SmackAndroid.init(context.getApplicationContext());
        configure(ProviderManager.getInstance());
    }

    private void configure(ProviderManager pm) {
        pm.addIQProvider("query", "jabber:iq:register", new RegistrationFormProvider());
        pm.addExtensionProvider("x", "jabber:x:data", new DataFormProvider());
    }

    public void setKey(PersonalKey key) {
        synchronized (mKeyLock) {
            mKey = key;
            mKeyLock.notifyAll();
        }
    }

    public PersonalKey getKey() {
        return mKey;
    }

    public EndpointServer getServer() {
        return mServer;
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
                /*
                // check that server is authorized to generate auth tokens
                mStep = STEP_CHECK_INFO;
                boolean supportsToken = checkServer();

                // server doesn't support authentication token
                if (!supportsToken) {
                    if (mListener != null) {
                        mStep = STEP_INIT;
                        mListener.onServerCheckFailed(this);
                        return;
                    }
                }
                */

                synchronized (mKeyLock) {
                    if (mKey == null) {
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
                initConnection();
                Packet form = createRegistrationForm();

                // setup listener for form response
                Connection conn = mConnector.getConnection();
                conn.addPacketListener(new PacketListener() {
                    public void processPacket(Packet packet) {
                    	int reason = 0;
                        IQ iq = (IQ) packet;

                        if (iq.getType() == IQ.Type.RESULT) {
                            DataForm response = (DataForm) iq.getExtension("x", "jabber:x:data");
                            if (response != null) {
                                // ok! message will be sent
                                Iterator<FormField> iter = response.getFields();
                                while (iter.hasNext()) {
                                    FormField field = iter.next();
                                    if (field.getVariable().equals("from")) {
                                        String smsFrom = field.getValues().next();
                                        Log.d(TAG, "using sms sender id: " + smsFrom);
                                        mListener.onValidationRequested(NumberValidator.this);

                                        // prevent error handling
                                        return;
                                    }
                                }
                            }
                        }

                        else if (iq.getType() == IQ.Type.ERROR) {
                        	XMPPError error = iq.getError();

                        	if (XMPPError.Condition.service_unavailable.toString()
                        			.equals(error.getCondition())) {

                        		if (error.getType() == XMPPError.Type.WAIT) {
                        			reason = ERROR_THROTTLING;

                        		}

                        		else {
                        			mListener.onServerCheckFailed(NumberValidator.this);
                        			// onValidationFailed will not be called
                        			reason = -1;

                        		}
                        	}

                        }

                        // validation failed :(
                        if (reason >= 0)
                        	mListener.onValidationFailed(NumberValidator.this, reason);

                        mStep = STEP_INIT;
                        return;
                    }
                }, new PacketIDFilter(form.getPacketID()));

                // send registration form
                conn.sendPacket(form);
            }

            // sms received, request authentication token
            else if (mStep == STEP_AUTH_TOKEN) {
                Log.d(TAG, "requesting authentication token");

                initConnection();
                Packet form = createValidationForm();

                Connection conn = mConnector.getConnection();
                conn.addPacketListener(new PacketListener() {
                    public void processPacket(Packet packet) {
                        IQ iq = (IQ) packet;
                        if (iq.getType() == IQ.Type.RESULT) {
                            DataForm response = (DataForm) iq.getExtension("x", "jabber:x:data");
                            if (response != null) {
                                String publicKey = null;

                                // ok! message will be sent
                                Iterator<FormField> iter = response.getFields();
                                while (iter.hasNext()) {
                                    FormField field = iter.next();
                                    if ("publickey".equals(field.getVariable())) {
                                        publicKey = field.getValues().next();
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
                        return;
                    }
                }, new PacketIDFilter(form.getPacketID()));

                // send registration form
                conn.sendPacket(form);
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

    public int getStep() {
        return mStep;
    }

    private void initConnection() throws XMPPException, PGPException,
    		KeyStoreException, NoSuchProviderException,
    		NoSuchAlgorithmException, CertificateException,
    		IOException {

        if (!mConnector.isConnected()) {
            mConnector.setListener(this);
            mConnector.connectOnce(null);
        }
    }

    private Packet createRegistrationForm() {
        Registration iq = new Registration();
        iq.setType(IQ.Type.SET);
        iq.setTo(mConnector.getConnection().getServiceName());
        Form form = new Form(Form.TYPE_SUBMIT);

        FormField type = new FormField("FORM_TYPE");
        type.setType(FormField.TYPE_HIDDEN);
        type.addValue("jabber:iq:register");
        form.addField(type);

        FormField phone = new FormField("phone");
        phone.setLabel("Phone number");
        phone.setType(FormField.TYPE_TEXT_SINGLE);
        phone.addValue(mPhone);
        form.addField(phone);

        iq.addExtension(form.getDataFormToSend());
        return iq;
    }

    private Packet createValidationForm() {
        Registration iq = new Registration();
        iq.setType(IQ.Type.SET);
        iq.setTo(mConnector.getConnection().getServiceName());
        Form form = new Form(Form.TYPE_SUBMIT);

        FormField type = new FormField("FORM_TYPE");
        type.setType(FormField.TYPE_HIDDEN);
        type.addValue("http://kontalk.org/protocol/register#code");
        form.addField(type);

        FormField code = new FormField("code");
        code.setLabel("Validation code");
        code.setType(FormField.TYPE_TEXT_SINGLE);
        code.addValue(mValidationCode.toString());
        form.addField(code);

        if (mKey != null) {
            String publicKey;
            try {
                String userId = MessageUtils.sha1(mPhone);
                // TODO what in name and comment fields here?
                mKeyRing = mKey.storeNetwork(userId, mServer.getNetwork(),
                    mName, mPassphrase);
                publicKey = Base64.encodeToString(mKeyRing.publicKey.getEncoded(), Base64.NO_WRAP);
            }
            catch (Exception e) {
                // TODO
                Log.v(TAG, "error saving key", e);
                publicKey = null;
            }

            if (publicKey != null) {
                FormField key = new FormField("publickey");
                key.setLabel("Public key");
                key.setType(FormField.TYPE_TEXT_SINGLE);
                key.addValue(publicKey);
                form.addField(key);
            }
        }

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
        public void onValidationRequested(NumberValidator v);

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
        // TODO Auto-generated method stub

    }

    @Override
    public void connectionClosedOnError(Exception arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void reconnectingIn(int arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void reconnectionFailed(Exception arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void reconnectionSuccessful() {
        // TODO Auto-generated method stub

    }

    @Override
    public void aborted(Exception e) {
        // TODO Auto-generated method stub

    }

    @Override
    public void created() {
        // TODO Auto-generated method stub

    }

    @Override
    public void connected() {
        // TODO Auto-generated method stub

    }

    @Override
    public void authenticated() {
        // TODO Auto-generated method stub

    }
}

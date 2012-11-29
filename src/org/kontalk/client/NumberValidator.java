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

package org.kontalk.client;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.jivesoftware.smack.Connection;
import org.kontalk.ui.MessagingPreferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;


/**
 * A basic worker thread for doing number validation procedures.
 * It handles all the steps defined in phone number validation, from the
 * validation request to the received SMS and finally the authentication token
 * request.
 * @author Daniele Ricci
 * @version 1.0
 */
public class NumberValidator implements Runnable {
    private static final String TAG = NumberValidator.class.getSimpleName();

    private static final int MAX_SMS_WAIT_TIME = 30000;

    /** Initialization */
    public static final int STEP_INIT = 0;
    /** Serverinfo check */
    public static final int STEP_CHECK_INFO = 1;
    /** Validation step (sending phone number and waiting for SMS) */
    public static final int STEP_VALIDATION = 2;
    /** Requesting authentication token */
    public static final int STEP_AUTH_TOKEN = 3;

    private final Context mContext;
    private final EndpointServer mServer;
    private final String mPhone;
    private final Connection mClient;
    private final boolean mManual;
    private NumberValidatorListener mListener;
    private volatile int mStep;
    private CharSequence mValidationCode;
    private BroadcastReceiver mSmsReceiver;
    private volatile String mSmsFrom;
    private boolean mAlreadyChecked;

    private Runnable mTimeout;
    private Handler mHandler;
    private Thread mThread;

    public NumberValidator(Context context, EndpointServer server, String phone, boolean manual) {
        mContext = context.getApplicationContext();
        mServer = server;
        mPhone = phone;
        mClient = new KontalkConnection(mServer);
        mManual = manual;
    }

    public synchronized void start() {
        if (mThread != null) throw new IllegalArgumentException("already started");
        mThread = new Thread(this);
        mThread.start();
    }

    @Override
    public void run() {
        try {
            // begin!
            if (mStep == STEP_INIT) {
                // unregister previous receiver
                cancelBroadcastReceiver();

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

                mAlreadyChecked = true;

                if (!mManual) {
                    // setup the sms receiver
                    IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
                    mSmsReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            Log.d(TAG, "SMS received! " + intent.toString());

                            Bundle bdl = intent.getExtras();
                            Object pdus[] = (Object [])bdl.get("pdus");
                            for(int n=0; n < pdus.length; n++) {
                                byte[] byteData = (byte[])pdus[n];
                                SmsMessage sms = SmsMessage.createFromPdu(byteData);

                                // wait for sms origin to be filled
                                synchronized (this) {
                                    while (mSmsFrom == null) {
                                        try {
                                            wait();
                                        }
                                        catch (InterruptedException e) {
                                        }
                                    }
                                }

                                // possible message!
                                if (mSmsFrom.equals(sms.getOriginatingAddress()) ||
                                        PhoneNumberUtils.compare(mSmsFrom, sms.getOriginatingAddress())) {
                                    String txt = sms.getMessageBody();
                                    if (txt != null && txt.length() > 0) {
                                        clearTimeout();
                                        // FIXME take the entire message text for now
                                        mValidationCode = txt;
                                        mStep = STEP_AUTH_TOKEN;
                                        break;
                                    }
                                }
                            }

                            Log.d(TAG, "validation code found = \"" + mValidationCode + "\"");

                            if (mValidationCode != null) {
                                // unregister this receiver
                                context.unregisterReceiver(this);
                                mSmsReceiver = null;

                                // next start call will trigger the next condition
                                mThread = null;

                                // the listener will call start() again
                                if (mListener != null)
                                    mListener.onValidationCodeReceived(NumberValidator.this, mValidationCode);
                            }
                        }
                    };
                    mContext.registerReceiver(mSmsReceiver, filter);
                }

                // request number validation via sms
                mStep = STEP_VALIDATION;
                /*
                mClient.reconnect();
                RegistrationResponse res = mClient.registerWait(mPhone);
                if (mListener != null) {
                    // clear previous timeout
                    clearTimeout();
                    // set timeout
                    if (!mManual)
                        setTimeout();

                    if (res.getStatus() == RegistrationStatus.STATUS_CONTINUE) {

                        if (!mManual) {
                            if (res.hasSmsFrom()) {
                                mSmsFrom = res.getSmsFrom();
                                Log.d(TAG, "using sms sender id: " + mSmsFrom);
                                mListener.onValidationRequested(this);
                                synchronized (mSmsReceiver) {
                                    mSmsReceiver.notifyAll();
                                }
                            }
                            else {
                                // no sms from identification?
                                throw new IllegalArgumentException("no sms from id");
                            }
                        }
                        else {
                            mListener.onValidationRequested(this);
                        }
                    }
                    else {
                        // validation failed :(
                        mListener.onValidationFailed(this, res.getStatus());
                        mStep = STEP_INIT;
                        return;
                    }
                }
                */

                // validation succeded! Waiting for the sms...
            }

            // sms received, request authentication token
            else if (mStep == STEP_AUTH_TOKEN) {
                if (!mAlreadyChecked) {
                    // server doesn't support authentication token
                    if (!checkServer()) {
                        if (mListener != null) {
                            mStep = STEP_INIT;
                            mListener.onServerCheckFailed(this);
                            return;
                        }
                    }
                }

                Log.d(TAG, "requesting authentication token");

                /*
                mClient.reconnect();
                ValidationResponse res = mClient.validateWait(mValidationCode.toString());
                if (mListener != null) {
                    if (res.getStatus() == ValidationStatus.STATUS_SUCCESS) {
                        if (res.hasToken()) {
                            String token = res.getToken();
                            if (!TextUtils.isEmpty(token))
                                mListener.onAuthTokenReceived(this, token);
                        }
                    }
                    else {
                        // authentication failed :(
                        mListener.onAuthTokenFailed(this, res.getStatus());
                        mStep = STEP_INIT;
                        return;
                    }
                }
                */
            }
        }
        catch (Throwable e) {
            if (mListener != null)
                mListener.onError(this, e);

            mStep = STEP_INIT;
        }
        finally {
            mClient.disconnect();
        }
    }

    /**
     * Shuts down this thread gracefully.
     */
    public synchronized void shutdown() {
        Log.w(TAG, "shutting down");
        try {
            clearTimeout();

            if (mThread != null) {
                mClient.disconnect();
                mThread.interrupt();
                mThread.join();
                mThread = null;
            }
            cancelBroadcastReceiver();
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

    public void cancelBroadcastReceiver() {
        if (mSmsReceiver != null) {
            mContext.unregisterReceiver(mSmsReceiver);
            mSmsReceiver = null;
        }
    }

    private void clearTimeout() {
        if (mTimeout != null && mHandler != null)
            mHandler.removeCallbacks(mTimeout);
    }

    private void setTimeout() {
        if (mHandler != null) {
            mTimeout = new Runnable() {
                public void run() {
                    mTimeout = null;
                    if (mListener != null)
                        mListener.onValidationCodeTimeout(NumberValidator.this);
                    mStep = STEP_INIT;
                }
            };

            // set SMS timeout
            mHandler.postDelayed(mTimeout, MAX_SMS_WAIT_TIME);
        }
    }

    private boolean checkServer() throws IOException {
        /*
        mClient.reconnect();
        ServerInfoResponse info = mClient.serverinfoWait();
        if (info != null) {
            List<String> list = info.getSupportsList();
            for (String support : list) {
                if ("auth_token".equals(support))
                    return true;
            }
            return false;
        }
        else {
            // error - notify listener
            throw new IOException("unable to request server information");
        }
        */
        // TODO
        throw new IOException("unable to request server information");
    }

    public synchronized void setListener(NumberValidatorListener listener, Handler handler) {
        // clear any timeout set in the previous handler
        clearTimeout();
        mListener = listener;
        mHandler = handler;
        // retaining previous running validation
        if (mStep == STEP_VALIDATION && mHandler != null) {
            if (!mManual)
                setTimeout();
        }
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

        /** Called when the validation code SMS has been received. */
        public void onValidationCodeReceived(NumberValidator v, CharSequence code);

        /** Called if we are waiting too much time for the SMS. */
        public void onValidationCodeTimeout(NumberValidator v);

        /** Called on receiving of authentication token. */
        public void onAuthTokenReceived(NumberValidator v, CharSequence token);

        /** Called if validation code has not been verified. */
        public void onAuthTokenFailed(NumberValidator v, int reason);
    }

    public static CharSequence getCountryCode(Context context) {
        final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        final String regionCode = tm.getSimCountryIso().toUpperCase();
        int cc = PhoneNumberUtil.getInstance().getCountryCodeForRegion(regionCode);
        return cc > 0 ? String.valueOf(cc) : "";
    }

    public static CharSequence getCountryPrefix(Context context) {
        return getCountryPrefix(context, null, -1);
    }

    public static CharSequence getCountryPrefix(Context context, String from, int lastResort) {
        final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        final String regionCode = tm.getSimCountryIso().toUpperCase(Locale.US);
        int cc = PhoneNumberUtil.getInstance().getCountryCodeForRegion(regionCode);
        if (cc <= 0) {
            cc = MessagingPreferences.getLastCountryCode(context);
            // still no country code - take it from the given number (if any)
            if (cc <= 0 && from != null) {
                try {
                    PhoneNumber n = PhoneNumberUtil.getInstance().parse(from, null);
                    cc = n.getCountryCode();
                }
                catch (Exception e) {
                    // ignore exception for now
                    Log.d(TAG, "error parsing number: " + from, e);
                }
            }
        }

        // last resort
        if (cc <= 0)
            cc = lastResort;

        // try again...
        if (cc > 0) {
            MessagingPreferences.setLastCountryCode(context, cc);
            return "+" + cc;
        }

        // give up... :(
        return null;
    }

    public static String fixNumber(Context context, String number, String myNumber, String lastResort)
            throws IllegalArgumentException {
        // normalize number: strip separators
        number = PhoneNumberUtils.stripSeparators(number.trim());

        // normalize number: add country code if not found
        if (number.startsWith("00"))
            number = '+' + number.substring(2);
        else if (number.charAt(0) != '+') {
            int cc;
            try {
                cc = Integer.parseInt(lastResort);
            }
            catch (Exception e) {
                cc = -1;
            }

            CharSequence prefix = getCountryPrefix(context, myNumber, cc);
            if (prefix == null)
                throw new IllegalArgumentException("no country code available");
            number = prefix + number;
        }

        return number;
    }

    /** Returns the (parsed) number stored in this device SIM card. */
    public static PhoneNumber getMyNumber(Context context) {
        try {
            final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            final String regionCode = tm.getSimCountryIso().toUpperCase();
            return PhoneNumberUtil.getInstance().parse(tm.getLine1Number(), regionCode);
        }
        catch (Exception e) {
            return null;
        }
    }
}

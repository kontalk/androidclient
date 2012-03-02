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

import org.kontalk.ui.MessagingPreferences;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
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

    /** Initialization */
    public static final int STEP_INIT = 0;
    /** Validation step (sending phone number and waiting for SMS) */
    public static final int STEP_VALIDATION = 1;
    /** Requesting authentication token */
    public static final int STEP_AUTH_TOKEN = 2;

    private final Context mContext;
    private final EndpointServer mServer;
    private final String mPhone;
    private final RequestClient mClient;
    private final boolean mManual;
    private NumberValidatorListener mListener;
    private int mStep;
    private CharSequence mValidationCode;
    private BroadcastReceiver mSmsReceiver;
    private String mSmsFrom;

    private Thread mThread;

    public NumberValidator(Context context, EndpointServer server, String phone, boolean manual) {
        mContext = context;
        mServer = server;
        mPhone = phone;
        mClient = new RequestClient(context, mServer, null);
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
                if (mSmsReceiver != null) {
                    mContext.unregisterReceiver(mSmsReceiver);
                    mSmsReceiver = null;
                }

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

                                // possible message!
                                if (mSmsFrom.equals(sms.getOriginatingAddress()) ||
                                        PhoneNumberUtils.compare(mSmsFrom, sms.getOriginatingAddress())) {
                                    String txt = sms.getMessageBody();
                                    if (txt != null && txt.length() > 0) {
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
                Protocol.PhoneValidation res = mClient.validate(mPhone);
                if (mListener != null) {

                    if (res.getStatus() == Protocol.Status.STATUS_SUCCESS) {

                        if (res.hasSmsFrom()) {
                            mSmsFrom = res.getSmsFrom();
                            Log.d(TAG, "using sms sender id: " + mSmsFrom);
                            mListener.onValidationRequested(this);
                        }
                        else {
                            // no sms from identification?
                            throw new IllegalArgumentException("no sms from id");
                        }
                    }
                    else {
                        // validation failed :(
                        mListener.onValidationFailed(this, res.getStatus());
                        mStep = STEP_INIT;
                        return;
                    }
                }

                // validation succeded! Waiting for the sms...
            }

            // sms received, request authentication token
            else if (mStep == STEP_AUTH_TOKEN) {
                Log.d(TAG, "requesting authentication token");

                Protocol.Authentication res = mClient.authenticate(mValidationCode.toString());
                if (mListener != null) {
                    if (res.getStatus() == Protocol.Status.STATUS_SUCCESS) {
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
                mClient.abort();
                mThread.interrupt();
                mThread.join();
                mThread = null;
            }
            if (mSmsReceiver != null) {
                mContext.unregisterReceiver(mSmsReceiver);
                mSmsReceiver = null;
            }
        }
        catch (InterruptedException e) {
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

    public synchronized void setListener(NumberValidatorListener listener) {
        mListener = listener;
    }

    public abstract interface NumberValidatorListener {
        /** Called if an exception get thrown. */
        public void onError(NumberValidator v, Throwable e);

        /** Called on confirmation that the validation SMS is being sent. */
        public void onValidationRequested(NumberValidator v);

        /** Called if phone number validation failed. */
        public void onValidationFailed(NumberValidator v, Protocol.Status reason);

        /** Called when the validation code SMS has been received. */
        public void onValidationCodeReceived(NumberValidator v, CharSequence code);

        /** Called on receiving of authentication token. */
        public void onAuthTokenReceived(NumberValidator v, CharSequence token);

        /** Called if validation code has not been verified. */
        public void onAuthTokenFailed(NumberValidator v, Protocol.Status reason);
    }

    public static String getCountryPrefix(Context context) {
        return getCountryPrefix(context, null);
    }

    public static String getCountryPrefix(Context context, String from) {
        final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        final String regionCode = tm.getSimCountryIso().toUpperCase();
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

        // try again...
        if (cc > 0) {
            MessagingPreferences.setLastCountryCode(context, cc);
            return "+" + cc;
        }

        // give up... :(
        return null;
    }

    public static String fixNumber(Context context, String number, String myNumber)
            throws IllegalArgumentException {
        // normalize number: strip separators
        number = PhoneNumberUtils.stripSeparators(number.trim());

        // normalize number: add country code if not found
        if (number.startsWith("00"))
            number = '+' + number.substring(2);
        else if (number.charAt(0) != '+') {
            String prefix = getCountryPrefix(context, myNumber);
            if (prefix == null)
                throw new IllegalArgumentException("no country code available");
            number = prefix + number;
        }

        return number;
    }
}

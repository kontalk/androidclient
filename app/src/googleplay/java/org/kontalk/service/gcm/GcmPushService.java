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

package org.kontalk.service.gcm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.kontalk.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.kontalk.Kontalk;
import org.kontalk.service.msgcenter.IPushListener;
import org.kontalk.service.msgcenter.IPushService;
import org.kontalk.util.SystemUtils;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Random;
import java.util.concurrent.TimeUnit;


/**
 * Push service for Google Cloud Messaging.
 * @author Daniele Ricci
 */
public class GcmPushService implements IPushService {
    private static final String TAG = Kontalk.TAG;

    /**
     * Default lifespan (7 days) of the {@link #isRegisteredOnServer()}
     * flag until it is considered expired.
     */
    // NOTE: cannot use TimeUnit.DAYS because it's not available on API Level 8
    public static final long DEFAULT_ON_SERVER_LIFESPAN_MS =
            1000 * 3600 * 24 * 7;

    private static final Random sRandom = new Random();

    private static final String BACKOFF_MS = "backoff_ms";
    private static final int DEFAULT_BACKOFF_MS = 3000;
    private static final int MAX_BACKOFF_MS =
            (int) TimeUnit.SECONDS.toMillis(3600); // 1 hour

    private static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER = "onServer";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME =
            "onServerExpirationTime";
    private static final String PROPERTY_ON_SERVER_LIFESPAN =
            "onServerLifeSpan";

    private IPushListener mListener;
    private Context mContext;
    private GoogleCloudMessaging mGcm;

    public GcmPushService(Context context) {
        mContext = context.getApplicationContext();
    }

    private void ensureGcmInstance() {
        if (mGcm == null)
            mGcm = GoogleCloudMessaging.getInstance(mContext);
    }

    @Override
    public void register(final IPushListener listener, final String senderId) {
        mListener = listener;
        resetBackoff();

        new Thread(new Runnable() {
            public void run() {
                ensureGcmInstance();

                try {
                    String regId = mGcm.register(senderId);

                    // persist the regID - no need to register again.
                    storeRegistrationId(regId);

                    // call the listener
                    listener.onRegistered(mContext, regId);

                }
                catch (IOException e) {
                    listener.onError(mContext, e.toString());
                }
            }
        }).start();
    }

    @Override
    public void unregister(final IPushListener listener) {
        mListener = listener;
        resetBackoff();

        new Thread(new Runnable() {
            public void run() {
                ensureGcmInstance();

                try {
                    mGcm.unregister();

                    // persist the regID - no need to register again.
                    storeRegistrationId("");

                    // call the listener
                    listener.onUnregistered(mContext);

                }
                catch (IOException e) {
                    listener.onError(mContext, e.toString());

                    retryOnError();
                }
            }
        }).start();
    }

    public void retry() {
        String senderId = mListener.getSenderId(mContext);
        if (isRegistered() || senderId == null) {
            // force unregister if sender id is not present
            unregister(mListener);
        }
        else {
            register(mListener, senderId);
        }

    }

    @Override
    public boolean isRegistered() {
        return getRegistrationId().length() > 0;
    }

    @Override
    public boolean isServiceAvailable() {
        int status = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(mContext);
        return status == ConnectionResult.SUCCESS ||
            status == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED;
    }

    @Override
    public void setRegisteredOnServer(boolean flag) {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PROPERTY_ON_SERVER, flag);
        // set the flag's expiration date
        long lifespan = getRegisterOnServerLifespan();
        long expirationTime = System.currentTimeMillis() + lifespan;
        Log.v(TAG, "Setting registeredOnServer status as " + flag + " until " +
                new Timestamp(expirationTime));
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }

    @Override
    public boolean isRegisteredOnServer() {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        boolean isRegistered = prefs.getBoolean(PROPERTY_ON_SERVER, false);
        Log.v(TAG, "Is registered on server: " + isRegistered);
        if (isRegistered) {
            // checks if the information is not stale
            long expirationTime =
                    prefs.getLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, -1);
            if (System.currentTimeMillis() > expirationTime) {
                Log.v(TAG, "flag expired on: " + new Timestamp(expirationTime));
                return false;
            }
        }
        return isRegistered;
    }

    @Override
    public String getRegistrationId() {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        String registrationId = prefs.getString(PROPERTY_REG_ID, "");
        if (registrationId == null || registrationId.length() == 0) {
            Log.i(TAG, "Registration not found.");
            return "";
        }

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing regID is not guaranteed to work with the new
        // app version.
        int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
        int currentVersion = SystemUtils.getVersionCode();
        if (registeredVersion != currentVersion) {
            Log.i(TAG, "App version changed.");
            return "";
        }
        return registrationId;
    }

    @Override
    public long getRegisterOnServerLifespan() {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        long lifespan = prefs.getLong(PROPERTY_ON_SERVER_LIFESPAN,
                DEFAULT_ON_SERVER_LIFESPAN_MS);
        return lifespan;
    }

    @Override
    public void setRegisterOnServerLifespan(long lifespan) {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(PROPERTY_ON_SERVER_LIFESPAN, lifespan);
        editor.commit();
    }

    /**
     * Resets the backoff counter.
     * <p>
     * This method should be called after a GCM call succeeds.
     *
     */
    void resetBackoff() {
        Log.d(TAG, "resetting backoff for " + mContext.getPackageName());
        setBackoff(DEFAULT_BACKOFF_MS);
    }

    /**
     * Gets the current backoff counter.
     *
     * @return current backoff counter, in milliseconds.
     */
    int getBackoff() {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        return prefs.getInt(BACKOFF_MS, DEFAULT_BACKOFF_MS);
    }

    /**
     * Sets the backoff counter.
     * <p>
     * This method should be called after a GCM call fails, passing an
     * exponential value.
     *
     * @param backoff new backoff counter, in milliseconds.
     */
    void setBackoff(int backoff) {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(BACKOFF_MS, backoff);
        editor.commit();
    }

    private void storeRegistrationId(String regId) {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        int appVersion = SystemUtils.getVersionCode();
        prefs.edit()
            .putString(PROPERTY_REG_ID, regId)
            .putInt(PROPERTY_APP_VERSION, appVersion)
            .commit();
    }

    private void retryOnError() {
        int backoffTimeMs = getBackoff();
        int nextAttempt = backoffTimeMs / 2 + sRandom.nextInt(backoffTimeMs);
        Log.d(TAG, "Scheduling registration retry, backoff = "
                + nextAttempt + " (" + backoffTimeMs + ")");

        PendingIntent retryPendingIntent = GcmIntentService.getRetryIntent(mContext);
        AlarmManager am = (AlarmManager) mContext
                .getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()
                + nextAttempt, retryPendingIntent);

        // Next retry should wait longer.
        if (backoffTimeMs < MAX_BACKOFF_MS) {
            setBackoff(backoffTimeMs * 2);
        }

    }

    private static SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return context.getSharedPreferences(context.getPackageName() + ".gcm",
                Context.MODE_PRIVATE);
    }

}

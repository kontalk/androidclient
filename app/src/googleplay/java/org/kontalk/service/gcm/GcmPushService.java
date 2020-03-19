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

package org.kontalk.service.gcm;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.service.msgcenter.IPushListener;
import org.kontalk.service.msgcenter.IPushService;
import org.kontalk.util.SystemUtils;


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
    private static final long DEFAULT_ON_SERVER_LIFESPAN_MS = TimeUnit.DAYS.toMillis(7);

    private static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER = "onServer";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME =
            "onServerExpirationTime";
    private static final String PROPERTY_ON_SERVER_LIFESPAN =
            "onServerLifeSpan";

    private IPushListener mListener;
    private Context mContext;

    public GcmPushService(Context context) {
        mContext = context.getApplicationContext();
    }

    @Override
    public void register(final IPushListener listener, final String senderId) {
        mListener = listener;

        FirebaseInstanceId.getInstance().getInstanceId()
            .addOnSuccessListener(new OnSuccessListener<InstanceIdResult>() {
                @Override
                public void onSuccess(InstanceIdResult instanceIdResult) {
                    // persist the regID - no need to register again.
                    storeRegistrationId(instanceIdResult.getToken());

                    // call the listener
                    listener.onRegistered(mContext, instanceIdResult.getToken());
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    listener.onError(mContext, e.toString());
                }
            })
            .addOnCanceledListener(new OnCanceledListener() {
                @Override
                public void onCanceled() {
                    listener.onError(mContext, "canceled");
                }
            });
    }

    @Override
    public void unregister(final IPushListener listener) {
        mListener = listener;

        new Thread(new Runnable() {
            public void run() {
                try {
                    FirebaseInstanceId.getInstance().deleteInstanceId();

                    // persist the regID - no need to register again.
                    storeRegistrationId("");

                    // call the listener
                    listener.onUnregistered(mContext);

                }
                catch (IOException e) {
                    listener.onError(mContext, e.toString());
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

    @SuppressLint("ApplySharedPref")
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

    @SuppressLint("ApplySharedPref")
    @Override
    public void setRegisterOnServerLifespan(long lifespan) {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(PROPERTY_ON_SERVER_LIFESPAN, lifespan);
        editor.commit();
    }

    @SuppressLint("ApplySharedPref")
    private void storeRegistrationId(String regId) {
        final SharedPreferences prefs = getGCMPreferences(mContext);
        int appVersion = SystemUtils.getVersionCode();
        prefs.edit()
            .putString(PROPERTY_REG_ID, regId)
            .putInt(PROPERTY_APP_VERSION, appVersion)
            .commit();
    }

    private static SharedPreferences getGCMPreferences(Context context) {
        // This sample app persists the registration ID in shared preferences, but
        // how you store the regID in your app is up to you.
        return context.getSharedPreferences(context.getPackageName() + ".gcm",
                Context.MODE_PRIVATE);
    }

}

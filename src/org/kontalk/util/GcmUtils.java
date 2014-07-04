package org.kontalk.util;

import java.io.IOException;
import java.sql.Timestamp;

import org.kontalk.Kontalk;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.gcm.GcmListener;
import org.kontalk.service.gcm.GcmIntentService;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class GcmUtils {

	/** GCM message received from server. */
    private static final String ACTION_CHECK_MESSAGES = "org.kontalk.CHECK_MESSAGES";

    /**
     * Default lifespan (7 days) of the {@link #isRegisteredOnServer(Context)}
     * flag until it is considered expired.
     */
    // NOTE: cannot use TimeUnit.DAYS because it's not available on API Level 8
    public static final long DEFAULT_ON_SERVER_LIFESPAN_MS =
            1000 * 3600 * 24 * 7;

    private static final int DEFAULT_BACKOFF_MS = 3000;

    private static final String PROPERTY_REG_ID = "registration_id";
    private static final String PROPERTY_APP_VERSION = "appVersion";
    private static final String PROPERTY_ON_SERVER = "onServer";
    private static final String PROPERTY_ON_SERVER_EXPIRATION_TIME =
            "onServerExpirationTime";
    private static final String PROPERTY_ON_SERVER_LIFESPAN =
            "onServerLifeSpan";

    private static GoogleCloudMessaging sGcm;

	private GcmUtils() {
	}

    private static void ensureGcmInstance(Context context) {
    	if (sGcm == null)
    		sGcm = GoogleCloudMessaging.getInstance(context);
    }

	public static String getRegistrationId(Context context) {
	    final SharedPreferences prefs = getGCMPreferences(context);
	    String registrationId = prefs.getString(PROPERTY_REG_ID, "");
	    if (registrationId == null || registrationId.length() == 0) {
	        Log.i(Kontalk.TAG, "Registration not found.");
	        return "";
	    }

	    // Check if app was updated; if so, it must clear the registration ID
	    // since the existing regID is not guaranteed to work with the new
	    // app version.
	    int registeredVersion = prefs.getInt(PROPERTY_APP_VERSION, Integer.MIN_VALUE);
	    int currentVersion = Kontalk.getVersionCode(context);
	    if (registeredVersion != currentVersion) {
	        Log.i(Kontalk.TAG, "App version changed.");
	        return "";
	    }
	    return registrationId;
	}

	private static void storeRegistrationId(Context context, String regId) {
	    final SharedPreferences prefs = getGCMPreferences(context);
	    int appVersion = Kontalk.getVersionCode(context);
	    prefs.edit()
	    	.putString(PROPERTY_REG_ID, regId)
	    	.putInt(PROPERTY_APP_VERSION, appVersion)
	    	.commit();
	}

    /**
     * Sets whether the device was successfully registered in the server side.
     */
    public static void setRegisteredOnServer(Context context, boolean flag) {
        final SharedPreferences prefs = getGCMPreferences(context);
        Editor editor = prefs.edit();
        editor.putBoolean(PROPERTY_ON_SERVER, flag);
        // set the flag's expiration date
        long lifespan = getRegisterOnServerLifespan(context);
        long expirationTime = System.currentTimeMillis() + lifespan;
        Log.v(Kontalk.TAG, "Setting registeredOnServer status as " + flag + " until " +
                new Timestamp(expirationTime));
        editor.putLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, expirationTime);
        editor.commit();
    }

    /**
     * Checks whether the device was successfully registered in the server side,
     * as set by {@link #setRegisteredOnServer(Context, boolean)}.
     *
     * <p>To avoid the scenario where the device sends the registration to the
     * server but the server loses it, this flag has an expiration date, which
     * is {@link #DEFAULT_ON_SERVER_LIFESPAN_MS} by default (but can be changed
     * by {@link #setRegisterOnServerLifespan(Context, long)}).
     */
    public static boolean isRegisteredOnServer(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        boolean isRegistered = prefs.getBoolean(PROPERTY_ON_SERVER, false);
        Log.v(Kontalk.TAG, "Is registered on server: " + isRegistered);
        if (isRegistered) {
            // checks if the information is not stale
            long expirationTime =
                    prefs.getLong(PROPERTY_ON_SERVER_EXPIRATION_TIME, -1);
            if (System.currentTimeMillis() > expirationTime) {
                Log.v(Kontalk.TAG, "flag expired on: " + new Timestamp(expirationTime));
                return false;
            }
        }
        return isRegistered;
    }

    /**
     * Gets how long (in milliseconds) the {@link #isRegistered(Context)}
     * property is valid.
     *
     * @return value set by {@link #setRegisteredOnServer(Context, boolean)} or
     *      {@link #DEFAULT_ON_SERVER_LIFESPAN_MS} if not set.
     */
    public static long getRegisterOnServerLifespan(Context context) {
        final SharedPreferences prefs = getGCMPreferences(context);
        long lifespan = prefs.getLong(PROPERTY_ON_SERVER_LIFESPAN,
                DEFAULT_ON_SERVER_LIFESPAN_MS);
        return lifespan;
    }

    /**
     * Sets how long (in milliseconds) the {@link #isRegistered(Context)}
     * flag is valid.
     */
    public static void setRegisterOnServerLifespan(Context context,
            long lifespan) {
        final SharedPreferences prefs = getGCMPreferences(context);
        Editor editor = prefs.edit();
        editor.putLong(PROPERTY_ON_SERVER_LIFESPAN, lifespan);
        editor.commit();
    }

	public static void register(final GcmListener listener, final Context context, final String senderId) {
	    new Thread(new Runnable() {
			public void run() {
                ensureGcmInstance(context);

				try {
	                String regId = sGcm.register(senderId);

	                // persist the regID - no need to register again.
	                storeRegistrationId(context, regId);

	                // call the listener
	                listener.onRegistered(context, regId);

				}
				catch (IOException e) {
					listener.onError(context, e.toString());
				}
			}
		}).start();
	}

	public static void unregister(final GcmListener listener, final Context context) {
	    new Thread(new Runnable() {
			public void run() {
                ensureGcmInstance(context);

				try {
	                sGcm.unregister();

	                // persist the regID - no need to register again.
	                storeRegistrationId(context, "");

	                // call the listener
	                listener.onUnregistered(context);

				}
				catch (IOException e) {
					listener.onError(context, e.toString());
				}
			}
		}).start();
	}

    public static boolean isRegistered(Context context) {
        return getRegistrationId(context).length() > 0;
    }

    public static boolean isGcmAvailable(Context context) {
        return GooglePlayServicesUtil
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

    /** Process a new incoming {@link Intent} from {@link GcmIntentService}. */
    public static void processIntent(Context context, Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle

        	if (GoogleCloudMessaging.
                    MESSAGE_TYPE_MESSAGE.equals(messageType)) {

                String dataAction = intent.getStringExtra("action");
                Log.v(Kontalk.TAG, "cloud message received: " + dataAction);

                // new messages - start message center
                if (ACTION_CHECK_MESSAGES.equals(dataAction)) {
                	// remember we just received a push notifications
                	// this means that there are really messages waiting for us
                	Preferences.setLastPushNotification(context,
                		System.currentTimeMillis());

                	// start message center
                    MessageCenterService.start(context.getApplicationContext());
                }

            }
        }

    }

	private static SharedPreferences getGCMPreferences(Context context) {
	    // This sample app persists the registration ID in shared preferences, but
	    // how you store the regID in your app is up to you.
	    return context.getSharedPreferences(GcmUtils.class.getName(),
	            Context.MODE_PRIVATE);
	}

}

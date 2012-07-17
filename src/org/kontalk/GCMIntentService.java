package org.kontalk;

import org.kontalk.service.MessageCenterService;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gcm.GCMBaseIntentService;


/**
 * Google Cloud Messaging service.
 * Handles tasks for GCM support.
 * @author Daniele Ricci
 */
public class GCMIntentService extends GCMBaseIntentService {
    private static final String TAG = GCMIntentService.class.getSimpleName();
    private static final String ACTION_CHECK_MESSAGES = "org.kontalk.CHECK_MESSAGES";

    public GCMIntentService() {
        super(MessageCenterService.getPushSenderId());
    }

    @Override
    protected void onRegistered(Context context, String registrationId) {
        Log.i(TAG, "registered to GCM - " + registrationId);
        MessageCenterService.registerPushNotifications(context, registrationId);
    }

    @Override
    protected void onUnregistered(Context context, String registrationId) {
        Log.i(TAG, "unregistered from GCM");
        MessageCenterService.registerPushNotifications(context, null);
    }

    @Override
    protected void onError(Context context, String errorId) {
        Log.e(TAG, "error registering to GCM service: " + errorId);
    }

    @Override
    protected void onMessage(Context context, Intent intent) {
        String dataAction = intent.getStringExtra("action");
        Log.i(TAG, "cloud message received: " + dataAction);

        // new messages - start message center
        if (ACTION_CHECK_MESSAGES.equals(dataAction))
            MessageCenterService.startMessageCenter(context.getApplicationContext());
    }

}

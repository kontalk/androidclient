package org.kontalk.service.gcm;

import org.kontalk.Kontalk;
import org.kontalk.service.msgcenter.IPushListener;
import org.kontalk.service.msgcenter.MessageCenterService;

import android.content.Context;
import android.util.Log;


/**
 * Listener for the Message Center.
 * @author Daniele Ricci
 */
public class DefaultGcmListener implements IPushListener {
    private static final String TAG = Kontalk.TAG;

    @Override
    public void onRegistered(Context context, String registrationId) {
        Log.d(TAG, "registered to GCM - " + registrationId);
        MessageCenterService.registerPushNotifications(context, registrationId);
    }

    @Override
    public void onUnregistered(Context context) {
        Log.d(TAG, "unregistered from GCM");
        MessageCenterService.registerPushNotifications(context, null);
    }

    @Override
    public void onError(Context context, String errorId) {
        Log.w(TAG, "error registering to GCM service: " + errorId);
    }

    @Override
    public String getSenderId(Context context) {
        return MessageCenterService.getPushSenderId();
    }

}

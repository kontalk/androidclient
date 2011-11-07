package org.kontalk.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


/**
 * Cloud-to-device messaging receiver.
 * @author Daniele Ricci
 */
public class C2DMReceiver extends BroadcastReceiver {
    private static final String TAG = C2DMReceiver.class.getSimpleName();

    private static final String ACTION_CHECK_MESSAGES = "org.kontalk.CHECK_MESSAGES";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.google.android.c2dm.intent.REGISTRATION".equals(action)) {
            // notify c2dm registration to message center
            String registration = intent.getStringExtra("registration_id");
            if (registration != null) {
                Log.i(TAG, "registered to C2DM - " + registration);
            }
            else {
                String err = intent.getStringExtra("error");
                Log.e(TAG, "error registering to C2DM service: " + err);
            }

            Intent i = new Intent(context, MessageCenterService.class);
            i.setAction(MessageCenterService.C2DM_REGISTERED);
            i.putExtra(MessageCenterService.C2DM_REGISTRATION_ID, registration);
            context.startService(i);
        }
        else if ("com.google.android.c2dm.intent.RECEIVE".equals(action)) {
            // process push message
            Log.i("TAG", "cloud message received! " + intent);

            String dataAction = intent.getStringExtra("action");
            Log.i("TAG", "cloud message action: " + dataAction);

            if (ACTION_CHECK_MESSAGES.equals(dataAction)) {
                MessageCenterService.startMessageCenter(context.getApplicationContext());
            }
        }
    }

}

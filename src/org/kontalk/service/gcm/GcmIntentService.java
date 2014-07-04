package org.kontalk.service.gcm;

import org.kontalk.util.GcmUtils;

import android.app.IntentService;
import android.content.Intent;


/**
 * Intent service simply turning over control to {@link GcmUtils#processIntent}.
 * @author Daniele Ricci
 */
public class GcmIntentService extends IntentService {

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
    	GcmUtils.processIntent(this, intent);

        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

}

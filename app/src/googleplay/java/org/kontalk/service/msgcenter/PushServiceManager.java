package org.kontalk.service.msgcenter;

import android.content.Context;

import org.kontalk.service.gcm.DefaultGcmListener;
import org.kontalk.service.gcm.GcmPushService;


/**
 * Push service singleton container.
 * @author Daniele Ricci
 */
public class PushServiceManager {

    private static IPushService sInstance;
    private static IPushListener sListener;

    public static IPushService getInstance(Context context) {
        if (sInstance == null)
            sInstance = new GcmPushService(context);

        return sInstance;
    }

    public static IPushListener getDefaultListener() {
        if (sListener == null)
            sListener = new DefaultGcmListener();

        return sListener;
    }

}

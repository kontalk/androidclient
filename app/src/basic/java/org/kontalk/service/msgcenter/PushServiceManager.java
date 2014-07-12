package org.kontalk.service.msgcenter;


import android.content.Context;

/**
 * Push service singleton container.
 * @author Daniele Ricci
 */
public class PushServiceManager {

    private static IPushService sInstance;

    public static IPushService getInstance(Context context) {
        if (sInstance == null)
            sInstance = new DummyPushService(context);

        return sInstance;
    }

    public static IPushListener getDefaultListener() {
        return null;
    }

}

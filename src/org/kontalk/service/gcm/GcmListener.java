package org.kontalk.service.gcm;

import android.content.Context;


/**
 * Interface for GCM listeners (the Message Center in our case).
 * @author Daniele Ricci
 */
public interface GcmListener {

    public void onRegistered(Context context, String registrationId);

    public void onUnregistered(Context context);

    public void onError(Context context, String errorId);

}

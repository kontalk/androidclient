package org.kontalk.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


/**
 * Cloud-to-device messaging receiver.
 * @author Daniele Ricci
 */
public class C2DMReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if ("com.google.android.c2dm.intent.REGISTRATION".equals(action)) {
            // TODO notify c2dm registration to message center
        }
        else if ("com.google.android.c2dm.intent.RECEIVE".equals(action)) {
            // TODO process push message
        }
    }

}

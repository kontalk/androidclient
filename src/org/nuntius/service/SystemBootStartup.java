package org.nuntius.service;

import org.nuntius.authenticator.Authenticator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


/**
 * Receiver for the BOOT_COMPLETED action.
 * Starts the message center.
 * @author Daniele Ricci
 * @version 1.0
 */
public class SystemBootStartup extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (Authenticator.getDefaultAccount(context) != null) {
                MessageCenterService.startMessageCenter(context.getApplicationContext());
            }
        }
    }
}

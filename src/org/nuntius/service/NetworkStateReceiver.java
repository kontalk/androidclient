package org.nuntius.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.util.Log;


/**
 * Receives changes of the network state to start/stop the message service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class NetworkStateReceiver extends BroadcastReceiver {
    private static final String TAG = NetworkStateReceiver.class.getSimpleName();

    private static final int ACTION_START = 1;
    private static final int ACTION_STOP = 2;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        int serviceAction = 0;

        // background data setting has changed
        if (ConnectivityManager.ACTION_BACKGROUND_DATA_SETTING_CHANGED
                .equals(action)) {

            final ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

            // if background data gets deactivated, just stop the service now
            if (!cm.getBackgroundDataSetting()) {
                Log.w(TAG, "background data disabled!");
                serviceAction = ACTION_STOP;
            }
            else {
                Log.w(TAG, "background data enabled!");
                serviceAction = ACTION_START;
            }
        }

        // connectivity status has changed
        else if (ConnectivityManager.CONNECTIVITY_ACTION
                .equals(action)) {
            // TODO handle FAILOVER_CONNECTION

            final NetworkInfo info = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
            if (info != null) {
                Log.w(TAG, "network state changed!");
                serviceAction = (info.getState() == State.CONNECTED) ?
                    ACTION_START : ACTION_STOP;
            }
        }

        if (serviceAction == ACTION_START)
            // start the message center
            MessageCenterService.startMessageCenter(context);

        else if (serviceAction == ACTION_STOP)
            // stop the message center
            MessageCenterService.stopMessageCenter(context);
    }

}

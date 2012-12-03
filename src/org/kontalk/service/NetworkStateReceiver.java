/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
                switch (info.getState()) {
                    case CONNECTED:
                        serviceAction = ACTION_START;
                        break;
                    case DISCONNECTED:
                    case DISCONNECTING:
                    case UNKNOWN:
                        serviceAction = ACTION_STOP;
                        break;
                    // do nothing in other cases
                }
            }
        }

        if (serviceAction == ACTION_START)
            // start the message center
            MessageCenterServiceLegacy.startMessageCenter(context);

        else if (serviceAction == ACTION_STOP)
            // stop the message center
            MessageCenterServiceLegacy.stopMessageCenter(context);
    }

}

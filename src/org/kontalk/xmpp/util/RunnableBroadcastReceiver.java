package org.kontalk.xmpp.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;


/**
 * A {@link BroadcastReceiver} that posts a {@link Runnable} to a
 * {@link Handler}.
 */
public final class RunnableBroadcastReceiver extends BroadcastReceiver {
    private Runnable mAction;
    private Handler mHandler;

    public RunnableBroadcastReceiver(Runnable action, Handler handler) {
        mAction = action;
        mHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // push action
        mHandler.post(mAction);
    }

}
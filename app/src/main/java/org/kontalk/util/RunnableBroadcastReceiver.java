/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;


/**
 * A {@link BroadcastReceiver} that posts a {@link Runnable} or an
 * {@link ActionRunnable} to a {@link Handler}.
 */
public final class RunnableBroadcastReceiver extends BroadcastReceiver {
    private Runnable mAction;
    private ActionRunnable mAction2;
    private Handler mHandler;

    /** Will fire a plain no-arguments {@link Runnable}. */
    public RunnableBroadcastReceiver(Runnable action, Handler handler) {
        mAction = action;
        mHandler = handler;
    }

    /** Will fire a {@link ActionRunnable} with the intent action. */
    public RunnableBroadcastReceiver(ActionRunnable action, Handler handler) {
        mAction2 = action;
        mHandler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // push action
        final String action = intent.getAction();
        mHandler.post(mAction2 != null ? new Runnable() {
            public void run() {
                mAction2.run(action);
            }
        } : mAction);
    }

    public interface ActionRunnable {
        public void run(String action);
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.os.Handler;
import android.os.Looper;
import androidx.test.espresso.IdlingResource;


/**
 * An idling resource that waits for an event to be idle.
 */
public class EventIdlingResource implements IdlingResource {

    private static final long DEFAULT_TIMEOUT_MS = 2000;

    private final String mName;
    private final EventBus mBus;
    private final Class mClass;

    private final long mTimeoutMs;

    private final Handler mHandler;
    private final Runnable mTransitionToIdle;

    private boolean mRunning;
    private ResourceCallback mCallback;
    private boolean mEventReceived;

    public EventIdlingResource(String name, EventBus bus, Class klass) {
        this(name, bus, klass, DEFAULT_TIMEOUT_MS);
    }

    public EventIdlingResource(String name, EventBus bus, Class klass, long timeoutMs) {
        mName = name;
        mBus = bus;
        mClass = klass;
        mBus.register(this);
        mTimeoutMs = timeoutMs;
        mHandler = new Handler(Looper.getMainLooper());

        mTransitionToIdle = new Runnable() {
            @Override
            public void run() {
                mEventReceived = true;
                if (mCallback != null) {
                    mCallback.onTransitionToIdle();
                }
            }
        };
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onEvent(Object event) {
        if (event.getClass() == mClass) {
            Log.d("TEST", "got idling event: " + event);
            mHandler.postDelayed(mTransitionToIdle, mTimeoutMs);
            mBus.unregister(this);
        }
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isIdleNow() {
        return !mRunning || mEventReceived;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.mCallback = callback;
    }

    public void start() {
        mRunning = true;
    }
}

package org.kontalk;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import android.support.test.espresso.IdlingResource;


/**
 * An idling resource that waits for an event to be idle.
 */
public class EventIdlingResource<T> implements IdlingResource {

    private final String mName;
    private final EventBus mBus;

    private ResourceCallback mCallback;
    private boolean mEventReceived;

    public EventIdlingResource(String name, EventBus bus) {
        mName = name;
        mBus = bus;
        mBus.register(this);
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    public void onEvent(T event) {
        mEventReceived = true;
        mBus.unregister(this);
        if (mCallback != null) {
            mCallback.onTransitionToIdle();
        }
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isIdleNow() {
        return mEventReceived;
    }

    @Override
    public void registerIdleTransitionCallback(ResourceCallback callback) {
        this.mCallback = callback;
    }
}

package org.kontalk.service.msgcenter;


import android.content.Context;

/**
 * Dummy push client service.
 * @author Daniele Ricci
 */
public class DummyPushService implements IPushService {

    public DummyPushService(Context context) {
    }

    @Override
    public void register(IPushListener listener, String senderId) {
    }

    @Override
    public void unregister(IPushListener listener) {
    }

    @Override
    public void retry() {
    }

    @Override
    public boolean isRegistered() {
        return false;
    }

    @Override
    public boolean isServiceAvailable() {
        return false;
    }

    @Override
    public void setRegisteredOnServer(boolean flag) {
    }

    @Override
    public boolean isRegisteredOnServer() {
        return false;
    }

    @Override
    public long getRegisterOnServerLifespan() {
        return 0;
    }

    @Override
    public void setRegisterOnServerLifespan(long lifespan) {
    }

    @Override
    public String getRegistrationId() {
        return null;
    }

}

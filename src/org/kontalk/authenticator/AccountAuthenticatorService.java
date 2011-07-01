package org.kontalk.authenticator;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class AccountAuthenticatorService extends Service {
    private Authenticator mAuthenticator;

    @Override
    public void onCreate() {
        mAuthenticator = new Authenticator(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent.getAction().equals(android.accounts.AccountManager
                .ACTION_AUTHENTICATOR_INTENT))
            return mAuthenticator.getIBinder();
        return null;
    }

}

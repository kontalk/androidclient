package org.kontalk.sync;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;


public class ContactsSyncAdapterService extends Service {
    //private static final String TAG = ContactsSyncAdapterService.class.getSimpleName();

    private SyncAdapter mSyncAdapter;

    @Override
    public void onCreate() {
        mSyncAdapter = new SyncAdapter(this, true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent.getAction().equals("android.content.SyncAdapter"))
            return mSyncAdapter.getSyncAdapterBinder();

        return null;
    }

}

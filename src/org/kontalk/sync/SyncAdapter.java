/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.sync;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.provider.UsersProvider;
import org.kontalk.util.Preferences;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


/**
 * The Sync Adapter.
 * @author Daniele Ricci
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = SyncAdapter.class.getSimpleName();

    /** How many seconds between sync operations. */
    private static final int MAX_SYNC_DELAY = 600;

    /** Broadcast action: sync has started. */
    public static final String ACTION_SYNC_START = "org.kontalk.sync.action.START";
    /** Broadcast action: sync has finished. */
    public static final String ACTION_SYNC_FINISH = "org.kontalk.sync.action.FINISH";

    private final Context mContext;
    private final LocalBroadcastManager mBroadcastManager;
    private Syncer mSyncer;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mBroadcastManager = LocalBroadcastManager.getInstance(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {

        try {
            // broadcast sync start
            mBroadcastManager.sendBroadcast(new Intent(ACTION_SYNC_START));

            final long startTime = System.currentTimeMillis();
            boolean force = extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);

            // do not start if offline
            if (Preferences.getOfflineMode(mContext)) {
                Log.d(TAG, "not requesting sync - offline mode");
                return;
            }

            if (!force) {
                long lastSync = Preferences.getLastSyncTimestamp(mContext);
                long diff = (System.currentTimeMillis() - lastSync) / 1000;
                if (lastSync >= 0 && diff < MAX_SYNC_DELAY) {
                    Log.d(TAG, "not starting sync - throttling");
                    // TEST do not delay - syncResult.delayUntil = (long) diff;
                    return;
                }
            }

            Log.i(TAG, "sync started (authority=" + authority + ")");
            // avoid other syncs to get scheduled in the meanwhile
            Preferences.setLastSyncTimestamp(mContext, System.currentTimeMillis());

            ContentProviderClient usersProvider = getContext().getContentResolver()
                .acquireContentProviderClient(UsersProvider.AUTHORITY);

            try {
                mSyncer = new Syncer(mContext);
                mSyncer.performSync(mContext, account, authority,
                    provider, usersProvider, syncResult);
            }
            catch (OperationCanceledException e) {
                Log.w(TAG, "sync canceled!", e);
            }
            finally {
                usersProvider.release();
                long endTime = System.currentTimeMillis();
                Preferences.setLastSyncTimestamp(mContext, endTime);
                Log.d(TAG, String.format("sync took %.5f seconds", ((float)(endTime - startTime)) / 1000));
            }
        }
        finally {
            // broadcast sync finish
            mBroadcastManager.sendBroadcast(new Intent(ACTION_SYNC_FINISH));
        }
    }

    @Override
    public void onSyncCanceled() {
        super.onSyncCanceled();
        mSyncer.onSyncCanceled();
    }

    /**
     * Requests a manual sync to the system.
     * @return true if the sync has been actually requested to the system.
     */
    public static boolean requestSync(Context context, boolean force) {
        if (!force) {
            long lastSync = Preferences.getLastSyncTimestamp(context);
            float diff = (System.currentTimeMillis() - lastSync) / 1000;
            if (lastSync >= 0 && diff < MAX_SYNC_DELAY) {
                Log.d(TAG, "not requesting sync - throttling");
                return false;
            }
        }

        // do not start if offline
        if (Preferences.getOfflineMode(context)) {
            Log.d(TAG, "not requesting sync - offline mode");
            return false;
        }

        Account acc = Authenticator.getDefaultAccount(context);
        Bundle extra = new Bundle();
        // override auto-sync and background data settings
        extra.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        // put our sync ahead of other sync operations :)
        extra.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(acc, ContactsContract.AUTHORITY, extra);
        return true;
    }

    public static boolean isPending(Context context) {
        Account acc = Authenticator.getDefaultAccount(context);
        return ContentResolver.isSyncPending(acc, ContactsContract.AUTHORITY);
    }

    public static boolean isActive(Context context) {
        Account acc = Authenticator.getDefaultAccount(context);
        return ContentResolver.isSyncActive(acc, ContactsContract.AUTHORITY);
    }

}

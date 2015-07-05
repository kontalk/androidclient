/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.adapter.ContactsListAdapter;
import org.kontalk.ui.view.ContactPickerListener;
import org.kontalk.ui.view.ContactsListItem;
import org.kontalk.util.RunnableBroadcastReceiver;

import android.app.Activity;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;


/** Contacts list selection fragment. */
public class ContactsListFragment extends ListFragment
        implements ContactsListAdapter.OnContentChangedListener,
        SwipeRefreshLayout.OnRefreshListener {

    private Cursor mCursor;
    private ContactsListAdapter mListAdapter;
    private SwipeRefreshLayout mRefresher;

    private LocalBroadcastManager mBroadcastManager;

    private RunnableBroadcastReceiver mSyncMonitor;
    private Handler mHandler;

    private final RunnableBroadcastReceiver.ActionRunnable mPostSyncAction =
            new RunnableBroadcastReceiver.ActionRunnable() {
        public void run(String action) {
            if (SyncAdapter.ACTION_SYNC_START.equals(action)) {
                setSyncing(true);
            }
            else if (SyncAdapter.ACTION_SYNC_FINISH.equals(action)) {
                startQuery();
                setSyncing(false);
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.contacts_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mRefresher = (SwipeRefreshLayout) view.findViewById(R.id.refresher);
        mRefresher.setOnRefreshListener(this);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity parent = getActivity();

        mListAdapter = new ContactsListAdapter(parent, getListView());
        mListAdapter.setOnContentChangedListener(this);
        setListAdapter(mListAdapter);

        mHandler = new Handler();
        mBroadcastManager = LocalBroadcastManager.getInstance(parent);

        // retain current sync state to hide the refresh button and start indeterminate progress
        registerSyncReceiver();
        if (SyncAdapter.isActive(parent)) {
            // workaround for https://code.google.com/p/android/issues/detail?id=77712
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mRefresher.setRefreshing(true);
                }
            });
            ((ContactsSyncActivity) getActivity()).setSyncing(true);
        }
    }

    private void setSyncing(boolean syncing) {
        mRefresher.setRefreshing(syncing);
        ((ContactsSyncActivity) getActivity()).setSyncing(syncing);
    }

    @Override
    public void onStop() {
        super.onStop();

        mListAdapter.changeCursor(null);
        try {
            // make sure the cursor is really closed
            mCursor.close();
        }
        catch (Exception e) {
            // ignored
        }

        // cancel sync monitor
        if (mSyncMonitor != null) {
            mBroadcastManager.unregisterReceiver(mSyncMonitor);
            mSyncMonitor = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        startQuery();
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        ContactPickerListener parent = (ContactPickerListener) getActivity();

        if (parent != null)
            parent.onContactSelected(this, ((ContactsListItem) v).getContact());
    }

    @Override
    public void onRefresh() {
        ((ContactsSyncActivity) getActivity()).startSync(true);
    }

    private void registerSyncReceiver() {
        // register sync monitor
        if (mSyncMonitor == null) {
            mSyncMonitor = new RunnableBroadcastReceiver(mPostSyncAction, mHandler);
            IntentFilter filter = new IntentFilter
                            (SyncAdapter.ACTION_SYNC_FINISH);
            filter.addAction(SyncAdapter.ACTION_SYNC_START);
            mBroadcastManager.registerReceiver(mSyncMonitor, filter);
        }
    }

    public void startQuery() {
        mCursor = Contact.queryContacts(getActivity());
        mListAdapter.changeCursor(mCursor);
    }

    @Override
    public void onContentChanged(ContactsListAdapter adapter) {
        startQuery();
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

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

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.akalipetis.fragment.ActionModeListFragment;
import com.akalipetis.fragment.MultiChoiceModeListener;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.Toast;

import lb.library.PinnedHeaderListView;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.message.TextComponent;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.adapter.ContactsListAdapter;
import org.kontalk.ui.view.ContactPickerListener;
import org.kontalk.ui.view.ContactsListItem;
import org.kontalk.util.RunnableBroadcastReceiver;
import org.kontalk.util.SystemUtils;


/** Contacts list selection fragment. */
public class ContactsListFragment extends ActionModeListFragment implements
        ContactsListAdapter.OnContentChangedListener,
        SwipeRefreshLayout.OnRefreshListener,
        ContactsSyncer, MultiChoiceModeListener {

    private Cursor mCursor;
    private ContactsListAdapter mListAdapter;
    private SwipeRefreshLayout mRefresher;

    private LocalBroadcastManager mBroadcastManager;

    private RunnableBroadcastReceiver mSyncMonitor;
    private Handler mHandler;

    private MenuItem mSyncButton;

    private int mCheckedItemCount;

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
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.contacts_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        PinnedHeaderListView list = (PinnedHeaderListView) getListView();

        View pinnedHeaderView = LayoutInflater.from(view.getContext())
            .inflate(R.layout.pinned_header_listview_side_header, list, false);
        list.setPinnedHeaderView(pinnedHeaderView);
        list.setEmptyView(view.findViewById(android.R.id.empty));

        mRefresher = (SwipeRefreshLayout) view.findViewById(R.id.refresher);
        mRefresher.setOnRefreshListener(this);

        // http://nlopez.io/swiperefreshlayout-with-listview-done-right/
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                ((PinnedHeaderListView) view).configureHeaderView(firstVisibleItem);
                int topRowVerticalPosition = (view.getChildCount() == 0) ?
                            0 : view.getChildAt(0).getTop();
                mRefresher.setEnabled(firstVisibleItem == 0 && topRowVerticalPosition >= 0);
            }
        });
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Activity parent = getActivity();

        mListAdapter = new ContactsListAdapter(parent, getListView());
        mListAdapter.setPinnedHeader(parent);
        ((PinnedHeaderListView) getListView()).setEnableHeaderTransparencyChanges(true);

        mListAdapter.setOnContentChangedListener(this);
        setListAdapter(mListAdapter);
        setMultiChoiceModeListener(this);

        mHandler = new Handler();
        mBroadcastManager = LocalBroadcastManager.getInstance(parent);

        // retain current sync state to hide the refresh button and start indeterminate progress
        registerSyncReceiver();
        if (SyncAdapter.isActive(parent)) {
            // workaround for https://code.google.com/p/android/issues/detail?id=77712
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setSyncing(true);
                }
            });
        }
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
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.contacts_list_menu, menu);
        mSyncButton = menu.findItem(R.id.menu_refresh);

        Context ctx = getActivity();
        if (ctx != null)
            mSyncButton.setVisible(!SyncAdapter.isActive(getActivity()));

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (isActionModeActive())
            return true;

        switch (item.getItemId()) {
            case R.id.menu_refresh:
                startSync(true);
                return true;

            case R.id.menu_invite:
                startInvite();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public boolean isActionModeActive() {
        return mCheckedItemCount > 0;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        if (checked)
            mCheckedItemCount++;
        else
            mCheckedItemCount--;
        mode.setTitle(getResources()
            .getQuantityString(R.plurals.context_selected,
                mCheckedItemCount, mCheckedItemCount));
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.menu_compose) {
            // using clone because listview returns its original copy
            openSelectedContacts(SystemUtils
                .cloneSparseBooleanArray(getListView().getCheckedItemPositions()));
            mode.finish();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.contacts_list_ctx, menu);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mCheckedItemCount = 0;
        getListView().clearChoices();
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    private void openSelectedContacts(SparseBooleanArray checked) {
        List<Contact> selected = new ArrayList<>();
        for (int i = 0, c = mListAdapter.getCount(); i < c; ++i) {
            if (checked.get(i)) {
                Cursor cursor = (Cursor) mListAdapter.getItem(i);
                Contact contact = Contact.fromUsersCursor(getContext(), cursor);
                selected.add(contact);
            }
        }
        ContactPickerListener parent = (ContactPickerListener) getActivity();
        if (parent != null) {
            if (selected.size() == 1)
                parent.onContactSelected(this, selected.get(0));
            else
                parent.onContactsSelected(this, selected);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        int choiceMode = l.getChoiceMode();
        if (choiceMode == ListView.CHOICE_MODE_NONE || choiceMode == ListView.CHOICE_MODE_SINGLE) {
            ContactPickerListener parent = (ContactPickerListener) getActivity();
            if (parent != null) {
                parent.onContactSelected(this, ((ContactsListItem) v).getContact());
            }
        }
        else {
            super.onListItemClick(l, v, position, id);
        }

    }

    @Override
    public void startSync(boolean errorWarning) {
        Activity activity = getActivity();
        if (SystemUtils.isNetworkConnectionAvailable(activity)) {
            if (SyncAdapter.requestSync(activity, true))
                setSyncing(true);
        }
        else if (errorWarning) {
            Toast.makeText(activity, R.string.err_sync_nonetwork, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void setSyncing(boolean syncing) {
        mRefresher.setRefreshing(syncing);
        if (mSyncButton != null)
            mSyncButton.setVisible(!syncing);
    }

    @Override
    public void onRefresh() {
        startSync(true);
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
        final Context context = getContext();
        if (context != null) {
            mCursor = Contact.queryContacts(context);
            mListAdapter.changeCursor(mCursor);
        }
    }

    @Override
    public void onContentChanged(ContactsListAdapter adapter) {
        startQuery();
    }

    private void startInvite() {
        Context ctx = getActivity();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType(TextComponent.MIME_TYPE);
        shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.text_invite_message));

        List<ResolveInfo> resInfo = ctx.getPackageManager().queryIntentActivities(shareIntent, 0);
        // having size=1 means that we are the only handlers
        if (resInfo != null && resInfo.size() > 1) {
            List<Intent> targets = new ArrayList<>();

            for (ResolveInfo resolveInfo : resInfo) {
                String packageName = resolveInfo.activityInfo.packageName;

                if (!ctx.getPackageName().equals(packageName)) {
                    // copy intent and add resolved info
                    Intent targetShareIntent = new Intent(shareIntent);
                    targetShareIntent
                        .setPackage(packageName)
                        .setComponent(new ComponentName(
                            packageName, resolveInfo.activityInfo.name))
                        .putExtra("org.kontalk.invite.label", resolveInfo.loadLabel(ctx.getPackageManager()));

                    targets.add(targetShareIntent);
                }
            }

            // initial intents are added before of the main intent, so we remove the last one here
            Intent chooser = Intent.createChooser(targets.remove(targets.size() - 1), getString(R.string.menu_invite));
            Collections.sort(targets, new DisplayNameComparator());
            // remove custom extras
            for (Intent intent : targets)
                intent.removeExtra("org.kontalk.invite.label");

            Parcelable[] extraIntents = new Parcelable[targets.size()];
            targets.toArray(extraIntents);
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents);

            startActivity(chooser);
        }

        else {
            // no activity to handle invitation
            Toast.makeText(ctx, R.string.warn_invite_no_app,
                Toast.LENGTH_SHORT).show();
        }
    }

    public static class DisplayNameComparator implements
        Comparator<Intent> {
        public DisplayNameComparator() {
            mCollator.setStrength(Collator.PRIMARY);
        }

        public final int compare(Intent a, Intent b) {
            CharSequence sa = a.getCharSequenceExtra("org.kontalk.invite.label");
            if (sa == null)
                sa = a.getComponent().getClassName();
            CharSequence sb = b.getCharSequenceExtra("org.kontalk.invite.label");
            if (sb == null)
                sb = b.getComponent().getClassName();

            return mCollator.compare(sa.toString(), sb.toString());
        }

        private final Collator mCollator = Collator.getInstance();
    }

}

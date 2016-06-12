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

import java.util.LinkedList;
import java.util.List;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import com.akalipetis.fragment.ActionModeListFragment;
import com.akalipetis.fragment.MultiChoiceModeListener;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.view.ActionMode;
import android.text.TextUtils;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.KontalkGroupManager.KontalkGroup;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.MessagesProviderUtils;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.ui.view.ContactsListItem;
import org.kontalk.util.SystemUtils;


/**
 * Group information fragment
 * @author Daniele Ricci
 */
public class GroupInfoFragment extends ActionModeListFragment
        implements Contact.ContactChangeListener, MultiChoiceModeListener {

    private TextView mTitle;
    private Button mSetSubject;
    private Button mLeave;
    private MenuItem mRemoveMenu;
    private MenuItem mComposeMenu;

    private GroupMembersAdapter mMembersAdapter;

    private Conversation mConversation;

    private int mCheckedItemCount;

    public static GroupInfoFragment newInstance(long threadId) {
        GroupInfoFragment f = new GroupInfoFragment();
        Bundle data = new Bundle();
        data.putLong("conversation", threadId);
        f.setArguments(data);
        return f;
    }

    private void loadConversation(long threadId) {
        mConversation = Conversation.loadFromId(getContext(), threadId);
        String subject = mConversation.getGroupSubject();
        mTitle.setText(TextUtils.isEmpty(subject) ?
            getString(R.string.group_untitled) : subject);

        String selfJid = Authenticator.getSelfJID(getContext());
        boolean isOwner = KontalkGroup.checkOwnership(mConversation.getGroupJid(), selfJid);
        boolean isMember = mConversation.getGroupMembership() == Groups.MEMBERSHIP_MEMBER;
        mSetSubject.setEnabled(isOwner && isMember);
        mLeave.setEnabled(isMember);

        // load members
        // TODO sort
        String[] members = getGroupMembers();
        mMembersAdapter.clear();
        for (String jid : members) {
            mMembersAdapter.add(jid);
        }

        mMembersAdapter.notifyDataSetChanged();
        updateUI();
    }

    private void updateUI() {
        if (mRemoveMenu != null) {
            String selfJid = Authenticator.getSelfJID(getContext());
            boolean isOwner = KontalkGroup.checkOwnership(mConversation.getGroupJid(), selfJid);
            mRemoveMenu.setVisible(isOwner);
            MenuItemCompat.setShowAsAction(mRemoveMenu, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            MenuItemCompat.setShowAsAction(mComposeMenu, isOwner ?
                MenuItemCompat.SHOW_AS_ACTION_IF_ROOM : MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
        }
    }

    private String[] getGroupMembers() {
        String[] members = mConversation.getGroupPeers();
        String[] added = MessagesProviderUtils.getGroupMembers(getContext(),
            mConversation.getGroupJid(), Groups.MEMBER_PENDING_ADDED);
        if (added.length > 0)
            return SystemUtils.concatenate(members, added);
        return members;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMembersAdapter = new GroupMembersAdapter(getContext());
        setMultiChoiceModeListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.group_info, container, false);

        mTitle = (TextView) view.findViewById(R.id.title);

        mSetSubject = (Button) view.findViewById(R.id.btn_change_title);
        mSetSubject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new MaterialDialog.Builder(getContext())
                    .title(R.string.title_group_subject)
                    .positiveText(android.R.string.ok)
                    .negativeText(android.R.string.cancel)
                    .input(null, mConversation.getGroupSubject(), true, new MaterialDialog.InputCallback() {
                        @Override
                        public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                            setGroupSubject(!TextUtils.isEmpty(input) ? input.toString() : null);
                        }
                    })
                    .inputRange(0, Groups.GROUP_SUBJECT_MAX_LENGTH)
                    .show();
            }
        });
        mLeave = (Button) view.findViewById(R.id.btn_leave);
        mLeave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialogWrapper.Builder(getActivity())
                    .setMessage(R.string.confirm_will_leave_group)
                    .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // leave group
                                mConversation.leaveGroup();
                            }
                        })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            }
        });

        return view;
    }

    private void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
        // reload conversation
        reload();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return isActionModeActive() || super.onOptionsItemSelected(item);
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
        switch (item.getItemId()) {
            case R.id.menu_remove:
                // using clone because listview returns its original copy
                removeSelectedUsers(SystemUtils
                    .cloneSparseBooleanArray(getListView().getCheckedItemPositions()));
                mode.finish();
                return true;
            case R.id.menu_compose:
                // using clone because listview returns its original copy
                composeSelectedUsers(SystemUtils
                    .cloneSparseBooleanArray(getListView().getCheckedItemPositions()));
                mode.finish();
                return true;
        }
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.group_info_ctx, menu);
        mRemoveMenu = menu.findItem(R.id.menu_remove);
        mComposeMenu = menu.findItem(R.id.menu_compose);
        updateUI();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mCheckedItemCount = 0;
        getListView().clearChoices();
        mMembersAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    private void removeSelectedUsers(final SparseBooleanArray checked) {
        List<String> users = new LinkedList<>();
        for (int i = 0, c = mMembersAdapter.getCount(); i < c; ++i) {
            if (checked.get(i))
                users.add((String) mMembersAdapter.getItem(i));
        }
        mConversation.removeUsers(users.toArray(new String[users.size()]));
        reload();
    }

    private void composeSelectedUsers(final SparseBooleanArray checked) {
        List<String> users = new LinkedList<>();
        for (int i = 0, c = mMembersAdapter.getCount(); i < c; ++i) {
            if (checked.get(i))
                users.add((String) mMembersAdapter.getItem(i));
        }
        // TODO create group with users
    }

    @Override
    public void onContactInvalidated(String userId) {
        Activity context = getActivity();
        if (context != null) {
            context.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // just reload
                    reload();
                }
            });
        }
    }

    private void reload() {
        // reload conversation data
        Bundle data = getArguments();
        long threadId = data.getLong("conversation");
        loadConversation(threadId);
    }

    @Override
    public void onResume() {
        super.onResume();
        setListAdapter(mMembersAdapter);
        reload();
    }

    @Override
    public void onPause() {
        super.onPause();
        setListAdapter(null);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (!(context instanceof GroupInfoParent))
            throw new IllegalArgumentException("parent activity must implement " +
                GroupInfoParent.class.getSimpleName());
    }

    private static final class GroupMembersAdapter extends BaseAdapter {
        private final Context mContext;
        private List<String> mMembers;

        public GroupMembersAdapter(Context context) {
            mContext = context;
            mMembers = new LinkedList<>();
        }

        public void clear() {
            mMembers.clear();
        }

        public void add(String jid) {
            mMembers.add(jid);
        }

        @Override
        public int getCount() {
            return mMembers.size();
        }

        @Override
        public Object getItem(int position) {
            return mMembers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            if (convertView == null) {
                v = newView(parent);
            } else {
                v = convertView;
            }
            bindView(v, position);
            return v;
        }

        private View newView(ViewGroup parent) {
            ContactsListItem view = (ContactsListItem) LayoutInflater.from(mContext)
                .inflate(R.layout.contacts_list_item, parent, false);
            // FIXME this is really a terrible hack
            view.removeView(view.findViewById(R.id.header_container));
            return view;
        }

        private void bindView(View v, int position) {
            ContactsListItem view = (ContactsListItem) v;
            String jid = (String) getItem(position);
            Contact contact = Contact.findByUserId(mContext, jid);
            view.bind(mContext, contact);
        }

    }

    public interface GroupInfoParent {

        void dismiss();

    }

}

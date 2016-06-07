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

import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
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
public class GroupInfoFragment extends Fragment {

    private TextView mTitle;
    private ListView mMembers;
    private Button mSetSubject;

    private GroupMembersAdapter mMembersAdapter;

    private Conversation mConversation;

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
        mSetSubject.setEnabled(isOwner);

        // load members
        // TODO sort
        String[] members = getGroupMembers();
        for (String jid : members) {
            mMembersAdapter.add(jid);
        }

        mMembersAdapter.notifyDataSetChanged();
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
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.group_info, container, false);

        mTitle = (TextView) view.findViewById(R.id.title);
        mMembers = (ListView) view.findViewById(R.id.members_list);
        mMembers.setAdapter(mMembersAdapter);

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
        view.findViewById(R.id.btn_leave).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialogWrapper.Builder builder = new AlertDialogWrapper.Builder(getActivity());
                builder.setMessage(R.string.confirm_will_delete_thread);
                builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // TODO this needs to be delegated to the compose fragment or activity
                            // create an interface that makes use of either onActivityResult or some other method
                            // to deliver results to the parent activity/fragment/whatever.
                        }
                    });
                builder.setNegativeButton(android.R.string.cancel, null);
                builder.create().show();

            }
        });

        return view;
    }

    private void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
        // reload conversation
        reload();
    }

    private void dismiss() {
        GroupInfoParent parent = (GroupInfoParent) getActivity();
        if (parent != null)
            parent.dismiss();
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
        reload();
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

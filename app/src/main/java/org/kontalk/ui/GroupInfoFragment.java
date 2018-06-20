/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.KontalkGroupManager.KontalkGroup;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyUsers;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.view.ContactsListItem;
import org.kontalk.util.SystemUtils;


/**
 * Group information fragment
 * FIXME this class is too tied to the concept of "Kontalk group"
 * @author Daniele Ricci
 */
public class GroupInfoFragment extends ListFragment
        implements Contact.ContactChangeListener, AbsListView.MultiChoiceModeListener {

    private TextView mTitle;
    private Button mSetSubject;
    private Button mLeave;
    private Button mIgnoreAll;
    private MenuItem mAddMenu;
    private MenuItem mRemoveMenu;
    private MenuItem mChatMenu;
    private MenuItem mReaddMenu;

    GroupMembersAdapter mMembersAdapter;

    Conversation mConversation;

    private int mCheckedItemCount;

    // created on demand
    private BroadcastReceiver mRosterReceiver;
    private LocalBroadcastManager mLocalBroadcastManager;

    public static GroupInfoFragment newInstance(long threadId) {
        GroupInfoFragment f = new GroupInfoFragment();
        Bundle data = new Bundle();
        data.putLong("conversation", threadId);
        f.setArguments(data);
        return f;
    }

    private void loadConversation(long threadId) {
        mConversation = Conversation.loadFromId(getContext(), threadId);
        mMembersAdapter.setGroupJid(mConversation.getGroupJid());
        String subject = mConversation.getGroupSubject();
        mTitle.setText(TextUtils.isEmpty(subject) ?
            getString(R.string.group_untitled) : subject);

        String selfJid = Authenticator.getSelfJID(getContext());
        boolean isOwner = KontalkGroup.checkOwnership(mConversation.getGroupJid(), selfJid);
        boolean isMember = mConversation.getGroupMembership() == Groups.MEMBERSHIP_MEMBER;
        mSetSubject.setEnabled(isOwner && isMember);
        mLeave.setEnabled(isMember);

        if (mRosterReceiver == null) {
            // listen to roster entry status requests
            mRosterReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                    boolean isSubscribed = intent
                        .getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_FROM, false) &&
                        intent.getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_TO, false);
                    mMembersAdapter.setSubscribed(jid, isSubscribed);
                }
            };

            IntentFilter filter = new IntentFilter(MessageCenterService.ACTION_ROSTER_STATUS);
            mLocalBroadcastManager.registerReceiver(mRosterReceiver, filter);
        }

        // load members
        boolean showIgnoreAll = false;
        String[] members = getGroupMembers();
        mMembersAdapter.clear();
        for (String jid : members) {
            Contact c = Contact.findByUserId(getContext(), jid);
            if (c.isKeyChanged() || c.getTrustedLevel() == MyUsers.Keys.TRUST_UNKNOWN)
                showIgnoreAll = true;
            boolean owner = KontalkGroup.checkOwnership(mConversation.getGroupJid(), jid);
            boolean isSelfJid = jid.equalsIgnoreCase(selfJid);
            mMembersAdapter.add(c, owner, isSelfJid);
            if (!isSelfJid) {
                // request roster entry status
                MessageCenterService.requestRosterEntryStatus(getContext(), jid);
            }
        }

        mIgnoreAll.setVisibility(showIgnoreAll ? View.VISIBLE : View.GONE);

        // notifyDataSetChanged() will be called after receiving roster status.
        // It will prevent the blocked/unsubscribed icon flickering.
        // Roster status is just a query to the roster database so it will work
        // also when disconnected.

        updateUI();
    }

    private void updateUI() {
        String selfJid = Authenticator.getSelfJID(getContext());
        boolean isOwner = KontalkGroup.checkOwnership(mConversation.getGroupJid(), selfJid);
        if (mRemoveMenu != null) {
            mRemoveMenu.setVisible(isOwner);
        }
        if (mAddMenu != null) {
            mAddMenu.setVisible(isOwner);
        }
        if (mReaddMenu != null) {
            mReaddMenu.setVisible(isOwner);
        }
    }

    private String[] getGroupMembers() {
        String[] members = mConversation.getGroupPeers();
        String[] added = MessagesProviderClient.getGroupMembers(getContext(),
            mConversation.getGroupJid(), Groups.MEMBER_PENDING_ADDED);
        if (added.length > 0)
            members = SystemUtils.concatenate(members, added);
        // if we are in the group, add ourself to the list
        if (mConversation.getGroupMembership() == Groups.MEMBERSHIP_MEMBER) {
            String selfJid = Authenticator.getSelfJID(getContext());
            members = SystemUtils.concatenate(members, selfJid);
        }
        return members;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mMembersAdapter = new GroupMembersAdapter(getContext(), null);
        setListAdapter(mMembersAdapter);

        ListView list = getListView();
        list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        list.setMultiChoiceModeListener(this);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.group_info, container, false);

        mTitle = view.findViewById(R.id.title);

        mSetSubject = view.findViewById(R.id.btn_change_title);
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
        mLeave = view.findViewById(R.id.btn_leave);
        mLeave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmLeave();
            }
        });
        mIgnoreAll = view.findViewById(R.id.btn_ignore_all);
        mIgnoreAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new MaterialDialog.Builder(getContext())
                    .title(R.string.title_ignore_all_identities)
                    .content(R.string.msg_ignore_all_identities)
                    .positiveText(android.R.string.ok)
                    .positiveColorRes(R.color.button_danger)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            mMembersAdapter.ignoreAll();
                            reload();
                        }
                    })
                    .negativeText(android.R.string.cancel)
                    .show();
            }
        });

        return view;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        updateUI();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_info_menu, menu);
        mAddMenu = menu.findItem(R.id.menu_invite);
    }

    void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
        reload();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // action mode is active - no processing
        if (isActionModeActive())
            return true;

        switch (item.getItemId()) {
            case R.id.menu_invite:
                Activity parent = getActivity();
                if (parent != null) {
                    parent.setResult(GroupInfoActivity.RESULT_ADD_USERS, null);
                    parent.finish();
                }
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
        mode.invalidate();
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
            case R.id.menu_add_again:
                // using clone because listview returns its original copy
                readdUser(SystemUtils
                    .cloneSparseBooleanArray(getListView().getCheckedItemPositions()));
                return true;
            case R.id.menu_chat:
                openChat(getCheckedItem().contact.getJID());
                return true;
        }
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.group_info_ctx, menu);
        mRemoveMenu = menu.findItem(R.id.menu_remove);
        mChatMenu = menu.findItem(R.id.menu_chat);
        mReaddMenu = menu.findItem(R.id.menu_add_again);
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
        mChatMenu.setVisible(mCheckedItemCount == 1);
        return true;
    }

    private GroupMembersAdapter.GroupMember getCheckedItem() {
        if (mCheckedItemCount != 1)
            throw new IllegalStateException("checked items count must be exactly 1");

        return (GroupMembersAdapter.GroupMember) getListView()
            .getItemAtPosition(getCheckedItemPosition());
    }

    private int getCheckedItemPosition() {
        SparseBooleanArray checked = getListView().getCheckedItemPositions();
        return checked.keyAt(checked.indexOfValue(true));
    }

    private void removeSelectedUsers(final SparseBooleanArray checked) {
        boolean removingSelf = false;
        List<String> users = new LinkedList<>();
        for (int i = 0, c = mMembersAdapter.getCount(); i < c; ++i) {
            if (checked.get(i)) {
                GroupMembersAdapter.GroupMember member =
                    (GroupMembersAdapter.GroupMember) mMembersAdapter.getItem(i);
                if (Authenticator.isSelfJID(getContext(), member.contact.getJID())) {
                    removingSelf = true;
                }
                else {
                    users.add(member.contact.getJID());
                }
            }
        }

        if (users.size() > 0) {
            mConversation.removeUsers(users.toArray(new String[users.size()]));
            reload();
        }

        if (removingSelf)
            confirmLeave();
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

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // open identity dialog
        // TODO one day this will be the contact info activity
        GroupMembersAdapter.GroupMember member =
            (GroupMembersAdapter.GroupMember) mMembersAdapter.getItem(position);
        showIdentityDialog(member.contact, member.subscribed);
    }

    private void showIdentityDialog(Contact c, boolean subscribed) {
        final String jid = c.getJID();
        final String dialogFingerprint;
        final String fingerprint;
        final boolean selfJid = Authenticator.isSelfJID(getContext(), jid);
        int titleResId = R.string.title_identity;
        String uid;

        PGPPublicKeyRing publicKey = Keyring.getPublicKey(getContext(), jid, MyUsers.Keys.TRUST_UNKNOWN);
        if (publicKey != null) {
            PGPPublicKey pk = PGP.getMasterKey(publicKey);
            String rawFingerprint = PGP.getFingerprint(pk);
            fingerprint = PGP.formatFingerprint(rawFingerprint);

            uid = PGP.getUserId(pk, XmppStringUtils.parseDomain(jid));
            dialogFingerprint = selfJid ? null : rawFingerprint;
        }
        else {
            // FIXME using another string
            fingerprint = getString(R.string.peer_unknown);
            uid = null;
            dialogFingerprint = null;
        }

        if (Authenticator.isSelfJID(getContext(), jid)) {
            titleResId = R.string.title_identity_self;
        }

        SpannableStringBuilder text = new SpannableStringBuilder();

        if (c.getName() != null && c.getNumber() != null) {
            text.append(c.getName())
                .append('\n')
                .append(c.getNumber());
        }
        else {
            int start = text.length();
            text.append(uid != null ? uid : c.getJID());
            text.setSpan(SystemUtils.getTypefaceSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        text.append('\n')
            .append(getString(R.string.text_invitation2))
            .append('\n');

        int start = text.length();
        text.append(fingerprint);
        text.setSpan(SystemUtils.getTypefaceSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        int trustStringId;
        CharacterStyle[] trustSpans;

        if (subscribed) {
            int trustedLevel;
            if (c.isKeyChanged()) {
                // the key has changed and was not trusted yet
                trustedLevel = MyUsers.Keys.TRUST_UNKNOWN;
            }
            else {
                trustedLevel = c.getTrustedLevel();
            }

            switch (trustedLevel) {
                case MyUsers.Keys.TRUST_IGNORED:
                    trustStringId = R.string.trust_ignored;
                    trustSpans = new CharacterStyle[] {
                        SystemUtils.getTypefaceSpan(Typeface.BOLD),
                        SystemUtils.getColoredSpan(getContext(), R.color.button_danger)
                    };
                    break;

                case MyUsers.Keys.TRUST_VERIFIED:
                    trustStringId = R.string.trust_verified;
                    trustSpans = new CharacterStyle[] {
                        SystemUtils.getTypefaceSpan(Typeface.BOLD),
                        SystemUtils.getColoredSpan(getContext(), R.color.button_success)
                    };
                    break;

                case MyUsers.Keys.TRUST_UNKNOWN:
                default:
                    trustStringId = R.string.trust_unknown;
                    trustSpans = new CharacterStyle[] {
                        SystemUtils.getTypefaceSpan(Typeface.BOLD),
                        SystemUtils.getColoredSpan(getContext(), R.color.button_danger)
                    };
                    break;
            }
        }
        else {
            trustStringId = R.string.status_notsubscribed;
            trustSpans = new CharacterStyle[] {
                SystemUtils.getTypefaceSpan(Typeface.BOLD),
            };
        }

        text.append('\n').append(getString(R.string.status_label));
        start = text.length();
        text.append(getString(trustStringId));
        for (CharacterStyle span : trustSpans)
            text.setSpan(span, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getContext())
            .content(text)
            .title(titleResId);

        if (dialogFingerprint != null && subscribed) {
            builder.onAny(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    switch (which) {
                        case POSITIVE:
                            // trust the key
                            trustKey(jid, dialogFingerprint, MyUsers.Keys.TRUST_VERIFIED);
                            break;
                        case NEUTRAL:
                            // ignore the key
                            trustKey(jid, dialogFingerprint, MyUsers.Keys.TRUST_IGNORED);
                            break;
                        case NEGATIVE:
                            // untrust the key
                            trustKey(jid, dialogFingerprint, MyUsers.Keys.TRUST_UNKNOWN);
                            break;
                    }
                }
            })
            .positiveText(R.string.button_accept)
            .positiveColorRes(R.color.button_success)
            .neutralText(R.string.button_ignore)
            .negativeText(R.string.button_refuse)
            .negativeColorRes(R.color.button_danger);
        }
        else if (!selfJid) {
            builder.onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    openChat(jid);
                }
            })
            .positiveText(R.string.button_private_chat);
        }

        builder.show();
    }

    private void readdUser(final SparseBooleanArray checked) {
        List<String> users = new LinkedList<>();
        for (int i = 0, c = mMembersAdapter.getCount(); i < c; ++i) {
            if (checked.get(i)) {
                GroupMembersAdapter.GroupMember member =
                    (GroupMembersAdapter.GroupMember) mMembersAdapter.getItem(i);
                if (!Authenticator.isSelfJID(getContext(), member.contact.getJID())) {
                    users.add(member.contact.getJID());
                }
            }
        }

        if (users.size() > 0) {
            mConversation.addUsers(users.toArray(new String[users.size()]));
        }

        getActivity().finish();
    }

    void openChat(String jid) {
        Intent i = new Intent();
        i.setData(MyMessages.Threads.getUri(jid));
        Activity parent = getActivity();
        parent.setResult(GroupInfoActivity.RESULT_PRIVATE_CHAT, i);
        parent.finish();
    }

    void trustKey(String jid, String fingerprint, int trustLevel) {
        Kontalk.get().getMessagesController()
            .setTrustLevelAndRetryMessages(getContext(), jid, fingerprint, trustLevel);
        Contact.invalidate(jid);
        reload();
    }

    void confirmLeave() {
        new MaterialDialog.Builder(getContext())
            .content(R.string.confirm_will_leave_group)
            .positiveText(android.R.string.ok)
            .negativeText(android.R.string.cancel)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    // leave group
                    mConversation.leaveGroup();
                    getActivity().finish();
                }
            })
            .show();
    }

    void reload() {
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
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (mLocalBroadcastManager != null && mRosterReceiver != null) {
            mLocalBroadcastManager.unregisterReceiver(mRosterReceiver);
        }
        mRosterReceiver = null;
    }

    private static final class GroupMembersAdapter extends BaseAdapter {
        private static final class GroupMember {
            final Contact contact;
            boolean subscribed;

            GroupMember(Contact contact, boolean subscribed) {
                this.contact = contact;
                this.subscribed = subscribed;
            }
        }

        private final Context mContext;
        private final List<GroupMember> mMembers;
        private String mOwner;
        private String mGroupJid;

        GroupMembersAdapter(Context context, String groupJid) {
            mContext = context;
            mMembers = new LinkedList<>();
            mGroupJid = groupJid;
        }

        public void setGroupJid(String groupJid) {
            mGroupJid = groupJid;
        }

        public void clear() {
            mMembers.clear();
        }

        @Override
        public void notifyDataSetChanged() {
            Collections.sort(mMembers, new DisplayNameComparator());
            super.notifyDataSetChanged();
        }

        public void add(Contact contact, boolean isOwner, boolean subscribed) {
            mMembers.add(new GroupMember(contact, subscribed));
            if (isOwner)
                mOwner = contact.getJID();
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
            return LayoutInflater.from(mContext)
                .inflate(R.layout.contact_item, parent, false);
        }

        private void bindView(View v, int position) {
            ContactsListItem view = (ContactsListItem) v;
            GroupMember member = (GroupMember) getItem(position);
            Contact contact = member.contact;
            String prependStatus = null;
            CharacterStyle prependStyle = null;
            if (contact.getJID().equalsIgnoreCase(mOwner)) {
                prependStatus = mContext.getString(R.string.group_info_owner_member);
                prependStyle = new ForegroundColorSpan(Color.RED);
            }
            view.bind(mContext, contact, prependStatus, prependStyle,
                member.subscribed);
        }

        public void ignoreAll() {
            synchronized (mMembers) {
                for (GroupMember m : mMembers) {
                    Contact c = m.contact;
                    if (c.isKeyChanged() || c.getTrustedLevel() == MyUsers.Keys.TRUST_UNKNOWN) {
                        String fingerprint = c.getFingerprint();
                        Keyring.setTrustLevel(mContext, c.getJID(), fingerprint, MyUsers.Keys.TRUST_IGNORED);
                        Contact.invalidate(c.getJID());
                    }
                }
                MessageCenterService.retryMessagesTo(mContext, mGroupJid);
            }
        }

        public void setSubscribed(String jid, boolean isSubscribed) {
            synchronized (mMembers) {
                for (GroupMember m : mMembers) {
                    Contact c = m.contact;
                    if (c.getJID().equalsIgnoreCase(jid)) {
                        m.subscribed = isSubscribed;
                        break;
                    }
                }
            }
            // we don't need to sort, so call super directly
            super.notifyDataSetChanged();
        }

        static class DisplayNameComparator implements
            Comparator<GroupMember> {
            DisplayNameComparator() {
                mCollator.setStrength(Collator.PRIMARY);
            }

            public final int compare(GroupMember a, GroupMember b) {
                return mCollator.compare(a.contact.getDisplayName(),
                    b.contact.getDisplayName());
            }

            private final Collator mCollator = Collator.getInstance();
        }

    }

    public interface GroupInfoParent {

        void dismiss();

    }

}

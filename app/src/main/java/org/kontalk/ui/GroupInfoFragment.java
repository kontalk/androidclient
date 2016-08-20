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

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.akalipetis.fragment.ActionModeListFragment;
import com.akalipetis.fragment.MultiChoiceModeListener;

import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKey;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.view.ActionMode;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.KontalkGroupManager.KontalkGroup;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MessagesProviderUtils;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.provider.MyUsers;
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
    private Button mIgnoreAll;
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
        boolean showIgnoreAll = false;
        // TODO sort by display name
        String[] members = getGroupMembers();
        mMembersAdapter.clear();
        for (String jid : members) {
            Contact c = Contact.findByUserId(getContext(), jid);
            if (c.getTrustedLevel() == MyUsers.Keys.TRUST_UNKNOWN)
                showIgnoreAll = true;
            mMembersAdapter.add(c);
        }

        mIgnoreAll.setVisibility(showIgnoreAll ? View.VISIBLE : View.GONE);

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
        mMembersAdapter = new GroupMembersAdapter(getContext());
        setListAdapter(mMembersAdapter);
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
                new MaterialDialog.Builder(getContext())
                    .content(R.string.confirm_will_leave_group)
                    .positiveText(android.R.string.ok)
                    .negativeText(android.R.string.cancel)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            // leave group
                            mConversation.leaveGroup();
                            reload();
                        }
                    })
                    .show();
            }
        });
        mIgnoreAll = (Button) view.findViewById(R.id.btn_ignore_all);
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

    private void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
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
                users.add(((Contact) mMembersAdapter.getItem(i)).getJID());
        }
        mConversation.removeUsers(users.toArray(new String[users.size()]));
        reload();
    }

    private void composeSelectedUsers(final SparseBooleanArray checked) {
        List<String> users = new LinkedList<>();
        for (int i = 0, c = mMembersAdapter.getCount(); i < c; ++i) {
            if (checked.get(i))
                users.add(((Contact) mMembersAdapter.getItem(i)).getJID());
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

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        int choiceMode = l.getChoiceMode();
        if (choiceMode == ListView.CHOICE_MODE_NONE || choiceMode == ListView.CHOICE_MODE_SINGLE) {
            // open identity dialog
            // one day this will be the contact info activity
            showIdentityDialog(((ContactsListItem) v).getContact());
        }
        else {
            super.onListItemClick(l, v, position, id);
        }
    }

    private void showIdentityDialog(Contact c) {
        final String jid = c.getJID();
        final String dialogFingerprint;
        final String fingerprint;
        int titleResId = R.string.title_identity;
        String uid;

        PGPPublicKeyRing publicKey = Keyring.getPublicKey(getActivity(), jid, MyUsers.Keys.TRUST_UNKNOWN);
        if (publicKey != null) {
            PGPPublicKey pk = PGP.getMasterKey(publicKey);
            String rawFingerprint = PGP.getFingerprint(pk);
            fingerprint = PGP.formatFingerprint(rawFingerprint);

            uid = PGP.getUserId(pk, XmppStringUtils.parseDomain(jid));

            if (Authenticator.isSelfJID(getContext(), jid)) {
                rawFingerprint = null;
                titleResId = R.string.title_identity_self;
            }
            dialogFingerprint = rawFingerprint;
        }
        else {
            // FIXME using another string
            fingerprint = getString(R.string.peer_unknown);
            uid = null;
            dialogFingerprint = null;
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
        switch (c.getTrustedLevel()) {
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

        text.append('\n').append(getString(R.string.status_label));
        start = text.length();
        text.append(getString(trustStringId));
        for (CharacterStyle span : trustSpans)
            text.setSpan(span, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(getContext())
            .content(text)
            .title(titleResId);

        if (dialogFingerprint != null) {
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
                            // block user immediately
                            trustKey(jid, dialogFingerprint, MyUsers.Keys.TRUST_UNKNOWN);
                            // TODO setPrivacy(PRIVACY_BLOCK);
                            break;
                    }
                }
            })
            .positiveText(R.string.button_accept)
            .positiveColorRes(R.color.button_success)
            .neutralText(R.string.button_ignore)
            .negativeText(R.string.button_block)
            .negativeColorRes(R.color.button_danger);
        }

        builder.show();
    }

    private void trustKey(String jid, String fingerprint, int trustLevel) {
        Keyring.setTrustLevel(getContext(), jid, fingerprint, trustLevel);
        Contact.invalidate(jid);
        reload();
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
        private final List<Contact> mMembers;

        private GroupMembersAdapter(Context context) {
            mContext = context;
            mMembers = new LinkedList<>();
        }

        public void clear() {
            mMembers.clear();
        }

        public void add(Contact contact) {
            mMembers.add(contact);
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
            Contact contact = (Contact) getItem(position);
            view.bind(mContext, contact);
        }

        public void ignoreAll() {
            synchronized (mMembers) {
                for (Contact c : mMembers) {
                    if (c.getTrustedLevel() == MyUsers.Keys.TRUST_UNKNOWN) {
                        String fingerprint = c.getFingerprint();
                        Keyring.setTrustLevel(mContext, c.getJID(), fingerprint, MyUsers.Keys.TRUST_IGNORED);
                        Contact.invalidate(c.getJID());
                    }
                }
            }
        }

    }

    public interface GroupInfoParent {

        void dismiss();

    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.service.msgcenter.event.NoPresenceEvent;
import org.kontalk.service.msgcenter.event.PresenceEvent;
import org.kontalk.service.msgcenter.event.PresenceRequest;
import org.kontalk.service.msgcenter.event.PublicKeyEvent;
import org.kontalk.service.msgcenter.event.PublicKeyRequest;
import org.kontalk.service.msgcenter.event.RosterLoadedEvent;
import org.kontalk.service.msgcenter.event.SendChatStateRequest;
import org.kontalk.service.msgcenter.event.UserOfflineEvent;
import org.kontalk.service.msgcenter.event.UserOnlineEvent;
import org.kontalk.util.Preferences;
import org.kontalk.util.XMPPUtils;


/**
 * Composer fragment for group chats.
 * @author Daniele Ricci
 */
public class GroupMessageFragment extends AbstractComposeFragment {
    private static final String TAG = ComposeMessage.TAG;

    private static final int REQUEST_GROUP_INFO = REQUEST_FIRST_CHILD + 1;

    /** The virtual or real group JID. */
    private String mGroupJID;

    /** List of typing users. */
    private Set<String> mTypingUsers = new HashSet<>();

    /** Map of request IDs for public key requests. */
    private Map<String, String> mKeyRequestIds = new HashMap<>();

    private MenuItem mInviteGroupMenu;
    private MenuItem mSetGroupSubjectMenu;
    private MenuItem mGroupInfoMenu;
    private MenuItem mLeaveGroupMenu;

    @Override
    protected void updateUI() {
        super.updateUI();
        Context context;
        if (mInviteGroupMenu != null && (context = getContext()) != null) {
            boolean visible;
            String myUser = Authenticator.getSelfJID(context);

            // menu items requiring ownership and membership
            visible = KontalkGroupManager.KontalkGroup
                .checkOwnership(mConversation.getGroupJid(), myUser) &&
                mConversation.getGroupMembership() == Groups.MEMBERSHIP_MEMBER;
            mInviteGroupMenu.setVisible(visible);
            mInviteGroupMenu.setEnabled(visible);
            mSetGroupSubjectMenu.setVisible(visible);
            mSetGroupSubjectMenu.setEnabled(visible);

            // menu items requiring membership
            visible = mConversation.getGroupMembership() == Groups.MEMBERSHIP_MEMBER;
            mGroupInfoMenu.setVisible(visible);
            mGroupInfoMenu.setEnabled(visible);
            mLeaveGroupMenu.setVisible(visible);
            mLeaveGroupMenu.setEnabled(visible);

            if (visible) {
                if (mConversation != null) {
                    int count = mConversation.getGroupPeers().length + 1;
                    visible = count > 1;
                }
                else {
                    visible = false;
                }
            }

            if (!visible)
                tryHideAttachmentView();
        }
    }

    @Override
    protected void onInflateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_message_menu, menu);
        mInviteGroupMenu = menu.findItem(R.id.invite_group);
        mSetGroupSubjectMenu = menu.findItem(R.id.group_subject);
        mGroupInfoMenu = menu.findItem(R.id.group_info);
        mLeaveGroupMenu = menu.findItem(R.id.leave_group);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.group_info:
                viewGroupInfo();
                return true;
            case R.id.group_subject:
                changeGroupSubject();
                return true;
            case R.id.leave_group:
                leaveGroup();
                return true;
        }

        return false;
    }

    @Override
    protected void addUsers(String[] members) {
        Set<String> existingMembers = new HashSet<>();
        Collections.addAll(existingMembers, mConversation.getGroupPeers());

        // ensure no duplicates
        String selfJid = Authenticator.getSelfJID(getContext());
        Set<String> usersList = new HashSet<>();
        for (String member : members) {
            // exclude ourselves and do not add if already an existing member
            if (!member.equalsIgnoreCase(selfJid) && !existingMembers.contains(member))
                usersList.add(member);
        }

        if (usersList.size() > 0) {
            String[] users = usersList.toArray(new String[0]);
            mConversation.addUsers(users);
            // reload conversation
            ((ComposeMessageParent) getActivity()).loadConversation(getThreadId(), false);
        }
    }

    @Override
    protected void deleteConversation() {
        try {
            // this will also leave the group if true is passed
            mConversation.delete(false);
        }
        catch (SQLiteDiskIOException e) {
            Log.w(TAG, "error deleting thread");
            Toast.makeText(getActivity(), R.string.error_delete_thread,
                Toast.LENGTH_LONG).show();
        }
    }

    private void leaveGroup() {
        new MaterialDialog.Builder(getActivity())
            .content(R.string.confirm_will_leave_group)
            .positiveText(android.R.string.ok)
            .positiveColorRes(R.color.button_danger)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    // leave group
                    if (dialog.isPromptCheckBoxChecked()) {
                        mConversation.delete(true);
                        // manually close the conversation
                        closeConversation();
                    }
                    else {
                        mConversation.leaveGroup();
                        // reload conversation
                        if (isVisible())
                            startQuery();
                    }
                }
            })
            .checkBoxPromptRes(R.string.leave_group_delete_messages, false, null)
            .negativeText(android.R.string.cancel)
            .show();
    }

    private void changeGroupSubject() {
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

    void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
        // reload conversation
        ((ComposeMessageParent) getActivity()).loadConversation(getThreadId(), false);
    }

    private void requestPresence() {
        if (mConversation != null) {
            String[] users = mConversation.getGroupPeers();
            if (users != null) {
                for (String user : users) {
                    mServiceBus.post(new PresenceRequest(JidCreate.bareFromOrThrowUnchecked(user)));
                }
            }
        }
    }

    @Override
    protected String getDecodedPeer(CompositeMessage msg) {
        if (msg.getDirection() == MyMessages.Messages.DIRECTION_IN) {
            String userId = msg.getSender();
            Contact c = Contact.findByUserId(getContext(), userId);
            return c != null ? c.getNumber() : userId;
        }
        return null;
    }

    @Override
    protected String getDecodedName(CompositeMessage msg) {
        if (msg.getDirection() == MyMessages.Messages.DIRECTION_IN) {
            String userId = msg.getSender();
            Contact c = Contact.findByUserId(getContext(), userId);
            return c.getName();
        }
        return null;
    }

    @Override
    protected void loadConversationMetadata(Uri uri) {
        super.loadConversationMetadata(uri);
        if (mConversation != null) {
            mGroupJID = mConversation.getRecipient();
            mUserName = mGroupJID;
        }
    }

    /**
     * Since this is a group chat, it can be only opened from within the app.
     * No ACTION_VIEW intent ever will be delivered for this.
     */
    @Override
    protected void handleActionView(Uri uri) {
        throw new AssertionError("This shouldn't be called ever!");
    }

    /**
     * Used only during activity restore.
     */
    @Override
    protected boolean handleActionViewConversation(Uri uri, Bundle args) {
        mGroupJID = uri.getPathSegments().get(1);
        mConversation = Conversation.loadFromUserId(getActivity(), mGroupJID);
        // unlikely, but better safe than sorry
        if (mConversation == null) {
            Log.i(TAG, "conversation for " + mGroupJID + " not found - exiting");
            return false;
        }

        setThreadId(mConversation.getThreadId());
        mUserName = mGroupJID;
        return true;
    }

    @Override
    protected void onArgumentsProcessed() {
    }

    @Override
    protected void onConversationCreated() {
        // warning will be reloaded if necessary
        hideWarning();

        super.onConversationCreated();

        boolean sendEnabled;
        int membership = mConversation.getGroupMembership();
        switch (membership) {
            case Groups.MEMBERSHIP_PARTED:
                sendEnabled = false;
                break;
            case Groups.MEMBERSHIP_KICKED:
                sendEnabled = false;
                break;
            case Groups.MEMBERSHIP_OBSERVER: {
                int count = mConversation.getGroupPeers().length;
                sendEnabled = count > 1;
                break;
            }
            case Groups.MEMBERSHIP_MEMBER: {
                // +1 because we are not included in the members list
                int count = mConversation.getGroupPeers().length + 1;
                sendEnabled = count > 1;
                break;
            }
            default:
                // shouldn't happen
                throw new RuntimeException("Unknown membership status: " + membership);
        }

        // disable sending if necessary
        mComposer.setSendEnabled(sendEnabled);

        updateStatusText();
    }

    private void showKeyWarning() {
        Activity context = getActivity();
        if (context != null) {
            showWarning(context.getText(R.string.warning_public_key_group_unverified), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    viewGroupInfo();
                }
            }, WarningType.FATAL);
        }
    }

    private void requestPublicKey(Jid jid) {
        Context context = getActivity();
        if (context != null) {
            String requestId = StringUtils.randomString(6);
            mKeyRequestIds.put(jid.asBareJid().toString(), requestId);
            mServiceBus.post(new PublicKeyRequest(requestId, jid));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onPublicKey(PublicKeyEvent event) {
        if (event.id != null && event.id.equals(mKeyRequestIds.get(event.jid.asBareJid().toString()))) {
            // invalidate contact
            Contact.invalidate(event.jid.asBareJid().toString());
            // request presence again
            requestPresence();
        }
    }

    private void onUserStatusChanged(PresenceEvent event) {
        Context context = getContext();
        if (context == null)
            return;

        if (Log.isDebug()) {
            Log.d(TAG, "group member presence from " + event.jid + " (type=" + event.type + ", fingerprint=" + event.fingerprint + ")");
        }

        updateStatusText();

        // no encryption - pointless to verify keys
        if (!Preferences.getEncryptionEnabled(context))
            return;

        String bareJid = event.jid.asBareJid().toString();

        Contact contact = Contact.findByUserId(context, bareJid);
        // if this is null, we are accepting the key for the first time
        PGPPublicKeyRing trustedPublicKey = contact.getTrustedPublicKeyRing();

        // request the key if we don't have a trusted one and of course if the user has a key
        boolean unknownKey = (trustedPublicKey == null && contact.getFingerprint() != null);
        boolean changedKey = false;
        // check if fingerprint changed (only if we have roster presence)
        if (event.type != null && trustedPublicKey != null && event.fingerprint != null) {
            String oldFingerprint = PGP.getFingerprint(PGP.getMasterKey(trustedPublicKey));
            if (!event.fingerprint.equalsIgnoreCase(oldFingerprint)) {
                // fingerprint has changed since last time
                changedKey = true;
            }
        }
        // user has no key (or we have no roster presence) or it couldn't be found: request it
        else if ((trustedPublicKey == null && event.fingerprint == null) || event.type == null) {
            if (mKeyRequestIds.containsKey(event.jid.toString())) {
                // avoid request loop
                mKeyRequestIds.remove(event.jid.toString());
            }
            else {
                // autotrust the key we are about to request
                // but set the trust level to ignored because we didn't really verify it
                Keyring.setAutoTrustLevel(context, event.jid.toString(), Keyring.TRUST_IGNORED);
                requestPublicKey(event.jid);
            }
        }

        if (Keyring.isAdvancedMode(context, event.jid.toString()) && (changedKey || unknownKey)) {
            showKeyWarning();
        }
    }

    @Override
    public void onUserOnline(UserOnlineEvent event) {
        // check that origin matches the current chat
        if (!isUserId(event.jid.toString())) {
            // not for us
            return;
        }

        super.onUserOnline(event);
        onUserStatusChanged(event);
    }

    @Override
    public void onUserOffline(UserOfflineEvent event) {
        // check that origin matches the current chat
        if (!isUserId(event.jid.toString())) {
            // not for us
            return;
        }

        super.onUserOffline(event);
        onUserStatusChanged(event);
    }

    @Override
    public void onNoUserPresence(NoPresenceEvent event) {
        // nothing to do
    }

    @Override
    protected void resetConnectionStatus() {
        super.resetConnectionStatus();
        mTypingUsers.clear();
        updateStatusText();
        mKeyRequestIds.clear();
    }

    @Override
    public void onRosterLoaded(RosterLoadedEvent event) {
        requestPresence();
    }

    @Override
    protected void onStartTyping(String jid, @Nullable String groupJid) {
        if (mGroupJID.equals(groupJid)) {
            mTypingUsers.add(jid);
            updateStatusText();
        }
    }

    @Override
    protected void onStopTyping(String jid, @Nullable String groupJid) {
        if (mGroupJID.equals(groupJid)) {
            mTypingUsers.remove(jid);
            updateStatusText();
        }
    }

    @Override
    protected boolean isUserId(String jid) {
        if (mConversation != null) {
            String[] users = mConversation.getGroupPeers();
            if (users != null) {
                for (String user : users) {
                    if (XMPPUtils.equalsBareJID(jid, user))
                        return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getUserId() {
        return mGroupJID;
    }

    @Override
    public boolean sendTyping() {
        if (mAvailableResources.size() > 0) {
            mServiceBus.post(new SendChatStateRequest.Builder(null)
                .setChatState(ChatState.composing)
                .setTo(JidCreate.fromOrThrowUnchecked(mGroupJID))
                .setGroup(true)
                .build());
            return true;
        }
        return false;
    }

    @Override
    public boolean sendInactive() {
        if (mAvailableResources.size() > 0) {
            mServiceBus.post(new SendChatStateRequest.Builder(null)
                .setChatState(ChatState.inactive)
                .setTo(JidCreate.fromOrThrowUnchecked(mGroupJID))
                .setGroup(true)
                .build());
            return true;
        }
        return false;
    }

    /** Updates the status text in the toolbar. */
    private void updateStatusText() {
        Context context = getContext();
        if (context == null)
            return;

        int typingPeople = mTypingUsers.size();
        if (typingPeople > 0) {
            int msgId;
            Object[] args;
            if (typingPeople == 1) {
                Contact c = Contact.findByUserId(context, mTypingUsers.iterator().next());
                msgId = R.string.seen_group_typing_label_one;
                args = new Object[] { c.getShortDisplayName() };
            }
            else if (typingPeople == 2) {
                Iterator<String> users = mTypingUsers.iterator();
                Contact c1 = Contact.findByUserId(context, users.next());
                Contact c2 = Contact.findByUserId(context, users.next());
                msgId = R.string.seen_group_typing_label_two;
                args = new Object[] { c1.getShortDisplayName(), c2.getShortDisplayName() };
            }
            else {
                msgId = R.string.seen_group_typing_label_more;
                args = new Object[] { typingPeople };
            }
            setActivityTitle(null, context.getResources().getString(msgId, args));
        }
        else {
            final Conversation conv = mConversation;
            if (conv != null) {
                // set group title
                String subject = conv.getGroupSubject();
                if (TextUtils.isEmpty(subject))
                    subject = context.getString(R.string.group_untitled);

                String status;
                int membership = conv.getGroupMembership();
                switch (membership) {
                    case Groups.MEMBERSHIP_PARTED:
                        status = context.getString(R.string.group_command_text_part_self);
                        break;
                    case Groups.MEMBERSHIP_KICKED:
                        status = context.getString(R.string.group_command_text_part_kicked);
                        break;
                    case Groups.MEMBERSHIP_OBSERVER: {
                        int count = conv.getGroupPeers().length;
                        status = getMemberCountQuantityString(count, mAvailableResources.size());
                        break;
                    }
                    case Groups.MEMBERSHIP_MEMBER: {
                        // +1 because we are not included in the members list
                        int count = conv.getGroupPeers().length + 1;
                        // the "connected" here is used to show ourselves as online
                        status = getMemberCountQuantityString(count, mConnected ? mAvailableResources.size() + 1 : 0);
                        break;
                    }
                    default:
                        // shouldn't happen
                        throw new RuntimeException("Unknown membership status: " + membership);
                }

                setActivityTitle(subject, status);
            }
        }
    }

    // FIXME not i18n-friendly (especially for RTL)
    private String getMemberCountQuantityString(int count, int online) {
        StringBuilder msg = new StringBuilder();
        msg.append(getResources().getQuantityString(R.plurals.group_people, count, count));

        if (online > 0) {
            msg.append(", ")
                .append(getResources().getQuantityString(R.plurals.group_people_online, online, online));
        }

        return msg.toString();
    }

    public void viewGroupInfo() {
        final Activity ctx = getActivity();
        if (ctx == null)
            return;

        int membership = mConversation != null ? mConversation.getGroupMembership() : Groups.MEMBERSHIP_PARTED;
        if (membership == Groups.MEMBERSHIP_MEMBER || membership == Groups.MEMBERSHIP_OBSERVER) {
            if (Kontalk.hasTwoPanesUI(ctx)) {
                GroupInfoDialog.start(ctx, this, getThreadId(), REQUEST_GROUP_INFO);
            }
            else {
                GroupInfoActivity.start(ctx, this, getThreadId(), REQUEST_GROUP_INFO);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_GROUP_INFO) {
            switch (resultCode) {
                case GroupInfoActivity.RESULT_PRIVATE_CHAT:
                    ((ComposeMessageParent) getActivity())
                        .loadConversation(data.getData());
                    break;

                case GroupInfoActivity.RESULT_ADD_USERS:
                    addUsers();
                    break;
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}

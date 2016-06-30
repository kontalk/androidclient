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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;

import org.jivesoftware.smack.packet.Presence;

import android.content.DialogInterface;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.util.XMPPUtils;


/**
 * Composer fragment for group chats.
 * @author Daniele Ricci
 */
public class GroupMessageFragment extends AbstractComposeFragment {
    private static final String TAG = ComposeMessage.TAG;

    /** The virtual or real group JID. */
    private String mGroupJID;

    private MenuItem mInviteGroupMenu;
    private MenuItem mSetGroupSubjectMenu;
    private MenuItem mLeaveGroupMenu;

    @Override
    public boolean sendInactive() {
        // TODO
        return false;
    }

    @Override
    protected void updateUI() {
        super.updateUI();
        if (mInviteGroupMenu != null) {
            boolean visible;
            String myUser = Authenticator.getSelfJID(getContext());

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
            mLeaveGroupMenu.setVisible(visible);
            mLeaveGroupMenu.setEnabled(visible);
        }
    }

    @Override
    protected void onInflateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_message_menu, menu);
        mInviteGroupMenu = menu.findItem(R.id.invite_group);
        mSetGroupSubjectMenu = menu.findItem(R.id.group_subject);
        mLeaveGroupMenu = menu.findItem(R.id.leave_group);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
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
            String[] users = usersList.toArray(new String[usersList.size()]);
            mConversation.addUsers(users);
            // reload conversation
            ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
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
        new AlertDialogWrapper.Builder(getActivity())
            .setMessage(R.string.confirm_will_leave_group)
            .setPositiveButton(android.R.string.ok,
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // leave group
                    mConversation.leaveGroup();
                    // reload conversation
                    ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
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

    private void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
        // reload conversation
        ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
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
     * Since this is a group chat, it can be only opened from within the app.
     * No ACTION_VIEW_USERID intent ever will be delivered for this.
     */
    @Override
    protected void handleActionViewConversation(Uri uri, Bundle args) {
        throw new AssertionError("This shouldn't be called ever!");
    }

    @Override
    protected void onArgumentsProcessed() {
        // nothing
    }

    @Override
    protected void onConversationCreated() {
        super.onConversationCreated();
        // set group title
        String subject = mConversation.getGroupSubject();
        if (TextUtils.isEmpty(subject))
            subject = getString(R.string.group_untitled);

        String status;
        boolean sendEnabled;
        if (mConversation.getGroupMembership() != Groups.MEMBERSHIP_PARTED) {
            // +1 because we are not included in the members list
            int count = mConversation.getGroupPeers().length + 1;
            status = getResources()
                .getQuantityString(R.plurals.group_people, count, count);
            sendEnabled = count > 1;
        }
        else {
            status = getString(R.string.group_command_text_part_self);
            sendEnabled = false;
        }

        // disable sending if necessary
        mComposer.setSendEnabled(sendEnabled);

        setActivityTitle(subject, status);
    }

    @Override
    protected void onPresence(String jid, Presence.Type type, boolean removed, Presence.Mode mode, String fingerprint) {
        // TODO
    }

    @Override
    protected void onConnected() {
        // TODO
    }

    @Override
    protected void onRosterLoaded() {
        // TODO
    }

    @Override
    protected void onStartTyping(String jid) {
        // TODO
    }

    @Override
    protected void onStopTyping(String jid) {
        // TODO
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
        // TODO
        return false;
    }

    public void viewGroupInfo() {
        // TODO tablet support
        GroupInfoActivity.start(getContext(), getThreadId());
    }

}

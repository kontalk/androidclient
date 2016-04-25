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

import java.util.HashSet;
import java.util.Set;

import com.afollestad.materialdialogs.MaterialDialog;

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.data.Contact;
import org.kontalk.message.CompositeMessage;
import org.kontalk.service.msgcenter.MessageCenterService;


/**
 * Composer fragment for group chats.
 * @author Daniele Ricci
 */
public class GroupMessageFragment extends AbstractComposeFragment {

    /** The virtual or real group JID. */
    private String mGroupJID;

    private MenuItem mInviteGroupMenu;
    private MenuItem mSetGroupSubjectMenu;

    @Override
    protected void sendBinaryMessageInternal(String mime, Uri localUri, long length,
        String previewPath, boolean encrypt, int compress, long msgId, String packetId) {
        // TODO
        throw new RuntimeException("Not implemented");
    }

    @Override
    protected void sendTextMessageInternal(String text, boolean encrypted, long msgId, String packetId) {
        MessageCenterService.sendGroupTextMessage(getContext(),
            mConversation.getGroupJid(), mConversation.getGroupSubject(),
            mConversation.getGroupPeers(), text, encrypted, msgId, packetId);
    }

    @Override
    public boolean sendInactive() {
        // TODO
        return false;
    }

    @Override
    protected void updateUI() {
        super.updateUI();
        if (mInviteGroupMenu != null) {
            String myUser = Authenticator.getSelfJID(getContext());
            boolean visible = KontalkGroupManager.KontalkGroup
                .checkOwnership(mConversation.getGroupJid(), myUser);
            mInviteGroupMenu.setVisible(visible);
            mInviteGroupMenu.setEnabled(visible);
            mSetGroupSubjectMenu.setVisible(visible);
            mSetGroupSubjectMenu.setEnabled(visible);
        }
    }

    @Override
    protected void onInflateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_message_menu, menu);
        mInviteGroupMenu = menu.findItem(R.id.invite_group);
        mSetGroupSubjectMenu = menu.findItem(R.id.group_subject);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.group_subject:
                changeGroupSubject();
                return true;
        }

        return false;
    }

    @Override
    protected void addUsers(String[] members) {
        // ensure no duplicates
        String selfJid = Authenticator.getSelfJID(getContext());
        Set<String> usersList = new HashSet<>();
        usersList.add(getUserId());
        for (String member : members) {
            // exclude ourselves
            if (!member.equalsIgnoreCase(selfJid))
                usersList.add(member);
        }

        String[] users = usersList.toArray(new String[usersList.size()]);
        mConversation.addUsers(users);
        // reload conversation
        ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
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
            .show();
    }

    private void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
        // reload conversation
        ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
    }

    @Override
    protected String getDecodedPeer(CompositeMessage msg) {
        String userId = msg.getSender();
        Contact c = Contact.findByUserId(getContext(), userId);
        return c != null ? c.getNumber() : userId;
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
            // TODO i18n
            subject = "Untitled group";

        // +1 because we are not included in the members list
        // TODO i18n
        String status = String.format("%d people", mConversation.getGroupPeers().length + 1);

        setActivityTitle(subject, status);
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
}

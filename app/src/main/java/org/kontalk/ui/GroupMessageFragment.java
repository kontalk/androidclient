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

import com.afollestad.materialdialogs.MaterialDialog;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
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
import org.kontalk.crypto.Coder;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.CompositeMessage;
import org.kontalk.message.GroupCommandComponent;
import org.kontalk.provider.MyMessages;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.Preferences;


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
        // add members to database
        Conversation.addUsers(getContext(), getUserId(), members);

        // store add group member command to outbox
        boolean encrypted = Preferences.getEncryptionEnabled(getContext());
        String msgId = MessageCenterService.messageId();
        Uri cmdMsg = storeAddGroupMember(members, msgId, encrypted);
        // TODO check for null

        // send add group member command now
        String[] currentMembers = mConversation.getGroupPeers(true);
        MessageCenterService.addGroupMembers(getContext(), getUserId(),
            mConversation.getGroupSubject(), currentMembers, members, encrypted,
            ContentUris.parseId(cmdMsg), msgId);

        // reload conversation
        ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
    }

    private void changeGroupSubject() {
        new MaterialDialog.Builder(getContext())
            // TODO i18n
            .title("Group title")
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
        // set group subject in database
        Conversation.setGroupSubject(getContext(), getUserId(), subject);

        // store set group subject command to outbox
        boolean encrypted = Preferences.getEncryptionEnabled(getContext());
        String msgId = MessageCenterService.messageId();
        Uri cmdMsg = storeSetGroupSubject(subject, msgId, encrypted);
        // TODO check for null

        // send set group subject command now
        String[] currentMembers = mConversation.getGroupPeers(true);
        MessageCenterService.setGroupSubject(getContext(), getUserId(),
            subject, currentMembers, encrypted,
            ContentUris.parseId(cmdMsg), msgId);

        // reload conversation
        ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
    }

    private Uri storeSetGroupSubject(String subject, String msgId, boolean encrypted) {
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, getThreadId());
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, getUserId());
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.getSetSubjectCommandBodyContent(subject));
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return getActivity().getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
    }

    private Uri storeAddGroupMember(String[] members, String msgId, boolean encrypted) {
        ContentValues values = new ContentValues();
        values.put(MyMessages.Messages.THREAD_ID, getThreadId());
        values.put(MyMessages.Messages.MESSAGE_ID, msgId);
        values.put(MyMessages.Messages.PEER, getUserId());
        values.put(MyMessages.Messages.BODY_MIME, GroupCommandComponent.MIME_TYPE);
        values.put(MyMessages.Messages.BODY_CONTENT, GroupCommandComponent.getAddMembersBodyContent(members));
        values.put(MyMessages.Messages.BODY_LENGTH, 0);
        values.put(MyMessages.Messages.UNREAD, false);
        values.put(MyMessages.Messages.DIRECTION, MyMessages.Messages.DIRECTION_OUT);
        values.put(MyMessages.Messages.TIMESTAMP, System.currentTimeMillis());
        values.put(MyMessages.Messages.STATUS, MyMessages.Messages.STATUS_SENDING);
        // of course outgoing messages are not encrypted in database
        values.put(MyMessages.Messages.ENCRYPTED, false);
        values.put(MyMessages.Messages.SECURITY_FLAGS, encrypted ? Coder.SECURITY_BASIC : Coder.SECURITY_CLEARTEXT);
        return getActivity().getContentResolver().insert(MyMessages.Messages.CONTENT_URI, values);
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

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

import android.net.Uri;
import android.os.Bundle;

import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.CompositeMessage;
import org.kontalk.service.msgcenter.MessageCenterService;


/**
 * Composer fragment for group chats.
 * @author Daniele Ricci
 */
public class GroupMessageFragment extends AbstractComposeFragment {

    /** The virtual or real group JID. */
    private String mGroupJID;

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
    protected void addUsers(String[] members) {
        Conversation.addUsers(getContext(), getUserId(), members);
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
    public String getUserId() {
        return mGroupJID;
    }

    @Override
    public boolean sendTyping() {
        // TODO
        return false;
    }
}

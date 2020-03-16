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

package org.kontalk.service.msgcenter.event;

import org.jivesoftware.smackx.chatstates.ChatState;
import org.jxmpp.jid.Jid;


/**
 * For requesting the message center to send a chat state notification message.
 * @author Daniele Ricci
 */
public class SendChatStateRequest extends RequestEvent {

    public final Jid to;
    public final boolean group;

    public final ChatState chatState;
    // TODO boolean encrypt

    public SendChatStateRequest(String id, Jid to, boolean group,
            ChatState chatState) {
        super(id);
        this.to = to;
        this.group = group;
        this.chatState = chatState;
    }

    public static final class Builder {
        private final String id;
        private Jid to;
        private boolean group;

        private ChatState chatState;

        public Builder(String id) {
            this.id = id;
        }

        public Builder setTo(Jid jid) {
            this.to = jid;
            return this;
        }

        public Builder setGroup(boolean group) {
            this.group = group;
            return this;
        }

        public Builder setChatState(ChatState chatState) {
            this.chatState = chatState;
            return this;
        }

        public SendChatStateRequest build() {
            return new SendChatStateRequest(id, to, group, chatState);
        }
    }

}

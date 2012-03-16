/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.client;

import java.io.IOException;
import java.util.Collection;

import org.kontalk.client.Protocol.MessageAckRequest;
import org.kontalk.service.ClientThread;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;

import android.content.Context;


/**
 * Request job for acknowledging incoming messages.
 * @author Daniele Ricci
 */
public class ReceivedJob extends RequestJob {

    protected String[] mMessageList;

    public ReceivedJob(String[] msgId) {
        mMessageList = msgId;
    }

    public ReceivedJob(Collection<String> msgId) {
        mMessageList = new String[msgId.size()];
        msgId.toArray(mMessageList);
    }

    @Override
    public String call(ClientThread client, RequestListener listener,
            Context context) throws IOException {
        MessageAckRequest.Builder b = MessageAckRequest.newBuilder();
        for (String id : mMessageList)
            b.addMessageId(id);
        return client.getConnection().send(b.build());
    }

}

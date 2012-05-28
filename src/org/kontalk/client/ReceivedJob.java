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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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

    private List<String> mMessageList;

    public ReceivedJob(String msgId) {
        mMessageList = new ArrayList<String>(1);
        mMessageList.add(msgId);
    }

    public ReceivedJob(String[] msgId) {
        mMessageList = new ArrayList<String>(Arrays.asList(msgId));
    }

    public ReceivedJob(Collection<String> msgId) {
        // avoid allocating another list
        if (msgId instanceof ArrayList<?>)
            mMessageList = (List<String>) msgId;
        else
            mMessageList = new ArrayList<String>(msgId);
    }

    @Override
    public synchronized String execute(ClientThread client, RequestListener listener,
            Context context) throws IOException {
        MessageAckRequest.Builder b = MessageAckRequest.newBuilder();
        b.addAllMessageId(mMessageList);
        return client.getConnection().send(b.build());
    }

    public synchronized boolean add(String msgId) {
        if (!mMessageList.contains(msgId))
            return mMessageList.add(msgId);
        return false;
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.service;

import java.io.IOException;
import java.util.List;

import org.kontalk.client.Protocol.UserLookupRequest;

import android.content.Context;


/**
 * A {@link RequestJob} to lookup users from server.
 * @author Daniele Ricci
 */
public class UserLookupJob extends RequestJob {
    protected List<String> mUsers;
    protected String mUser;

    public UserLookupJob(List<String> hashList) {
        mUsers = hashList;
    }

    public UserLookupJob(String hash) {
        mUser = hash;
    }

    @Override
    public String execute(ClientThread client, RequestListener listener, Context context) throws IOException {
        UserLookupRequest.Builder b = UserLookupRequest.newBuilder();
        if (mUsers != null)
            b.addAllUserId(mUsers);
        else
            b.addUserId(mUser);
        return client.getConnection().send(b.build());
    }

}

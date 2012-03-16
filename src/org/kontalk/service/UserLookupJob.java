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

    public UserLookupJob(List<String> hashList) {
        mUsers = hashList;
    }

    @Override
    public String call(ClientThread client, RequestListener listener, Context context) throws IOException {
        UserLookupRequest.Builder b = UserLookupRequest.newBuilder();
        b.addAllUserId(mUsers);
        return client.getConnection().send(b.build());
    }

}

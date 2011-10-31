package org.kontalk.client;

import java.io.IOException;
import java.util.Collection;

import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;

import android.content.Context;

import com.google.protobuf.MessageLite;

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
    public MessageLite call(RequestClient client, RequestListener listener,
            Context context) throws IOException {

        return client.received(mMessageList);
    }

}

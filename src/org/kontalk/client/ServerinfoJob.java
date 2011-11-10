package org.kontalk.client;

import java.io.IOException;

import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;

import android.content.Context;

import com.google.protobuf.MessageLite;


public class ServerinfoJob extends RequestJob {

    @Override
    public MessageLite call(RequestClient client, RequestListener listener,
            Context context) throws IOException {

        return client.serverinfo();
    }

}

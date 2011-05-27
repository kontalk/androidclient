package org.nuntius.android.client;

import com.loopj.android.http.RequestParams;

import android.content.Context;


/**
 * A client for the polling service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class PollingClient extends AbstractClient {

    public PollingClient(Context context, EndpointServer server) {
        super(context, server);
    }

    /**
     * Polls server for new messages.
     */
    public void polling() {
        RequestParams params = new RequestParams();
        params.header(HEADER_AUTH_TOKEN, mAuthToken);
        super.get(mContext, mServer.getPollingURL(), params, mHandler);
    }
}

package org.nuntius.android.client;

import com.loopj.android.http.RequestParams;

import android.content.Context;


/**
 * A client for the request service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class RequestClient extends AbstractClient {

    public RequestClient(Context context, EndpointServer server) {
        super(context, server);
    }

    public void request(String cmd, String[] params) {
        request(cmd, params, null);
    }

    public void request(String cmd, String[] params, String bodyContent) {
        RequestParams req = new RequestParams();
        req.header(HEADER_AUTH_TOKEN, mAuthToken);
        if (params != null) {
            for (int i = 0; i < params.length; i += 2) {
                req.put(params[i], params[i + 1]);
            }
        }

        if (bodyContent != null) {
            req.setRequestBody("text/xml", bodyContent);
            super.post(mContext, mServer.getRequestURL(cmd), req, mHandler);
        }
        else {
            super.get(mContext, mServer.getRequestURL(cmd), req, mHandler);
        }
    }
}

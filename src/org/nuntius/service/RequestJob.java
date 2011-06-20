package org.nuntius.service;

import java.util.List;

import org.apache.http.NameValuePair;

public class RequestJob {

    protected String mCommand;
    protected List<NameValuePair> mParams;
    protected String mContent;
    protected RequestListener mListener;

    // TODO content should be byte[]
    public RequestJob(String cmd, List<NameValuePair> params) {
        this(cmd, params, null);
    }

    public RequestJob(String cmd, List<NameValuePair> params, String content) {
        mCommand = cmd;
        mParams = params;
        mContent = content;
    }

    public String toString() {
        return getClass().getSimpleName() + ": cmd=" + mCommand;
    }

    public String getCommand() {
        return mCommand;
    }

    public List<NameValuePair> getParams() {
        return mParams;
    }

    public String getContent() {
        return mContent;
    }

    public void setListener(RequestListener listener) {
        mListener = listener;
    }

    public RequestListener getListener() {
        return mListener;
    }
}

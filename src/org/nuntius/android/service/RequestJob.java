package org.nuntius.android.service;

import java.util.List;

import org.apache.http.NameValuePair;

public class RequestJob {

    private String command;
    private List<NameValuePair> params;
    private String content;

    public RequestJob(String cmd, List<NameValuePair> params) {
        this(cmd, params, null);
    }

    public RequestJob(String cmd, List<NameValuePair> params, String content) {
        this.command = cmd;
        this.params = params;
        this.content = content;
    }

    public String toString() {
        return getClass().getSimpleName() + ": cmd=" + command;
    }

    public String getCommand() {
        return command;
    }

    public List<NameValuePair> getParams() {
        return params;
    }

    public String getContent() {
        return content;
    }
}

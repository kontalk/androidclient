package org.nuntius.service;

import java.util.List;

import org.nuntius.client.StatusResponse;

public interface ResponseListener {

    /** Manages the statuses receved from one request job. */
    public void response(RequestJob job, List<StatusResponse> statuses);

    /** Called if an error occured during sending a request to the server. */
    public void error(RequestJob job, Throwable e);
}

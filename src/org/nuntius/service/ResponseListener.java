package org.nuntius.service;

import java.util.List;

import org.nuntius.client.StatusResponse;

public interface ResponseListener {

    /** Manages the statuses receved from one request job. */
    public void response(RequestJob job, List<StatusResponse> statuses);

    /**
     * Called if an error occured while sending a request to the server.
     * @param job the job to be executed
     * @param exc the exception occured
     * @return true if the job should be re-queued, false to discard it.
     */
    public boolean error(RequestJob job, Throwable exc);
}

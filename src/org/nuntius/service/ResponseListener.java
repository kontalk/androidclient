package org.nuntius.service;

import java.util.List;

import org.nuntius.client.StatusResponse;

public interface ResponseListener {

    /**
     * Manages the statuses receved from one request job.
     * @param statuses
     */
    public void response(RequestJob job, List<StatusResponse> statuses);
}

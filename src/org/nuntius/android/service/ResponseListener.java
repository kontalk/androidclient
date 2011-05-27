package org.nuntius.android.service;

import java.util.List;

import org.nuntius.android.client.StatusResponse;

public interface ResponseListener {

    /**
     * Manages the statuses receved from one request job.
     * @param statuses
     */
    public void response(List<StatusResponse> statuses);
}

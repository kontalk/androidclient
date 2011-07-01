package org.kontalk.service;

import java.util.List;

import org.kontalk.client.StatusResponse;

public interface RequestListener {

    /**
     * Called now and then while sending out data.
     * Useful for checking the upload progress.
     * @param bytes how many bytes have been uploaded so far
     */
    public void uploadProgress(long bytes);

    /**
     * Called now and then while receiving data in.
     * Useful for checking the download progress.
     * @param bytes how many bytes have been uploaded so far
     */
    public void downloadProgress(long bytes);

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

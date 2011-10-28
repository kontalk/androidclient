package org.kontalk.service;

import com.google.protobuf.MessageLite;


public interface RequestListener {

    /**
     * Called now and then while sending out data.
     * Useful for checking the upload progress.
     * @param bytes how many bytes have been uploaded so far
     */
    public void uploadProgress(RequestJob job, long bytes);

    /**
     * Called now and then while receiving data in.
     * Useful for checking the download progress.
     * @param bytes how many bytes have been downloaded so far
     */
    public void downloadProgress(RequestJob job, long bytes);

    /** Manages the statuses receved from one request job. */
    public void response(RequestJob job, MessageLite response);

    /**
     * Called if an error occured while sending a request to the server.
     * @param job the job to be executed
     * @param exc the exception occured
     * @return true if the job should be re-queued, false to discard it.
     */
    public boolean error(RequestJob job, Throwable exc);

}

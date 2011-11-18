package org.kontalk.service;

import java.io.File;


public interface DownloadListener {

    /**
     * Called when then download is about to start.
     * @param url
     * @param destination
     * @param length the declared Content-Length
     */
    public void start(String url, File destination, long length);

    /**
     * Called now and then while receiving data in.
     * @param url
     * @param destination
     * @param bytes how many bytes have been downloaded so far
     */
    public void progress(String url, File destination, long bytes);

    /**
     * Called when the download has been completed.
     * @param url
     * @param destination
     */
    public void completed(String url, String mime, File destination);

    /**
     * Called if an error occured while download.
     * @param url
     * @param destination
     * @param exc the exception occured
     */
    public void error(String url, File destination, Throwable exc);

}

/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service;


/**
 * Generic interface for listening to progress and status of a
 * {@link RequestJob}.
 * @author Daniele Ricci
 */
public interface RequestListener {

    /** Called just before the job gets started. */
    public void starting(ClientThread client, RequestJob job);

    /**
     * Called now and then while sending out data.
     * Useful for checking the upload progress.
     * @param bytes how many bytes have been uploaded so far
     */
    public void uploadProgress(ClientThread client, RequestJob job, long bytes);

    /**
     * Called now and then while receiving data in.
     * Useful for checking the download progress.
     * @param bytes how many bytes have been downloaded so far
     */
    public void downloadProgress(ClientThread client, RequestJob job, long bytes);

    /** Called when the request to the server has been completed. */
    public void done(ClientThread client, RequestJob job, String txId);

    /**
     * Called if an error occured while sending a request to the server.
     * @param job the job to be executed
     * @param exc the exception occured
     * @return true if the job should be re-queued, false to discard it.
     */
    public boolean error(ClientThread client, RequestJob job, Throwable exc);

}

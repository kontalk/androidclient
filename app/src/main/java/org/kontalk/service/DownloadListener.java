/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;


public interface DownloadListener {

    /**
     * Called when then download is about to start.
     * @param url
     * @param destination
     * @param length the declared Content-Length
     */
    void start(String url, File destination, long length);

    /**
     * Called now and then while receiving data in.
     * @param url
     * @param destination
     * @param bytes how many bytes have been downloaded so far
     */
    void progress(String url, File destination, long bytes);

    /**
     * Called when the download has been completed.
     * @param url
     * @param destination
     */
    void completed(String url, String mime, File destination);

    /**
     * Called if an error occurred while download.
     * @param url
     * @param destination
     * @param exc the exception occurred
     */
    void error(String url, File destination, Throwable exc);

}

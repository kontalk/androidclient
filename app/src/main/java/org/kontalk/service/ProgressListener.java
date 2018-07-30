/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import org.kontalk.upload.UploadConnection;


/**
 * Generic interface for listening to progress and status of a generic request.
 * @author Daniele Ricci
 */
public interface ProgressListener {

    /** Called when operation is starting (before writeTo happens). */
    public void start(UploadConnection conn);

    /**
     * Called now and then while processing data.
     * Useful for checking the upload progress.
     * @param bytes how many bytes have been uploaded so far
     */
    public void progress(UploadConnection conn, long bytes);

}

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

package org.kontalk.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.NonNull;

import org.kontalk.service.ProgressListener;
import org.kontalk.upload.UploadConnection;


public class ProgressInputStreamEntity {
    private static final int BUFFER_SIZE = 1024 * 8;

    private final InputStream mStream;
    private final UploadConnection mConn;
    private final ProgressListener mListener;
    private final long mPublishDelay;

    public ProgressInputStreamEntity(InputStream instream,
            final UploadConnection conn, final ProgressListener listener,
            final long publishDelay) {
        mStream = instream;
        mConn = conn;
        mListener = listener;
        mPublishDelay = publishDelay;
    }

    private void _writeTo(OutputStream outstream) throws IOException {
        InputStream instream = mStream;
        try {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int l;
            while ((l = instream.read(buffer)) != -1) {
                outstream.write(buffer, 0, l);
            }
        }
        finally {
            if (instream != null) {
                try {
                    instream.close();
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    public void writeTo(final OutputStream outstream, long length) throws IOException {
        mListener.start(mConn);
        _writeTo(new CountingOutputStream(outstream, mConn, mListener, length, mPublishDelay));
    }

    private static final class CountingOutputStream extends FilterOutputStream {

        private final UploadConnection conn;
        private final ProgressListener listener;
        private final StepTimer publishTimer;
        private final long size;
        private long transferred;

        CountingOutputStream(OutputStream out, UploadConnection conn,
            ProgressListener listener, long size, long publishDelay) {
            super(out);
            this.listener = listener;
            this.conn = conn;
            this.size = size;
            this.publishTimer = new StepTimer(publishDelay);
            this.transferred = 0;
        }

        @Override
        public void write(@NonNull byte[] buffer) throws IOException {
            out.write(buffer);
            publishProgress(buffer.length);
        }

        @Override
        public void write(@NonNull byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            publishProgress(len);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            publishProgress(1);
        }

        private void publishProgress(long add) {
            this.transferred += add;
            if (this.transferred >= this.size || publishTimer.isStep(true))
                this.listener.progress(conn, this.transferred);
        }
    }

}

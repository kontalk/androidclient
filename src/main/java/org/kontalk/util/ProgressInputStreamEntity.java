/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import org.apache.http.entity.InputStreamEntity;
import org.kontalk.service.ProgressListener;
import org.kontalk.upload.UploadConnection;


public class ProgressInputStreamEntity extends InputStreamEntity {
    protected final UploadConnection mConn;
    protected final ProgressListener mListener;

    public ProgressInputStreamEntity(InputStream instream, long length,
            final UploadConnection conn, final ProgressListener listener) {
        super(instream, length);
        mConn = conn;
        mListener = listener;
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        mListener.start(mConn);
        super.writeTo(new CountingOutputStream(outstream, mConn, mListener));
    }

    private static final class CountingOutputStream extends FilterOutputStream {

        private final UploadConnection conn;
        private final ProgressListener listener;
        private long transferred;

        public CountingOutputStream(OutputStream out, UploadConnection conn,
            ProgressListener listener) {
            super(out);
            this.listener = listener;
            this.conn = conn;
            this.transferred = 0;
        }

        @Override
        public void write(byte[] buffer) throws IOException {
            out.write(buffer);
            publishProgress(buffer.length);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
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
            this.listener.progress(conn, this.transferred);
        }
    }

}

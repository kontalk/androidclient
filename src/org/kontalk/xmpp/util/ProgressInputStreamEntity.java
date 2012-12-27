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

package org.kontalk.xmpp.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.entity.InputStreamEntity;
import org.kontalk.xmpp.service.ClientThread;
import org.kontalk.xmpp.service.RequestJob;
import org.kontalk.xmpp.service.RequestListener;


public class ProgressInputStreamEntity extends InputStreamEntity {
    protected final ClientThread mClient;
    protected final RequestJob mJob;
    protected final RequestListener mListener;

    public ProgressInputStreamEntity(InputStream instream, long length,
            final ClientThread client, final RequestJob job,
            final RequestListener listener) {
        super(instream, length);
        mClient = client;
        mJob = job;
        mListener = listener;
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        super.writeTo(new CountingOutputStream(outstream, mClient, mJob, mListener));
    }

    private static final class CountingOutputStream extends FilterOutputStream {

        private final ClientThread client;
        private final RequestJob job;
        private final RequestListener listener;
        private long transferred;

        public CountingOutputStream(final OutputStream out,
                final ClientThread client, final RequestJob job,
                final RequestListener listener) {
            super(out);
            this.listener = listener;
            this.client = client;
            this.job = job;
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
            this.listener.uploadProgress(client, job, this.transferred);
        }
    }

}

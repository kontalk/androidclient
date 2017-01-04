/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import android.support.annotation.NonNull;

import org.kontalk.service.DownloadListener;


public class ProgressOutputStreamEntity {
    private static final int BUFFER_SIZE = 10240 * 10;

    private final HttpURLConnection mParent;
    private final String mUrl;
    private final File mFile;
    private final DownloadListener mListener;
    private final long mPublishDelay;

    public ProgressOutputStreamEntity(HttpURLConnection parent,
            String url, File file, final DownloadListener listener,
            final long publishDelay) {
        mParent = parent;
        mUrl = url;
        mFile = file;
        mListener = listener;
        mPublishDelay = publishDelay;
    }

    private void _writeTo(OutputStream outstream) throws IOException {
        InputStream instream = mParent.getInputStream();
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

    public void writeTo(OutputStream outstream) throws IOException {
        mListener.start(mUrl, mFile, mParent.getContentLength());
        _writeTo(new CountingOutputStream(outstream, mUrl, mFile, mListener, mParent.getContentLength(), mPublishDelay));
        String mime = mParent.getContentType();
        mListener.completed(mUrl, mime, mFile);
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private final DownloadListener listener;
        private final String url;
        private final File file;
        private final StepTimer publishTimer;
        private final long size;
        private long transferred;

        CountingOutputStream(final OutputStream out,
                final String url, final File file, final DownloadListener listener,
                long size, long publishDelay) {
            super(out);
            this.url = url;
            this.file = file;
            this.listener = listener;
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
            if (this.transferred >= this.size || publishTimer.isStep())
                this.listener.progress(url, file, this.transferred);
        }
    }

}

package org.kontalk.util;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.kontalk.service.DownloadListener;


public class ProgressOutputStreamEntity implements HttpEntity {

    private final HttpEntity mParent;
    private final String mUrl;
    private final File mFile;
    private final DownloadListener mListener;

    public ProgressOutputStreamEntity(HttpEntity parent,
            String url, File file, final DownloadListener listener) {
        mParent = parent;
        mUrl = url;
        mFile = file;
        mListener = listener;
    }

    @Override
    public void consumeContent() throws IOException {
        mParent.consumeContent();
    }

    @Override
    public InputStream getContent() throws IOException, IllegalStateException {
        return mParent.getContent();
    }

    @Override
    public Header getContentEncoding() {
        return mParent.getContentEncoding();
    }

    @Override
    public long getContentLength() {
        return mParent.getContentLength();
    }

    @Override
    public Header getContentType() {
        return mParent.getContentType();
    }

    @Override
    public boolean isChunked() {
        return mParent.isChunked();
    }

    @Override
    public boolean isRepeatable() {
        return mParent.isRepeatable();
    }

    @Override
    public boolean isStreaming() {
        return mParent.isStreaming();
    }

    @Override
    public void writeTo(OutputStream outstream) throws IOException {
        mListener.start(mUrl, mFile, mParent.getContentLength());
        mParent.writeTo(new CountingOutputStream(outstream, mUrl, mFile, mListener));
        mListener.completed(mUrl, mFile);
    }

    private static final class CountingOutputStream extends FilterOutputStream {
        private final DownloadListener listener;
        private final String url;
        private final File file;
        private long transferred;

        public CountingOutputStream(final OutputStream out,
                final String url, final File file, final DownloadListener listener) {
            super(out);
            this.url = url;
            this.file = file;
            this.listener = listener;
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
            this.listener.progress(url, file, this.transferred);
        }
    }

}

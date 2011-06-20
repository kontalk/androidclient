package org.nuntius.client;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.entity.InputStreamEntity;
import org.nuntius.service.RequestListener;

public class ProgressInputStreamEntity extends InputStreamEntity {
    protected final RequestListener mListener;

    public ProgressInputStreamEntity(InputStream instream, long length,
            final RequestListener listener) {
        super(instream, length);
        mListener = listener;
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        super.writeTo(new CountingOutputStream(outstream, mListener));
    }

    private static final class CountingOutputStream extends FilterOutputStream {

        private final RequestListener listener;
        private long transferred;

        public CountingOutputStream(final OutputStream out,
                final RequestListener listener) {
            super(out);
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
            this.listener.uploadProgress(this.transferred);
        }
    }

}

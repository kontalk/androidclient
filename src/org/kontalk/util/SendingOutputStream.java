package org.kontalk.util;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.kontalk.service.ClientThread;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;


/**
 * An {@link OutputStream} wrapper that keeps count of the bytes it's sending.
 * @author Daniele Ricci
 */
public class SendingOutputStream extends FilterOutputStream {
    private final ClientThread client;
    private final RequestJob job;
    private final RequestListener listener;
    private long transferred;

    public SendingOutputStream(final OutputStream out, final ClientThread client,
            final RequestJob job, final RequestListener listener) {
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

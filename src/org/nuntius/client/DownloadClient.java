package org.nuntius.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import android.content.Context;


/**
 * A client for the download service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class DownloadClient extends AbstractClient {

    public DownloadClient(Context context, EndpointServer server, String token) {
        super(context, server, token);
    }

    /** Downloads to a {@link File}. */
    public void download(String filename, File file) throws IOException {
        OutputStream out = new FileOutputStream(file);
        download(filename, out);
        out.close();
    }

    private void download(String filename, OutputStream out) throws IOException {
        currentRequest = mServer.prepareDownload(mAuthToken, filename);
        HttpResponse response = mServer.execute(currentRequest);

        // HTTP/1.1 200 OK -- other codes should throw Exceptions
        if (response.getStatusLine().getStatusCode() != 200) {
            HttpEntity entity = response.getEntity();
            if (entity != null)
                entity.writeTo(out);
        }
    }

}

package org.kontalk.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.kontalk.service.DownloadListener;
import org.kontalk.util.ProgressOutputStreamEntity;

import android.content.Context;
import android.util.Log;


/**
 * A client for the download service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class DownloadClient extends AbstractClient {
    private static final String TAG = DownloadClient.class.getSimpleName();

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    public DownloadClient(Context context, EndpointServer server, String token) {
        super(context, server, token);
    }

    /** Downloads to a directory represented by a {@link File} object,
     * determining the file name from the Content-Disposition header. */
    public void downloadAutofilename(String url, File base, DownloadListener listener) throws IOException {
        _download(url, base, listener);
    }

    private void _download(String url, File base, DownloadListener listener) throws IOException {
        currentRequest = mServer.prepareURLDownload(mAuthToken, url);
        HttpResponse response = mServer.execute(currentRequest);

        // HTTP/1.1 200 OK -- other codes should throw Exceptions
        if (response.getStatusLine().getStatusCode() == 200) {
            Header disp = response.getFirstHeader("Content-Disposition");
            if (disp != null) {
                String name = parseContentDisposition(disp.getValue());
                // TODO should check for content-disposition parsing here
                // and choose another filename if necessary

                HttpEntity _entity = response.getEntity();
                if (name != null && _entity != null) {
                    // we need to wrap the entity to monitor the download progress
                    File destination = new File(base, name);
                    ProgressOutputStreamEntity entity = new ProgressOutputStreamEntity(_entity, url, destination, listener);
                    FileOutputStream out = new FileOutputStream(destination);
                    entity.writeTo(out);
                    out.close();
                    return;
                }
            }
        }

        Log.e(TAG, "invalid response: " + response.getStatusLine().getStatusCode());
        HttpEntity entity = response.getEntity();
        Log.e(TAG, EntityUtils.toString(entity));
    }

    /*
     * Parse the Content-Disposition HTTP Header. The format of the header
     * is defined here: http://www.w3.org/Protocols/rfc2616/rfc2616-sec19.html
     * This header provides a filename for content that is going to be
     * downloaded to the file system. We only support the attachment type.
     */
    private static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) {
                return m.group(1);
            }
        }
        catch (IllegalStateException ex) {
            // This function is defined as returning null when it can't parse the header
        }
        return null;
    }

}

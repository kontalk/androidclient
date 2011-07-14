package org.kontalk.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;

import android.content.Context;


/**
 * A client for the download service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class DownloadClient extends AbstractClient {

    /** Regex used to parse content-disposition headers */
    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern
            .compile("attachment;\\s*filename\\s*=\\s*\"([^\"]*)\"");

    public DownloadClient(Context context, EndpointServer server, String token) {
        super(context, server, token);
    }

    /** Downloads to a {@link File}. */
    public void download(String filename, File file) throws IOException {
        OutputStream out = new FileOutputStream(file);
        _download(filename, out);
        out.close();
    }

    /** Downloads to a {@link File}. */
    public void downloadAutofilename(String filename, File base) throws IOException {
        _download(filename, base);
    }

    private void _download(String filename, OutputStream out) throws IOException {
        currentRequest = mServer.prepareDownload(mAuthToken, filename);
        HttpResponse response = mServer.execute(currentRequest);

        // HTTP/1.1 200 OK -- other codes should throw Exceptions
        if (response.getStatusLine().getStatusCode() != 200) {
            HttpEntity entity = response.getEntity();
            if (entity != null)
                entity.writeTo(out);
        }
    }

    private void _download(String filename, File base) throws IOException {
        currentRequest = mServer.prepareDownload(mAuthToken, filename);
        HttpResponse response = mServer.execute(currentRequest);

        // HTTP/1.1 200 OK -- other codes should throw Exceptions
        if (response.getStatusLine().getStatusCode() != 200) {
            Header disp = response.getFirstHeader("Content-Disposition");
            if (disp != null) {
                String name = parseContentDisposition(disp.getValue());
                // TODO should check for content-disposition parsing here
                // and choose another filename if necessary
                HttpEntity entity = response.getEntity();
                if (name != null && entity != null) {
                    FileOutputStream out = new FileOutputStream(
                            new File(base, name));
                    entity.writeTo(out);
                    out.close();
                }
            }
        }
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

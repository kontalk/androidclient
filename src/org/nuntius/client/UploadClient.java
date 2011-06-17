package org.nuntius.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.Header;
import org.apache.http.HttpResponse;

import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;


/**
 * A client for the upload service.
 * @author Daniele Ricci
 * @version 1.0
 */
public class UploadClient extends AbstractClient {

    public UploadClient(EndpointServer server, String token) {
        super(server, token);
    }

    /** Uploads a media {@link Uri}. */
    public String upload(ContentResolver resolver, Uri uri) throws IOException {
        // get file length
        AssetFileDescriptor stat = resolver.openAssetFileDescriptor(uri, "r");
        long length = stat.getLength();
        stat.close();

        InputStream in = resolver.openInputStream(uri);
        String name = upload(in, length);
        in.close();
        return name;
    }

    /** Uploads a {@link File}. */
    public String upload(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        String name = upload(in, file.length());
        in.close();
        return name;
    }

    private String upload(InputStream in, long length) throws IOException {
        currentRequest = mServer.prepareUpload(mAuthToken, in, length);
        HttpResponse response = mServer.execute(currentRequest);

        // HTTP/1.1 201 Created :)
        if (response.getStatusLine().getStatusCode() == 201) {
            Header[] fn = response.getHeaders(EndpointServer.HEADER_FILENAME);
            if (fn != null && fn.length > 0)
                return fn[0].getValue();
        }

        return null;
    }

}

package org.kontalk.xmpp.upload;

import java.io.IOException;

import org.kontalk.xmpp.service.ProgressListener;

import android.net.Uri;


/**
 * Public interface for upload service connection classes.
 * @author Daniele Ricci
 */
public interface UploadConnection {

    public void abort();

    public String upload(Uri uri, String mime, boolean encrypt, ProgressListener listener)
        throws IOException;

}

package org.kontalk.service;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.DownloadClient;
import org.kontalk.client.EndpointServer;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MediaStorage;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;

public class DownloadService extends IntentService {

    private DownloadClient mDownloadClient;

    public DownloadService() {
        super(DownloadService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (mDownloadClient == null) {
            EndpointServer server = MessagingPreferences.getEndpointServer(this);
            String token = Authenticator.getDefaultAccountToken(this);
            mDownloadClient = new DownloadClient(this, server, token);
        }

        /*
         * TODO here uri should be actually the request filename string,
         * but fetchUri in the database is a complete URL. WTF?!?
         */
        Uri uri = intent.getData();
        mDownloadClient.downloadAutofilename(uri.toString(), MediaStorage.MEDIA_ROOT);
    }

}

package org.nuntius.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.nuntius.authenticator.Authenticator;
import org.nuntius.client.EndpointServer;
import org.nuntius.client.RequestClient;
import org.nuntius.ui.MessagingPreferences;

import android.app.IntentService;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

public class MessageSenderService extends IntentService {
    private static final String TAG = MessageSenderService.class.getSimpleName();

    public static final String MSG_GROUP = "org.nuntius.message.group";
    public static final String MSG_MIME = "org.nuntius.message.mime";
    public static final String MSG_CONTENT = "org.nuntius.message.content";
    public static final String MSG_FILENAME = "org.nuntius.message.filename";

    public MessageSenderService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // get the endpoint server from configuration
        EndpointServer endpoint = new EndpointServer(MessagingPreferences.getServerURI(this));
        // get the authentication token
        String token = Authenticator.getDefaultAccountToken(this);
        Log.i(TAG, "using token: " + token);

        String[] group = intent.getStringArrayExtra(MSG_GROUP);
        String mime = intent.getStringExtra(MSG_MIME);
        byte[] content = intent.getByteArrayExtra(MSG_CONTENT);
        PutTask t;

        if (content != null)
            t = new PutTask(endpoint, token, group, mime, content);
        else {
            t = new PutTask(endpoint, token, group, mime,
                    Uri.fromFile(new File(intent.getStringExtra(MSG_FILENAME))));
        }

        t.run();
    }

    /** An {@link AsyncTask} to PUT a message to the postmessage service. */
    private final class PutTask implements Runnable {
        private final EndpointServer mServer;
        private final String mAuthToken;
        private final String[] mGroup;
        private final String mMime;
        private byte[] mContent;
        private Uri mUri;

        private PutTask(EndpointServer server, String token, String[] group, String mime) {
            mServer = server;
            mAuthToken = token;
            mGroup = group;
            mMime = mime;
        }

        public PutTask(EndpointServer server, String token, String[] group, String mime, byte[] content) {
            this(server, token, group, mime);
            mContent = content;
        }

        public PutTask(EndpointServer server, String token, String[] group, String mime, Uri uri) {
            this(server, token, group, mime);
            mUri = uri;
        }

        @Override
        public void run() {
            RequestClient client = new RequestClient(mServer, mAuthToken);
            /*
            if (mContent != null)
                client.message(mGroup, mMime, mContent);
            */

            long length;
            InputStream in;

            if (mContent != null) {
                length = mContent.length;
                in = new ByteArrayInputStream(mContent);
            }
            else {
                try {
                    AssetFileDescriptor stat = getContentResolver().openAssetFileDescriptor(mUri, "r");
                    length = stat.getLength();
                    in = getContentResolver().openInputStream(mUri);
                }
                catch (IOException e) {
                    Log.e(TAG, "error opening file: " + mUri, e);
                    return;
                }
            }

            try {
                mServer.prepareMessage(mAuthToken, mGroup, mMime, in, length);
            }
            catch (Exception e) {
                Log.e(TAG, "error sending message", e);
            }
        }

    }
}

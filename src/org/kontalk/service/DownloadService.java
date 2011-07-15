package org.kontalk.service;

import java.io.File;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.AbstractMessage;
import org.kontalk.client.DownloadClient;
import org.kontalk.client.EndpointServer;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.ui.ConversationList;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MediaStorage;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;


public class DownloadService extends IntentService implements DownloadListener {
    private static final String TAG = DownloadService.class.getSimpleName();

    public static final String ACTION_DOWNLOAD_URL = "org.kontalk.action.DOWNLOAD_URL";

    private static final int NOTIFICATION_ID = 103;

    private Notification mCurrentNotification;
    private long mTotalBytes;

    private String messageId;
    private DownloadClient mDownloadClient;

    public DownloadService() {
        super(DownloadService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // unknown action
        if (!ACTION_DOWNLOAD_URL.equals(intent.getAction())) return;

        if (mDownloadClient == null) {
            EndpointServer server = MessagingPreferences.getEndpointServer(this);
            String token = Authenticator.getDefaultAccountToken(this);
            mDownloadClient = new DownloadClient(this, server, token);
        }

        Uri uri = intent.getData();
        messageId = intent.getStringExtra(AbstractMessage.MSG_ID);

        try {
            // make sure storage directory is present
            MediaStorage.MEDIA_ROOT.mkdirs();

            // download content
            // TEST for testing in emulator...
            mDownloadClient.downloadAutofilename(
                    uri.toString().replaceFirst("localhost", "10.0.2.2"),
                    MediaStorage.MEDIA_ROOT, this);
            //mDownloadClient.downloadAutofilename(uri.toString(), MediaStorage.MEDIA_ROOT, this);
        }
        catch (Exception e) {
            error(uri.toString(), null, e);
        }
    }

    public void startForeground(long totalBytes) {
        Log.w(TAG, "starting foreground progress notification");
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ConversationList.class);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), NOTIFICATION_ID, ni, Intent.FLAG_ACTIVITY_NEW_TASK);

        mCurrentNotification = new Notification(R.drawable.icon, "Downloading attachment...", System.currentTimeMillis());
        mCurrentNotification.contentIntent = pi;
        mCurrentNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        foregroundNotification(0);
        startForeground(NOTIFICATION_ID, mCurrentNotification);
    }

    private void foregroundNotification(int progress) {
        mCurrentNotification.contentView = new RemoteViews(getApplicationContext().getPackageName(), R.layout.progress_notification);
        mCurrentNotification.contentView.setTextViewText(R.id.title, "Downloading attachment...");
        mCurrentNotification.contentView.setTextViewText(R.id.progress_text, String.format("%d%%", progress));
        mCurrentNotification.contentView.setProgressBar(R.id.progress_bar, 100, progress, false);
    }

    public void stopForeground() {
        stopForeground(true);
        mCurrentNotification = null;
        mTotalBytes = 0;
    }

    @Override
    public void start(String url, File destination, long length) {
        startForeground(length);
    }

    @Override
    public void completed(String url, File destination) {
        Log.i(TAG, "download complete");
        stopForeground();
        // TODO download complete notification

        // update messages.localUri
        ContentValues values = new ContentValues();
        values.put(Messages.LOCAL_URI, Uri.fromFile(destination).toString());
        values.put(Messages.FETCHED, true);
        getContentResolver().update(Messages.getUri(messageId), values, null, null);
    }

    @Override
    public void error(String url, File destination, Throwable exc) {
        // TODO
        Log.e(TAG, "download error", exc);
    }

    @Override
    public void progress(String url, File destination, long bytes) {
        Log.i(TAG, bytes + " bytes received");
        if (mCurrentNotification != null) {
            int progress = (int)((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID, mCurrentNotification);
        }

    }

}

package org.kontalk.service;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOADING;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_OK;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_ERROR;

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
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.RemoteViews;


public class DownloadService extends IntentService implements DownloadListener {
    private static final String TAG = DownloadService.class.getSimpleName();

    public static final String ACTION_DOWNLOAD_URL = "org.kontalk.action.DOWNLOAD_URL";

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
        Log.d(TAG, "starting foreground progress notification");
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ConversationList.class);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOADING, ni, Intent.FLAG_ACTIVITY_NEW_TASK);

        mCurrentNotification = new Notification(R.drawable.icon, "Downloading attachment...", System.currentTimeMillis());
        mCurrentNotification.contentIntent = pi;
        mCurrentNotification.flags |= Notification.FLAG_ONGOING_EVENT;

        foregroundNotification(0);
        startForeground(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);
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
    public void completed(String url, String mime, File destination) {
        Log.d(TAG, "download complete");
        stopForeground();

        Uri uri = Uri.fromFile(destination);

        // detect mime type if not available
        if (mime == null)
            mime = getContentResolver().getType(uri);

        // create intent for download complete notification
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mime);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_OK, i, Intent.FLAG_ACTIVITY_NEW_TASK);

        // create notification
        Notification no = new Notification(R.drawable.icon_stat,
                getString(R.string.notify_ticker_download_completed),
                System.currentTimeMillis());
        no.setLatestEventInfo(getApplicationContext(),
                getString(R.string.notify_title_download_completed),
                getString(R.string.notify_text_download_completed), pi);
        no.flags |= Notification.FLAG_AUTO_CANCEL;

        // notify!!
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID_DOWNLOAD_OK, no);

        // update messages.localUri
        ContentValues values = new ContentValues();
        values.put(Messages.LOCAL_URI, uri.toString());
        values.put(Messages.FETCHED, true);
        getContentResolver().update(Messages.getUri(messageId), values, null, null);
    }

    @Override
    public void error(String url, File destination, Throwable exc) {
        Log.e(TAG, "download error", exc);

        // create intent for download error notification
        Intent i = new Intent(this, ConversationList.class);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_ERROR, i, Intent.FLAG_ACTIVITY_NEW_TASK);

        // create notification
        Notification no = new Notification(R.drawable.icon_stat,
                getString(R.string.notify_ticker_download_error),
                System.currentTimeMillis());
        no.setLatestEventInfo(getApplicationContext(),
                getString(R.string.notify_title_download_error),
                getString(R.string.notify_text_download_error), pi);
        no.flags |= Notification.FLAG_AUTO_CANCEL;

        // notify!!
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID_DOWNLOAD_ERROR, no);
    }

    @Override
    public void progress(String url, File destination, long bytes) {
        //Log.v(TAG, bytes + " bytes received");
        if (mCurrentNotification != null) {
            int progress = (int)((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);
        }

    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.service;

/*
 * TODO instead of using a notification ID per type, use a notification ID per
 * upload.
 */

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.provider.MessagesProviderUtils;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.ConversationsActivity;
import org.kontalk.ui.ProgressNotificationBuilder;
import org.kontalk.upload.HTPPFileUploadConnection;
import org.kontalk.upload.UploadConnection;
import org.kontalk.util.MediaStorage;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_UPLOADING;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_UPLOAD_ERROR;


/**
 * Attachment upload service.
 * TODO implement multiple concurrent uploads
 *
 * @author Daniele Ricci
 */
public class UploadService extends IntentService implements ProgressListener {
    private static final String TAG = MessageCenterService.TAG;

    /**
     * A map to avoid duplicate uploads.
     */
    private static final Map<String, Long> queue = new LinkedHashMap<>();

    public static final String ACTION_UPLOAD = "org.kontalk.action.UPLOAD";
    public static final String ACTION_UPLOAD_ABORT = "org.kontalk.action.UPLOAD_ABORT";

    /**
     * Message database ID. Use with ACTION_UPLOAD.
     */
    public static final String EXTRA_DATABASE_ID = "org.kontalk.upload.DATABASE_ID";
    /**
     * Message ID. Use with ACTION_UPLOAD.
     */
    public static final String EXTRA_MESSAGE_ID = "org.kontalk.upload.MESSAGE_ID";
    /**
     * URL to post to. Use with ACTION_UPLOAD.
     */
    public static final String EXTRA_POST_URL = "org.kontalk.upload.POST_URL";
    /**
     * URL to fetch from. Use with ACTION_UPLOAD.
     */
    public static final String EXTRA_GET_URL = "org.kontalk.upload.GET_URL";
    /**
     * User(s) to send to.
     */
    public static final String EXTRA_USER = "org.kontalk.upload.USER";
    /**
     * Group JID.
     */
    public static final String EXTRA_GROUP = "org.kontalk.upload.GROUP";
    /**
     * Media MIME type.
     */
    public static final String EXTRA_MIME = "org.kontalk.upload.MIME";
    /**
     * Preview file path.
     */
    public static final String EXTRA_PREVIEW_PATH = "org.kontalk.upload.PREVIEW_PATH";
    /**
     * Encryption flag.
     */
    public static final String EXTRA_ENCRYPT = "org.kontalk.upload.ENCRYPT";
    /**
     * Delete local file after sending attempt.
     */
    public static final String EXTRA_DELETE_ORIGINAL = "org.kontalk.upload.DELETE_ORIGINAL";

    public static final String EXTRA_WIDTH = "org.kontalk.upload.WIDTH";
    public static final String EXTRA_HEIGHT = "org.kontalk.upload.HEIGHT";
    // Intent data is the local file Uri

    private ProgressNotificationBuilder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    // data about the upload currently being processed
    private Notification mCurrentNotification;
    private long mTotalBytes;

    private long mMessageId;
    private UploadConnection mConn;
    private boolean mCanceled;

    public UploadService() {
        super(UploadService.class.getSimpleName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // crappy firmware - as per docs, intent can't be null in this case
        if (intent != null) {
            if (mNotificationManager == null)
                mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (ACTION_UPLOAD_ABORT.equals(intent.getAction())) {
                String filename = intent.getData().toString();
                // TODO check for race conditions on queue
                Long msgId = queue.get(filename);
                if (msgId != null) {
                    // interrupt worker if running
                    if (msgId == mMessageId) {
                        mConn.abort();
                        mCanceled = true;
                    }
                    // remove from queue - will never be processed
                    else
                        queue.remove(filename);
                }
                return START_NOT_STICKY;
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // crappy firmware - as per docs, intent can't be null in this case
        if (intent == null)
            return;
        // check for unknown action
        if (!ACTION_UPLOAD.equals(intent.getAction()))
            return;

        // local file to upload
        Uri file = intent.getData();
        String filename = file.toString();
        // message database id
        long databaseId = intent.getLongExtra(EXTRA_DATABASE_ID, 0);
        // message id
        String msgId = intent.getStringExtra(EXTRA_MESSAGE_ID);
        // url to post to
        String url = intent.getStringExtra(EXTRA_POST_URL);
        // url to fetch from (will be requested to the connection if null)
        String fetchUrl = intent.getStringExtra(EXTRA_GET_URL);
        // group JID
        String groupJid = intent.getStringExtra(EXTRA_GROUP);
        // user(s) to send message to

        int width = intent.getIntExtra(EXTRA_WIDTH, 0);
        int height = intent.getIntExtra(EXTRA_HEIGHT, 0);

        String[] to;
        if (groupJid != null) {
            to = intent.getStringArrayExtra(EXTRA_USER);
        }
        else {
            to = new String[]{intent.getStringExtra(EXTRA_USER)};
        }
        // media mime type
        String mime = intent.getStringExtra(EXTRA_MIME);
        // preview file path
        String previewPath = intent.getStringExtra(EXTRA_PREVIEW_PATH);
        // encryption flag
        boolean encrypt = intent.getBooleanExtra(EXTRA_ENCRYPT, false);
        // delete original
        boolean deleteOriginal = intent.getBooleanExtra(EXTRA_DELETE_ORIGINAL, false);

        // check if upload has already been queued
        if (queue.get(filename) != null) return;

        try {
            // notify user about upload immediately
            long length = MediaStorage.getLength(this, file);
            Log.v(TAG, "file size is " + length + " bytes");

            mTotalBytes = length;
            startForeground(0);

            mCanceled = false;

            // TODO used class here should be decided by the caller
            mConn = new HTPPFileUploadConnection(this, url);

            mMessageId = databaseId;
            queue.put(filename, mMessageId);

            // upload content
            String mediaUrl = mConn.upload(file, length, mime, encrypt, to, this);
            if (mediaUrl == null)
                mediaUrl = fetchUrl;
            Log.d(TAG, "uploaded with media URL: " + mediaUrl);

            // update message fetch_url
            MessagesProvider.uploaded(this, databaseId, mediaUrl);

            // send message with fetch url to server
            if (groupJid != null) {
                MessageCenterService.sendGroupUploadedMedia(this, groupJid, to,
                    mime, file, width, height, length, previewPath, mediaUrl, encrypt, databaseId, msgId);
            }
            else {
                MessageCenterService.sendUploadedMedia(this, to[0], mime, file,
                    width, height, length, previewPath, mediaUrl, encrypt, databaseId, msgId);
            }

            // end operations
            completed();
        }
        catch (Exception e) {
            error(url, null, e);
        }
        finally {
            // only file uri are supported for delete
            if (deleteOriginal && "file".equals(file.getScheme()))
                new File(file.getPath()).delete();

            queue.remove(filename);
            mMessageId = 0;
        }
    }

    public void startForeground(long totalBytes) {
        Log.d(TAG, "starting foreground progress notification");

        Intent ni = new Intent(getApplicationContext(), ConversationsActivity.class);
        ni.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
            NOTIFICATION_ID_UPLOADING, ni, 0);

        if (mNotificationBuilder == null) {
            mNotificationBuilder = new ProgressNotificationBuilder(getApplicationContext(),
                R.layout.progress_notification,
                getString(R.string.sending_message),
                R.drawable.ic_stat_notify,
                pi);
        }

        // if we don't know the content length yet, start an interminate progress
        foregroundNotification(totalBytes > 0 ? 0 : -1);
        startForeground(NOTIFICATION_ID_UPLOADING, mCurrentNotification);
    }

    private void foregroundNotification(int progress) {
        mCurrentNotification = mNotificationBuilder
            .progress(progress,
                R.string.attachment_upload,
                R.string.sending_message)
            .build();
    }

    public void stopForeground() {
        stopForeground(true);
        mCurrentNotification = null;
        mTotalBytes = 0;
    }

    @Override
    public void start(UploadConnection conn) {
        startForeground(mTotalBytes);
    }

    public void completed() {
        stopForeground();

        // upload completed - no need for notification

        // TODO broadcast upload completed intent
    }

    public void error(String url, File destination, Throwable exc) {
        Log.e(TAG, "upload error", exc);
        stopForeground();
        if (!mCanceled) {
            ReportingManager.logException(exc);
            errorNotification(getString(R.string.notify_ticker_upload_error),
                getString(R.string.notify_text_upload_error));
        }
    }

    private void errorNotification(String ticker, String text) {
        errorNotification(this, mNotificationManager, ticker, text);
    }

    public static void errorNotification(Context context, String ticker, String text) {
        errorNotification(context,
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)),
            ticker, text);
    }

    private static void errorNotification(Context context, NotificationManager nm, String ticker, String text) {
        // create intent for upload error notification
        Intent i = new Intent(context, ConversationsActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context.getApplicationContext(),
            NOTIFICATION_ID_UPLOAD_ERROR, i, 0);

        // create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context.getApplicationContext())
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(context.getString(R.string.notify_title_upload_error))
            .setContentText(text)
            .setTicker(ticker)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true);


        // notify!!
        nm.notify(NOTIFICATION_ID_UPLOAD_ERROR, builder.build());
    }

    @Override
    public void progress(UploadConnection conn, long bytes) {
        if (mCanceled || !MessagesProviderUtils.exists(this, mMessageId)) {
            Log.v(TAG, "upload canceled or message deleted - aborting");
            mConn.abort();
            mCanceled = true;
        }

        if (mCurrentNotification != null) {
            int progress = (int) ((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            mNotificationManager.notify(NOTIFICATION_ID_UPLOADING, mCurrentNotification);
        }
    }

    public static boolean isQueued(String url) {
        return queue.containsKey(url);
    }
}

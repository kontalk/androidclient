/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_UPLOADING;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_UPLOAD_ERROR;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.provider.MessagesProvider;
import org.kontalk.ui.ConversationList;
import org.kontalk.ui.ProgressNotificationBuilder;
import org.kontalk.upload.KontalkBoxUploadConnection;
import org.kontalk.upload.UploadConnection;
import org.kontalk.util.MediaStorage;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;


/**
 * Attachment upload service.
 * TODO implement multiple concurrent uploads
 * @author Daniele Ricci
 */
public class UploadService extends IntentService implements ProgressListener {
    private static final String TAG = UploadService.class.getSimpleName();
    /** A map to avoid duplicate uploads. */
    private static final Map<String, Long> queue = new LinkedHashMap<String, Long>();

    public static final String ACTION_UPLOAD = "org.kontalk.action.UPLOAD";
    public static final String ACTION_UPLOAD_ABORT = "org.kontalk.action.UPLOAD_ABORT";

    /** Message database ID. Use with ACTION_UPLOAD. */
    public static final String EXTRA_MESSAGE_ID = "org.kontalk.upload.MESSAGE_ID";
    /** URL to post to. Use with ACTION_UPLOAD. */
    public static final String EXTRA_POST_URL = "org.kontalk.upload.POST_URL";
    /** User id to send to. */
    public static final String EXTRA_USER_ID = "org.kontalk.upload.USER_ID";
    /** Media MIME type. */
    public static final String EXTRA_MIME = "org.kontalk.upload.MIME";
    /** Preview file path. */
    public static final String EXTRA_PREVIEW_PATH = "org.kontalk.upload.PREVIEW_PATH";
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
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (ACTION_UPLOAD_ABORT.equals(intent.getAction())) {
            String filename = intent.getData().toString();
            // TODO check for race conditions on queue
            Long msgId = queue.get(filename);
            if (msgId != null) {
                // interrupt worker if running
                if (msgId.longValue() == mMessageId) {
                    mConn.abort();
                    mCanceled = true;
                }
                // remove from queue - will never be processed
                else
                    queue.remove(filename);
            }
            return START_NOT_STICKY;
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // check for unknown action
        if (!ACTION_UPLOAD.equals(intent.getAction())) return;

        // local file to upload
        Uri file = intent.getData();
        String filename = file.toString();
        // message id
        long msgId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0);
        // url to post to
        String url = intent.getStringExtra(EXTRA_POST_URL);
        // user to send message to
        String userId = intent.getStringExtra(EXTRA_USER_ID);
        // media mime type
        String mime = intent.getStringExtra(EXTRA_MIME);
        // preview file path
        String previewPath = intent.getStringExtra(EXTRA_PREVIEW_PATH);

        // check if upload has already been queued
        if (queue.get(filename) != null) return;

        try {
            // notify user about upload immediately
            long length = MediaStorage.getLength(this, file);
            Log.v(TAG, "file size is " + length + " bytes");

            mTotalBytes = length;
            startForeground(0);

            mCanceled = false;

            if (mConn == null) {
                PersonalKey key = ((Kontalk) getApplication()).getPersonalKey();
                // TODO used class here should be decided by the caller
                mConn = new KontalkBoxUploadConnection(this, url,
                    key.getBridgePrivateKey(), key.getBridgeCertificate());
            }

            mMessageId = msgId;
            queue.put(filename, mMessageId);

            // upload content
            String mediaUrl = mConn.upload(file, mime, false, this);
            Log.d(TAG, "uploaded with media URL: " + mediaUrl);

            // update message fetch_url
            MessagesProvider.uploaded(this, msgId, mediaUrl);

            // send message with fetch url to server
            MessageCenterService.sendUploadedMedia(this, userId, mime, file, length, previewPath, mediaUrl, msgId);

            // end operations
            completed();
        }
        catch (Exception e) {
            error(url, null, e);
        }
        finally {
            queue.remove(filename);
            mMessageId = 0;
        }
    }

    public void startForeground(long totalBytes) {
        Log.d(TAG, "starting foreground progress notification");

        Intent ni = new Intent(getApplicationContext(), ConversationList.class);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_UPLOADING, ni, Intent.FLAG_ACTIVITY_NEW_TASK);

        if (mNotificationBuilder == null) {
            mNotificationBuilder = new ProgressNotificationBuilder(getApplicationContext(),
                R.layout.progress_notification,
                getString(R.string.sending_message),
                R.drawable.stat_notify,
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
        if (!mCanceled)
            errorNotification(getString(R.string.notify_ticker_upload_error),
                getString(R.string.notify_text_upload_error));
    }

    private void errorNotification(String ticker, String text) {
        // create intent for upload error notification
        Intent i = new Intent(this, ConversationList.class);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_UPLOAD_ERROR, i, Intent.FLAG_ACTIVITY_NEW_TASK);

        // create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
            .setSmallIcon(R.drawable.stat_notify)
            .setContentTitle(getString(R.string.notify_title_upload_error))
            .setContentText(text)
            .setTicker(ticker)
            .setContentIntent(pi)
            .setAutoCancel(true);


        // notify!!
        mNotificationManager.notify(NOTIFICATION_ID_UPLOAD_ERROR, builder.build());
    }

    @Override
    public void progress(UploadConnection conn, long bytes) {
        if (mCanceled || !MessagesProvider.exists(this, mMessageId)) {
            Log.v(TAG, "upload canceled or message deleted - aborting");
            mConn.abort();
            mCanceled = true;
        }

        //Log.v(TAG, "bytes = " + bytes);
        if (mCurrentNotification != null) {
            int progress = (int)((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            mNotificationManager.notify(NOTIFICATION_ID_UPLOADING, mCurrentNotification);
        }

        Thread.yield();
    }

    public static boolean isQueued(String url) {
        return queue.containsKey(url);
    }
}

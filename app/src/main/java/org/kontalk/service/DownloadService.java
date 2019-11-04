/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.greenrobot.eventbus.EventBus;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.client.ClientHTTPConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.DecryptException;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.ConversationsActivity;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.ui.ProgressNotificationBuilder;
import org.kontalk.util.EventBusIndex;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.Permissions;
import org.kontalk.util.Preferences;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOADING;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_ERROR;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_OK;


/**
 * The attachment download service.
 * TODO implement multiple downloads in queue or in parallel
 * @author Daniele Ricci
 */
public class DownloadService extends JobIntentService implements DownloadListener {
    private static final String TAG = MessageCenterService.TAG;

    private static final int JOB_ID = 1001;

    // used only for events to UI
    private static final EventBus BUS;

    static {
        BUS = EventBus.builder()
            // TODO .logger(...)
            .addIndex(new EventBusIndex())
            .throwSubscriberException(BuildConfig.DEBUG)
            .logNoSubscriberMessages(BuildConfig.DEBUG)
            .build();
    }

    /** A map to avoid duplicate downloads. */
    private static final Map<String, Long> sQueue = new LinkedHashMap<>();

    private static final String ACTION_DOWNLOAD_URL = "org.kontalk.action.DOWNLOAD_URL";
    private static final String ACTION_DOWNLOAD_ABORT = "org.kontalk.action.DOWNLOAD_ABORT";

    private static final String EXTRA_MSG_ID = "org.kontalk.download.message.id";
    private static final String EXTRA_MSG_SERVER_ID = "org.kontalk.download.message.serverId";
    private static final String EXTRA_MSG_SENDER = "org.kontalk.message.download.sender";
    private static final String EXTRA_MSG_CONVERSATION = "org.kontalk.message.download.context";
    private static final String EXTRA_MSG_MIME = "org.kontalk.message.download.mime";
    private static final String EXTRA_MSG_TIMESTAMP = "org.kontalk.message.download.timestamp";
    private static final String EXTRA_MSG_ENCRYPTED = "org.kontalk.message.download.encrypted";
    private static final String EXTRA_MSG_COMPRESS = "org.kontalk.message.download.compress";
    private static final String EXTRA_NOTIFY = "org.kontalk.download.notify";

    private ProgressNotificationBuilder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    // data about the download currently being processed
    private Notification mCurrentNotification;
    private long mTotalBytes;

    private long mMessageId;
    private String mPeer;
    private String mConversation;
    private boolean mEncrypted;
    private boolean mNotify;

    private ClientHTTPConnection mDownloadClient;
    private boolean mCanceled;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String action = intent.getAction();

        if (ACTION_DOWNLOAD_URL.equals(action)) {
            onDownloadURL(intent.getData(), intent.getExtras());
        }
        else if (ACTION_DOWNLOAD_ABORT.equals(intent.getAction())) {
            onDownloadAbort(intent.getData());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDownloadClient != null) {
            mDownloadClient.close();
        }
    }

    private void onDownloadURL(Uri uri, Bundle args) {
        String url = uri.toString();

        // check if download has already been queued
        if (sQueue.get(url) != null) return;

        // notify user about download immediately
        startForeground(0);
        mCanceled = false;

        mNotify = args.getBoolean(EXTRA_NOTIFY, true);

        if (mDownloadClient == null) {
            mDownloadClient = new ClientHTTPConnection(this);
        }

        try {
            // check if external storage is available
            if (!MediaStorage.isExternalStorageAvailable()) {
                errorNotification(getString(R.string.notify_ticker_external_storage),
                    getString(R.string.notify_text_external_storage));
                return;
            }

            // needed in case the next if triggers
            mConversation = args.getString(EXTRA_MSG_CONVERSATION);

            // check for write permission
            // an event will be sent to the compose activity if present
            if (!Permissions.canWriteExternalStorage(this)) {
                writePermissionDenied();
                return;
            }

            mMessageId = args.getLong(EXTRA_MSG_ID, 0);
            mPeer = args.getString(EXTRA_MSG_SENDER);
            mEncrypted = args.getBoolean(EXTRA_MSG_ENCRYPTED, false);
            sQueue.put(url, mMessageId);

            Date date;
            long timestamp = args.getLong(EXTRA_MSG_TIMESTAMP);
            if (timestamp > 0)
                date = new Date(timestamp);
            else
                date = new Date();

            String mime = args.getString(EXTRA_MSG_MIME);
            // this will be used if the server doesn't provide one
            // if the server provides a filename, only the path will be used
            File defaultFile = CompositeMessage.getIncomingFile(mime, date);
            if (defaultFile == null) {
                defaultFile = MediaStorage.getIncomingFile(date, "bin");
            }

            BUS.post(new DownloadStarted(mMessageId));

            // download content
            mDownloadClient.downloadAutofilename(url, defaultFile, date, this);
        }
        catch (Exception e) {
            error(url, null, e);
        }
        finally {
            sQueue.remove(url);
            mMessageId = 0;
            mPeer = null;
        }
    }

    void onDownloadAbort(Uri uri) {
        String url = uri.toString();
        Long msgId = sQueue.get(url);
        if (msgId != null) {
            // interrupt worker if running
            if (msgId == mMessageId) {
                mDownloadClient.abort();
                mCanceled = true;
            }
            // remove from queue - will never be processed
            else
                sQueue.remove(url);
        }
    }

    private void writePermissionDenied() {
        if (MessagingNotification.isPaused(mConversation)) {
            BUS.post(new WritePermissionDenied());
        }
        else {
            permissionDeniedNotification();
        }
    }

    public void startForeground(long totalBytes) {
        Log.d(TAG, "starting foreground progress notification");
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ConversationsActivity.class);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOADING, ni, 0);

        if (mNotificationBuilder == null) {
            mNotificationBuilder = new ProgressNotificationBuilder(getApplicationContext(),
                MessagingNotification.CHANNEL_MEDIA_DOWNLOAD,
                R.layout.progress_notification,
                getString(R.string.downloading_attachment),
                R.drawable.ic_stat_notify,
                pi);
        }

        // if we don't know the content length yet, start an interminate progress
        foregroundNotification(totalBytes > 0 ? 0 : -1);
        startForeground(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);
    }

    private void foregroundNotification(int progress) {
        mCurrentNotification = mNotificationBuilder
            .progress(progress,
                R.string.attachment_download,
                R.string.downloading_attachment)
            .build();
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
        Uri uri = Uri.fromFile(destination);

        boolean destinationEncrypted = mEncrypted;
        long destinationLength = -1;

        // encrypted file?
        if (mEncrypted) {
            mCurrentNotification = mNotificationBuilder
                .progress(-1,
                    R.string.attachment_download,
                    R.string.decrypting_attachment)
                .build();
            // send the updates to the notification manager
            mNotificationManager.notify(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);

            InputStream in = null;
            OutputStream out = null;
            try {
                EndpointServer server = Preferences.getEndpointServer(this);
                PersonalKey key = Kontalk.get().getPersonalKey();
                Coder coder = Keyring.getDecryptCoder(this, server, key, mPeer);
                if (coder != null) {
                    in = new FileInputStream(destination);

                    File outFile = new File(destination + ".new");
                    out = new FileOutputStream(outFile);
                    List<DecryptException> errors = new LinkedList<>();
                    coder.decryptFile(in, true, out, errors);

                    // TODO process errors

                    // delete old file and rename the decrypted one
                    destination.delete();
                    outFile.renameTo(destination);

                    // save this for later
                    destinationEncrypted = false;
                    destinationLength = destination.length();
                }
            }
            catch (Exception e) {
                Log.e(TAG, "decryption failed!", e);
                errorNotification(getString(R.string.notify_ticker_download_error),
                    getString(R.string.notify_text_decryption_error));
                return;
            }
            finally {
                try {
                    if (in != null)
                        in.close();
                }
                catch (IOException e) {
                    // ignored
                }
                try {
                    if (out != null)
                        out.close();
                }
                catch (IOException e) {
                    // ignored
                }
            }
        }

        // mark file as downloaded
        MessagesProviderClient.downloaded(this, mMessageId, uri,
            destinationEncrypted, destinationLength);

        // update media store
        MediaStorage.scanFile(this, destination, mime);

        // stop foreground
        stopForeground();

        // notify only if conversation is not open
        if (!MessagingNotification.isPaused(mConversation) && mNotify) {

            // detect mime type if not available
            if (mime == null)
                mime = getContentResolver().getType(uri);

            // create intent for download complete notification
            Intent i = new Intent(Intent.ACTION_VIEW);
            uri = MediaStorage.getWorldReadableUri(this, uri, i, true);
            i.setDataAndType(uri, mime);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_OK, i, 0);

            // create notification
            NotificationCompat.Builder builder = new NotificationCompat
                .Builder(getApplicationContext(), MessagingNotification.CHANNEL_MEDIA_DOWNLOAD)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(getString(R.string.notify_title_download_completed))
                .setContentText(getString(R.string.notify_text_download_completed))
                .setTicker(getString(R.string.notify_ticker_download_completed))
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setAutoCancel(true);

            // notify!!
            mNotificationManager.notify(NOTIFICATION_ID_DOWNLOAD_OK, builder.build());
        }
    }

    @Override
    public void error(String url, File destination, Throwable exc) {
        Log.e(TAG, "download error", exc);
        stopForeground();
        if (!mCanceled) {
            ReportingManager.logException(exc);
            errorNotification(getString(R.string.notify_ticker_download_error),
                getString(R.string.notify_text_download_error));
        }
    }

    private void errorNotification(String ticker, String text) {
        errorNotification(ticker, text, getString(R.string.notify_title_download_error), null);
    }

    private void errorNotification(String ticker, String text, String title, Intent baseIntent) {
        // create intent for download error notification
        Intent i = baseIntent != null ? baseIntent : new Intent(this, ConversationsActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_ERROR, i, 0);

        // create notification
        NotificationCompat.Builder builder = new NotificationCompat
            .Builder(getApplicationContext(), MessagingNotification.CHANNEL_MEDIA_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setTicker(ticker)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pi)
            .setAutoCancel(true);

        // notify!!
        mNotificationManager.notify(NOTIFICATION_ID_DOWNLOAD_ERROR, builder.build());
    }

    private void permissionDeniedNotification() {
        errorNotification(getString(R.string.notify_ticker_download_permission_denied_error),
            getString(R.string.notify_summary_download_permission_denied_error),
            getString(R.string.notify_title_download_permission_denied_error),
            ConversationsActivity.writeStoragePermissionRequest(this));
    }

    @Override
    public void progress(String url, File destination, long bytes) {
        if (mCurrentNotification != null) {
            int progress = (int) ((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            mNotificationManager.notify(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);
        }
    }

    public static EventBus bus() {
        return BUS;
    }

    public static boolean isQueued(String url) {
        return sQueue.containsKey(url);
    }

    public static void start(Context context, long databaseId, String sender,
            String mime, long timestamp, boolean encrypted, String url, String conversation, boolean notify) {
        Intent i = new Intent(context, DownloadService.class);
        i.setAction(ACTION_DOWNLOAD_URL);
        i.putExtra(EXTRA_MSG_ID, databaseId);
        i.putExtra(EXTRA_MSG_SENDER, sender);
        i.putExtra(EXTRA_MSG_CONVERSATION, conversation);
        i.putExtra(EXTRA_MSG_MIME, mime);
        i.putExtra(EXTRA_MSG_TIMESTAMP, timestamp);
        i.putExtra(EXTRA_MSG_ENCRYPTED, encrypted);
        i.putExtra(EXTRA_NOTIFY, notify);
        i.setData(Uri.parse(url));
        enqueueWork(context, DownloadService.class, JOB_ID, i);
    }

    public static void abort(Context context, Uri uri) {
        Intent i = new Intent(context, DownloadService.class);
        i.setAction(ACTION_DOWNLOAD_ABORT);
        i.setData(uri);
        enqueueWork(context, DownloadService.class, JOB_ID, i);
    }

    public static class WritePermissionDenied {
    }

    public static class DownloadStarted {
        public final long id;

        DownloadStarted(long id) {
            this.id = id;
        }
    }

}

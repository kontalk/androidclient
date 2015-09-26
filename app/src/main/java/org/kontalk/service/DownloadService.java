/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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
 * download.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.client.ClientHTTPConnection;
import org.kontalk.client.EndpointServer;
import org.kontalk.crypto.Coder;
import org.kontalk.crypto.DecryptException;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.MyMessages.Messages;
import org.kontalk.provider.UsersProvider;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.ui.ConversationsActivity;
import org.kontalk.ui.MessagingNotification;
import org.kontalk.ui.ProgressNotificationBuilder;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.Preferences;
import org.kontalk.util.StepTimer;

import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOADING;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_ERROR;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_ID_DOWNLOAD_OK;
import static org.kontalk.ui.MessagingNotification.NOTIFICATION_UPDATE_DELAY;


/**
 * The attachment download service.
 * TODO implement multiple downloads in queue or in parallel
 * @author Daniele Ricci
 */
public class DownloadService extends IntentService implements DownloadListener {
    private static final String TAG = MessageCenterService.TAG;

    /** A map to avoid duplicate downloads. */
    private static final Map<String, Long> sQueue = new LinkedHashMap<>();

    public static final String ACTION_DOWNLOAD_URL = "org.kontalk.action.DOWNLOAD_URL";
    public static final String ACTION_DOWNLOAD_ABORT = "org.kontalk.action.DOWNLOAD_ABORT";

    private ProgressNotificationBuilder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    // data about the download currently being processed
    private Notification mCurrentNotification;
    private long mTotalBytes;
    /** Step timer for notification updates. */
    private StepTimer mUpdateTimer = new StepTimer(NOTIFICATION_UPDATE_DELAY);

    private long mMessageId;
    private String mPeer;
    private boolean mEncrypted;

    private ClientHTTPConnection mDownloadClient;
    private boolean mCanceled;

    public DownloadService() {
        super(DownloadService.class.getSimpleName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mNotificationManager == null)
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (ACTION_DOWNLOAD_ABORT.equals(intent.getAction())) {
            final Uri uri = intent.getData();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    onDownloadAbort(uri);
                }
            }).start();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();

        if (ACTION_DOWNLOAD_URL.equals(action)) {
            onDownloadURL(intent.getData(), intent.getExtras());
        }
    }

    private void onDownloadURL(Uri uri, Bundle args) {
        String url = uri.toString();

        // check if download has already been queued
        if (sQueue.get(url) != null) return;

        // notify user about download immediately
        startForeground(0);
        mCanceled = false;

        if (mDownloadClient == null) {
            PersonalKey key;
            PrivateKey privateKey;
            try {
                key = ((Kontalk) getApplication()).getPersonalKey();
                privateKey = key.getBridgePrivateKey();
            }
            catch (Exception e) {
                // TODO i18n :)
                errorNotification("ERROR", "NAUGHTY BOY/GIRL!");
                return;
            }

            mDownloadClient = new ClientHTTPConnection(this,
                privateKey, key.getBridgeCertificate());
        }

        try {
            // check if external storage is available
            if (!MediaStorage.isExternalStorageAvailable()) {
                errorNotification(getString(R.string.notify_ticker_external_storage),
                    getString(R.string.notify_text_external_storage));
                return;
            }

            // make sure storage directory is present
            MediaStorage.MEDIA_ROOT.mkdirs();

            mMessageId = args.getLong(CompositeMessage.MSG_ID, 0);
            mPeer = args.getString(CompositeMessage.MSG_SENDER);
            mEncrypted = args.getBoolean(CompositeMessage.MSG_ENCRYPTED, false);
            sQueue.put(url, mMessageId);

            Date date = null;
            long timestamp = args.getLong(CompositeMessage.MSG_TIMESTAMP);
            if (timestamp > 0)
                date = new Date(timestamp);

            // download content
            mDownloadClient.downloadAutofilename(url, MediaStorage.MEDIA_ROOT, date, this);
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

    private void onDownloadAbort(Uri uri) {
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

    public void startForeground(long totalBytes) {
        Log.d(TAG, "starting foreground progress notification");
        mTotalBytes = totalBytes;

        Intent ni = new Intent(getApplicationContext(), ConversationsActivity.class);
        // FIXME this intent should actually open the ComposeMessage activity
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOADING, ni, 0);

        if (mNotificationBuilder == null) {
            mNotificationBuilder = new ProgressNotificationBuilder(getApplicationContext(),
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
        mUpdateTimer.reset();
        startForeground(length);
    }

    @Override
    public void completed(String url, String mime, File destination) {
        Uri uri = Uri.fromFile(destination);

        ContentValues values = null;

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
                PersonalKey key = ((Kontalk) getApplicationContext()).getPersonalKey();
                Coder coder = UsersProvider.getDecryptCoder(this, server, key, mPeer);
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
                    values = new ContentValues(3);
                    values.put(Messages.ATTACHMENT_ENCRYPTED, false);
                    values.put(Messages.ATTACHMENT_LENGTH, destination.length());
                }
            }
            catch (Exception e) {
                Log.e(TAG, "decryption failed!", e);
                errorNotification(getString(R.string.notify_ticker_download_error),
                    // TODO i18n
                    "Decryption failed.");
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

        // update messages.localUri
        if (values == null)
            values = new ContentValues(1);
        values.put(Messages.ATTACHMENT_LOCAL_URI, uri.toString());
        getContentResolver().update(ContentUris
            .withAppendedId(Messages.CONTENT_URI, mMessageId), values, null, null);

        // update media store
        MediaStorage.scanFile(this, destination, mime);

        // stop foreground
        stopForeground();

        // notify only if conversation is not open
        if (!MessagingNotification.isPaused(mPeer)) {

            // detect mime type if not available
            if (mime == null)
                mime = getContentResolver().getType(uri);

            // create intent for download complete notification
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_OK, i, 0);

            // create notification
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setContentTitle(getString(R.string.notify_title_download_completed))
                .setContentText(getString(R.string.notify_text_download_completed))
                .setTicker(getString(R.string.notify_ticker_download_completed))
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true);

            // notify!!
            mNotificationManager.notify(NOTIFICATION_ID_DOWNLOAD_OK, builder.build());
        }
    }

    @Override
    public void error(String url, File destination, Throwable exc) {
        Log.e(TAG, "download error", exc);
        stopForeground();
        if (!mCanceled)
            errorNotification(getString(R.string.notify_ticker_download_error),
                getString(R.string.notify_text_download_error));
    }

    private void errorNotification(String ticker, String text) {
        // create intent for download error notification
        Intent i = new Intent(this, ConversationsActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(),
                NOTIFICATION_ID_DOWNLOAD_ERROR, i, 0);

        // create notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext())
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(getString(R.string.notify_title_download_error))
            .setContentText(text)
            .setTicker(ticker)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(pi)
            .setAutoCancel(true);

        // notify!!
        mNotificationManager.notify(NOTIFICATION_ID_DOWNLOAD_ERROR, builder.build());
    }

    @Override
    public void progress(String url, File destination, long bytes) {
        if (mCurrentNotification != null && (bytes >= mTotalBytes || mUpdateTimer.isStep())) {
            int progress = (int) ((100 * bytes) / mTotalBytes);
            foregroundNotification(progress);
            // send the updates to the notification manager
            mNotificationManager.notify(NOTIFICATION_ID_DOWNLOADING, mCurrentNotification);
        }
    }

    public static boolean isQueued(String url) {
        return sQueue.containsKey(url);
    }
}

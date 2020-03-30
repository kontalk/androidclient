/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.kontalk.Log;
import org.kontalk.message.ImageComponent;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MessagesProviderClient.MessageUpdater;
import org.kontalk.provider.MyMessages;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.Preferences;


/**
 * A service to handle background requests to generate thumbnail, compress
 * images, videos and the like.
 * @author Daniele Ricci
 */
public class MediaService extends JobIntentService {
    private static final String TAG = MessageCenterService.TAG;

    private static final int JOB_ID = 1000;

    private static final String ACTION_PREPARE_MESSAGE = "org.kontalk.action.PREPARE_MESSAGE";

    private static final String EXTRA_MSG_ID = "org.kontalk.media.message.id";
    private static final String EXTRA_MSG_SERVER_ID = "org.kontalk.media.message.serverId";
    private static final String EXTRA_MSG_MIME = "org.kontalk.media.message.mime";
    private static final String EXTRA_MSG_MEDIA = "org.kontalk.media.message.media";
    private static final String EXTRA_MSG_COMPRESS = "org.kontalk.media.message.compress";

    /**
     * Broadcasted when a media message is ready for sending.
     * @deprecated We should use the event bus.
     */
    @Deprecated
    public static final String ACTION_MEDIA_READY = "org.kontalk.action.MEDIA_READY";

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String action = intent.getAction();

        if (ACTION_PREPARE_MESSAGE.equals(action)) {
            onPrepareMessage(intent.getData(), intent.getExtras());
        }
    }

    private void onPrepareMessage(Uri uri, Bundle args) {
        long databaseId = args.getLong(EXTRA_MSG_ID);
        String mime = args.getString(EXTRA_MSG_MIME);
        boolean media = args.getBoolean(EXTRA_MSG_MEDIA, false);

        try {
            File previewFile = null;
            long length;

            int compress = 0;
            // FIXME hard-coded to ImageComponent (how about videos?)
            if (ImageComponent.supportsMimeType(mime)) {
                compress = Preferences.getImageCompression(this);

                // generate thumbnail
                String filename = ImageComponent.buildMediaFilename(MediaStorage.THUMBNAIL_MIME_NETWORK);
                previewFile = MediaStorage.cacheThumbnail(this, uri, filename, true);
            }

            if (compress > 0) {
                File compressed = MediaStorage.resizeImage(this, uri, compress);
                length = compressed.length();
                // use the compressed image from now on
                uri = Uri.fromFile(compressed);
            }
            else if (media) {
                File copy = MediaStorage.copyOutgoingMedia(this, uri);
                length = copy.length();
                uri = Uri.fromFile(copy);
            }
            else {
                length = MediaStorage.getLength(this, uri);
            }

            MessagesProviderClient.updateMedia(this, databaseId,
                previewFile != null ? previewFile.toString() : null,
                uri, length);

            // TODO post event to message center bus
            Intent i = new Intent(ACTION_MEDIA_READY);
            i.putExtra("org.kontalk.message.msgId", databaseId);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        }
        catch (Exception e) {
            Log.w(TAG, "unable to prepare media for sending", e);
            ReportingManager.logException(e);
            MessageUpdater.forMessage(this, databaseId)
                .setStatus(MyMessages.Messages.STATUS_ERROR)
                .commit();
            // simulate upload error
            UploadService.genericErrorNotification(this);
        }
    }

    public static void prepareMessage(Context context, String msgId, long databaseId, Uri uri, String mime, boolean media, int compress) {
        Intent i = new Intent(context, MediaService.class);
        i.setAction(ACTION_PREPARE_MESSAGE);
        i.putExtra(EXTRA_MSG_SERVER_ID, msgId);
        i.putExtra(EXTRA_MSG_ID, databaseId);
        i.putExtra(EXTRA_MSG_MIME, mime);
        i.putExtra(EXTRA_MSG_MEDIA, media);
        i.putExtra(EXTRA_MSG_COMPRESS, compress);
        i.setData(uri);
        enqueueWork(context, MediaService.class, JOB_ID, i);
    }

}

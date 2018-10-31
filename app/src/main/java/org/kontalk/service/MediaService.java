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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;
import android.support.v4.content.LocalBroadcastManager;

import org.kontalk.message.CompositeMessage;
import org.kontalk.message.ImageComponent;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.provider.MessagesProviderClient.MessageUpdater;
import org.kontalk.provider.MyMessages;
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

    /**
     * Broadcasted when a media message is ready for sending.
     */
    public static final String ACTION_MEDIA_READY = "org.kontalk.action.MEDIA_READY";

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        String action = intent.getAction();

        if (ACTION_PREPARE_MESSAGE.equals(action)) {
            onPrepareMessage(intent.getData(), intent.getExtras());
        }
    }

    private void onPrepareMessage(Uri uri, Bundle args) {
        long databaseId = args.getLong(CompositeMessage.MSG_ID);
        String mime = args.getString(CompositeMessage.MSG_MIME);
        boolean media = args.getBoolean("org.kontalk.message.media", false);

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

            Intent i = new Intent(ACTION_MEDIA_READY);
            i.putExtra("org.kontalk.message.msgId", databaseId);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
        }
        catch (Exception e) {
            MessageUpdater.forMessage(this, databaseId)
                .setStatus(MyMessages.Messages.STATUS_ERROR)
                .commit();
            // TODO notify error in some way?
        }
    }

    public static void prepareMessage(Context context, String msgId, long databaseId, Uri uri, String mime, boolean media, int compress) {
        Intent i = new Intent(context, MediaService.class);
        i.setAction(MediaService.ACTION_PREPARE_MESSAGE);
        i.putExtra(CompositeMessage.MSG_SERVER_ID, msgId);
        i.putExtra(CompositeMessage.MSG_ID, databaseId);
        i.putExtra(CompositeMessage.MSG_MIME, mime);
        i.putExtra("org.kontalk.message.media", media);
        i.putExtra(CompositeMessage.MSG_COMPRESS, compress);
        i.setData(uri);
        enqueueWork(context, MediaService.class, JOB_ID, i);
    }

}

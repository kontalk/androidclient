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

package org.kontalk.ui;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.message.ImageComponent;
import org.kontalk.message.TextComponent;
import org.kontalk.message.VCardComponent;
import org.kontalk.util.MediaStorage;


/**
 * Static utilities used by activities that can receive ACTION_SEND intents,
 * namely {@link ConversationsActivity} (indirectly) and {@link ComposeMessage}.
 * Not the best solution, but it was too complicated to be used as parent class.
 */
class SendIntentReceiver {
    private static final String TAG = ComposeMessage.class.getSimpleName();

    private SendIntentReceiver() {
    }

    static void processSendIntent(Context context, Intent sendIntent, AbstractComposeFragment fragment) {
        String mime = sendIntent.getType();
        boolean multi = Intent.ACTION_SEND_MULTIPLE.equals(sendIntent.getAction());

        if (multi) {
            // multiple texts: take only the first one
            // FIXME this will not allow text file attachments
            if (TextComponent.supportsMimeType(mime)) {
                ArrayList<CharSequence> texts = sendIntent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT);
                if (texts != null && texts.size() > 0)
                    fragment.setTextEntry(texts.get(0));
            }

            else {
                ArrayList<Uri> uris = sendIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (uris != null) {
                    for (Uri uri : uris) {
                        sendMedia(context, uri, fragment);
                    }
                }
            }
        }

        else {
            // FIXME this will not allow text file attachments
            CharSequence text = sendIntent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text != null || TextComponent.supportsMimeType(mime)) {
                fragment.setTextEntry(text);
            }

            else {
                Uri uri = sendIntent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (uri != null)
                    sendMedia(context, uri, fragment);
            }
        }
    }

    private static void sendMedia(Context context, Uri uri, AbstractComposeFragment fragment) {
        Log.d(TAG, "looking up mime type for uri " + uri);
        String mime = MediaStorage.getType(context, uri);
        Log.d(TAG, "using detected mime type " + mime);

        if (ImageComponent.supportsMimeType(mime)) {
            // send image immediately
            fragment.sendBinaryMessage(uri, mime, true, ImageComponent.class);
        }

        else if (VCardComponent.supportsMimeType(mime)) {
            fragment.sendBinaryMessage(uri, mime, true, VCardComponent.class);
        }

        else {
            // notify to user
            Log.w(TAG, "mime " + mime + " not supported");
            Toast.makeText(context, R.string.send_mime_not_supported, Toast.LENGTH_LONG)
                .show();
        }
    }

}

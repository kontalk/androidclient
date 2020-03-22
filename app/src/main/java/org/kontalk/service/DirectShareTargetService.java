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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;

import androidx.core.content.ContextCompat;

import org.kontalk.R;
import org.kontalk.data.Contact;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.ui.ComposeMessage;
import org.kontalk.util.MediaStorage;
import org.kontalk.util.MessageUtils;


/**
 * Direct share service.
 * @author Daniele Ricci
 */
@TargetApi(Build.VERSION_CODES.M)
public class DirectShareTargetService extends ChooserTargetService {

    private static final int MAX_TARGETS = 5;

    private Paint roundPaint;
    private RectF bitmapRect;

    /**
     * Returns a list of targets available for sharing, in this case, the last {@link #MAX_TARGETS}
     * open conversations.
     */
    @Override
    public List<ChooserTarget> onGetChooserTargets(ComponentName targetActivityName, IntentFilter matchedFilter) {
        ComponentName componentName = new ComponentName(getPackageName(),
            ComposeMessage.class.getCanonicalName());

        Cursor cursor = MessagesProviderClient.getLatestThreads(this, true, MAX_TARGETS);
        if (cursor.moveToFirst()) {
            List<ChooserTarget> targets = new ArrayList<>(MAX_TARGETS);
            do {
                String userId = cursor.getString(MessagesProviderClient.LATEST_THREADS_COLUMN_PEER);
                boolean isGroup = cursor.getString(MessagesProviderClient.LATEST_THREADS_COLUMN_GROUP_JID) != null;

                String displayName;
                Icon avatar;
                if (!isGroup) {
                    Contact contact = Contact.findByUserId(this, userId);
                    displayName = contact.getDisplayName();
                    avatar = Icon.createWithBitmap(contact.getAvatarBitmap(this));
                }
                else {
                    displayName = cursor.getString(MessagesProviderClient.LATEST_THREADS_COLUMN_GROUP_SUBJECT);
                    // groups don't have avatars yet, for now we use the default icon
                    avatar = getGroupAvatar();
                }

                // ComposeMessage will get an ACTION_SEND intent with a user id extra.
                // This will skip the chooseContact() step and go directly to the conversation
                Bundle extras = new Bundle();
                extras.putString(ComposeMessage.EXTRA_USERID, userId);
                targets.add(new ChooserTarget(displayName, avatar, 0.5f, componentName, extras));
            } while (cursor.moveToNext());
            return targets;
        }

        return Collections.emptyList();
    }

    /**
     * @deprecated This will go away when groups will have their own avatars and will be
     * retrieved by {@link Contact}.
     */
    @Deprecated
    private Icon getGroupAvatar() {
        // optimize for rounded avatars
        if (Contact.isRoundedAvatars()) {
            Bitmap avatarOriginal = MessageUtils.drawableToBitmap(ContextCompat
                .getDrawable(this, R.drawable.ic_default_group));
            Bitmap avatarRounded = MediaStorage.createRoundBitmap(avatarOriginal);
            if (avatarRounded != avatarOriginal)
                avatarOriginal.recycle();
            return Icon.createWithBitmap(avatarRounded);
        }
        else {
            return Icon.createWithResource(this, R.drawable.ic_default_group);
        }
    }

}

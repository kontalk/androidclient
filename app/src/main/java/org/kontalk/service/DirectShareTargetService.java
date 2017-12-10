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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.service.chooser.ChooserTarget;
import android.service.chooser.ChooserTargetService;
import android.support.annotation.NonNull;

import org.kontalk.data.Contact;
import org.kontalk.provider.MessagesProviderClient;
import org.kontalk.ui.ComposeMessage;


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

        Cursor cursor = MessagesProviderClient.getLatestThreads(this, false, MAX_TARGETS);
        if (cursor.moveToFirst()) {
            List<ChooserTarget> targets = new ArrayList<>(MAX_TARGETS);
            do {
                String userId = cursor.getString(MessagesProviderClient.LATEST_THREADS_COLUMN_PEER);

                Contact contact = Contact.findByUserId(this, userId);

                // ComposeMessage will get an ACTION_SEND intent with a user id extra.
                // This will skip the chooseContact() step and go directly to the conversation
                Bundle extras = new Bundle();
                extras.putString(ComposeMessage.EXTRA_USERID, userId);
                targets.add(new ChooserTarget(contact.getDisplayName(),
                    Icon.createWithBitmap(createRoundBitmap(contact.getAvatarBitmap(this))), 0.5f,
                    componentName, extras));
            } while (cursor.moveToNext());
            return targets;
        }

        return Collections.emptyList();
    }

    // thanks to Telegram source code :)
    private Bitmap createRoundBitmap(@NonNull Bitmap source) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        result.eraseColor(Color.TRANSPARENT);
        Canvas canvas = new Canvas(result);
        BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        if (roundPaint == null) {
            roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bitmapRect = new RectF();
        }
        roundPaint.setShader(shader);
        bitmapRect.set(0, 0, source.getWidth(), source.getHeight());
        canvas.drawRoundRect(bitmapRect, source.getWidth(), source.getHeight(), roundPaint);
        return result;
    }

}

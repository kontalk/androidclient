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

import org.kontalk.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import android.widget.RemoteViews;


/**
 * Builder for progress notifications.
 * @author Daniele Ricci
 */
public class ProgressNotificationBuilder extends NotificationCompat.Builder {

    private Context mContext;
    private RemoteViews mContentView;
    private int mLayout;

    public ProgressNotificationBuilder(Context context,
            String channelId,
            int layout,
            CharSequence tickerText,
            int smallIcon,
            PendingIntent intent) {
        super(context, channelId);

        mContext = context;
        mLayout = layout;

        setTicker(tickerText);
        // HACK this is needed otherwise notification won't be showed
        setSmallIcon(smallIcon);
        setContentIntent(intent);
        setOngoing(true);
        setCategory(NotificationCompat.CATEGORY_PROGRESS);
        // apparently PRIORITY_MIN/IMPORTANCE_MIN is not compatible with setOnGoing
        // system will raise this to PRIORITY_LOW/IMPORTANCE_MIN unless changed by user
        setPriority(NotificationCompat.PRIORITY_MIN);
    }

    /**
     * Updates the notification progress bar.
     * @param progress if less than 0, progress bar will be indeterminate
     */
    public ProgressNotificationBuilder progress(int progress, int contentTitle, int contentText) {
        this.setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(mContext.getString(contentTitle))
            .setContentText(mContext.getString(contentText));

        if (progress < 0)
            setProgress(0, 0, true);
        else
            setProgress(100, progress, false);

        // not using custom content view
        mContentView = null;

        return this;
    }

    @Override
    public Notification build() {
        Notification no = super.build();

        if (mContentView != null) {
            /*
             * HACK working around bug #30495
             * @see https://code.google.com/p/android/issues/detail?id=30495
             */
            no.contentView = mContentView;
        }

        return no;
    }

}

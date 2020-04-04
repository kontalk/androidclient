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

package org.kontalk.ui.prefs;

import java.io.File;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.preference.Preference;
import android.util.AttributeSet;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.util.MediaStorage;


/**
 * Preference for sending the debug log.
 * @author Daniele Ricci
 */
public class SendDebugLogPreference extends Preference {

    public SendDebugLogPreference(Context context) {
        super(context);
    }

    public SendDebugLogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SendDebugLogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SendDebugLogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onClick() {
        super.onClick();
        sendDebugLog(getContext());
    }

    private void sendDebugLog(Context context) {
        File file = Log.getLogFile();
        if (file != null && file.isFile()) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_EMAIL, new String[] { context.getString(R.string.mailto) });
            i.putExtra(Intent.EXTRA_SUBJECT, "Kontalk debug log");
            Uri uri = MediaStorage.getWorldReadableUri(context,
                Uri.fromFile(file), i);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            context.startActivity(i);
        }
    }

}

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

package org.kontalk.util;

import java.util.Hashtable;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import org.kontalk.Kontalk;

/**
 * Kontalk Utilities.
 * @author Andrea Cappelli
 */

public class KontalkUtilities {
    static final String TAG = KontalkUtilities.class.getSimpleName();

    public static float density = 1;
    public static Point displaySize = new Point();

    static {
        density = Kontalk.mApplicationContext.getResources().getDisplayMetrics().density;
        getDisplaySize();
    }

    public static int getDensityPixel(int value) {
        return (int)(Math.max(1, density * value));
    }

    public static void getDisplaySize() {
        try {
            WindowManager manager = (WindowManager)Kontalk.mApplicationContext.getSystemService(Context.WINDOW_SERVICE);
            if (manager != null) {
                Display display = manager.getDefaultDisplay();
                if (display != null) {
                    if(android.os.Build.VERSION.SDK_INT < 13) {
                        displaySize.set(display.getWidth(), display.getHeight());
                    } else {
                        display.getSize(displaySize);
                    }
                    Log.e(TAG, "display size = " + displaySize.x + " " + displaySize.y);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, String.valueOf(e));
        }
    }
}

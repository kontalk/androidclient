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
 * Created by andrea on 23/08/14.
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

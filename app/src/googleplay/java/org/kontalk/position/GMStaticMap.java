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

package org.kontalk.position;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import android.content.Context;


public class GMStaticMap {

    private static final String GM_MAP_TYPE = "roadmap";
    private static final String GM_MARKER_COLOR = "red";
    private static final char GM_MARKER_LABEL = '\0';
    private static final boolean GM_SENSOR = false;
    private static final int GM_MAP_WIDTH = 900;
    private static final int GM_MAP_HEIGHT = 500;
    private static final int GM_MAP_ZOOM = 13;

    private static GMStaticMap sInstance;

    public static GMStaticMap getInstance(Context context) {
        if (sInstance == null)
            sInstance = new GMStaticMap(context.getApplicationContext());

        return sInstance;
    }

    private final Context mContext;

    private final Map<String, Set<GMStaticMapListener>> mQueue;

    private GMStaticMap(Context context) {
        mContext = context;
        mQueue = new HashMap<String, Set<GMStaticMapListener>>();
    }

    public File requestMap(double lat, double lon, GMStaticMapListener listener) {
        String filename = String.format(Locale.US, "gmap_%f_%f.png", lat, lon);
        final File dest = new File(mContext.getCacheDir(), filename);

        // file exists - return file object
        if (dest.isFile())
            return dest;

        // queue the download
        queueDownload(lat, lon, filename, dest, listener);
        return null;
    }

    private void queueDownload(double lat, double lon, final String id, final File destination,
            GMStaticMapListener listener) {

        boolean start = false;

        synchronized (mQueue) {
            Set<GMStaticMapListener> listeners = mQueue.get(id);
            if (listeners == null) {
                listeners = new LinkedHashSet<GMStaticMapListener>(1);
                mQueue.put(id, listeners);

                start = true;
            }

            listeners.add(listener);
        }

        if (start) {
            GMStaticUrlBuilder url = new GMStaticUrlBuilder()
                .setCenter(lat, lon)
                .setMapType(GM_MAP_TYPE)
                .setMarker(lat, lon)
                .setSensor(GM_SENSOR)
                .setSize(GM_MAP_WIDTH, GM_MAP_HEIGHT)
                .setZoom(GM_MAP_ZOOM);

            /*new HttpDownload(url.toString(), destination,
                new Runnable() {
                    public void run() {
                        completed(id, destination);
                    }
                },
                new Runnable() {
                    public void run() {
                        error(id, destination);
                    }
                })
            .start();*/
        }
    }

    public void completed(String id, File destination) {
        // TODO check file size

        synchronized (mQueue) {
            Set<GMStaticMapListener> listeners = mQueue.get(id);
            if (listeners != null) {
                for (GMStaticMapListener l : listeners)
                    l.completed(destination);

                mQueue.remove(id);
            }
        }
    }

    public void error(String id, File destination) {
        // delete file
        destination.delete();

        synchronized (mQueue) {
            Set<GMStaticMapListener> listeners = mQueue.get(id);
            if (listeners != null) {
                for (GMStaticMapListener l : listeners)
                    l.error(destination);

                mQueue.remove(id);
            }
        }
    }

    public interface GMStaticMapListener {
        public void completed(File destination);

        public void error(File destination);
    }

}

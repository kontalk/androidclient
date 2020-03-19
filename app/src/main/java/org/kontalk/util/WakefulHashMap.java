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

package org.kontalk.util;

import java.util.HashMap;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;


/**
 * A hash map that acquires a wake lock whenever an item is inserted
 * and releases a wake lock whenever an item is removed.
 */
public class WakefulHashMap<K, V> extends HashMap<K, V> {

    private final PowerManager.WakeLock mWakeLock;

    public WakefulHashMap(Context context, int levelAndFlags, String tag) {
        super();

        PowerManager pwr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pwr.newWakeLock(levelAndFlags, tag);
    }

    @Override
    @SuppressLint("WakelockTimeout")
    public V put(K key, V value) {
        V prev = super.put(key, value);
        if (prev == null) {
            mWakeLock.acquire();
        }
        return prev;
    }

    public V put(K key, V value, long timeout) {
        V prev = super.put(key, value);
        if (prev == null) {
            mWakeLock.acquire(timeout);
        }
        return prev;
    }

    @Override
    public V remove(Object key) {
        V prev = super.remove(key);
        if (prev != null) {
            mWakeLock.release();
        }
        return prev;
    }

    @Override
    public void clear() {
        super.clear();
        while (mWakeLock.isHeld())
            mWakeLock.release();
    }

}

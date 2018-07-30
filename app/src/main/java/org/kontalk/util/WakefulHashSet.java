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

package org.kontalk.util;

import java.util.HashSet;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;


/**
 * A hash set that acquires a wake lock whenever an item is inserted
 * and releases a wake lock whenever an item is removed.
 */
public class WakefulHashSet<E> extends HashSet<E> {

    private final PowerManager.WakeLock mWakeLock;

    public WakefulHashSet(int initialCapacity, Context context, int levelAndFlags, String tag) {
        super(initialCapacity);

        PowerManager pwr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pwr.newWakeLock(levelAndFlags, tag);
    }

    @Override
    @SuppressLint("WakelockTimeout")
    public boolean add(E e) {
        boolean added = super.add(e);
        if (added) {
            mWakeLock.acquire();
        }
        return added;
    }

    public boolean add(E e, long timeout) {
        boolean added = super.add(e);
        if (added) {
            mWakeLock.acquire(timeout);
        }
        return added;
    }

    @Override
    public boolean remove(Object key) {
        boolean removed = super.remove(key);
        if (removed) {
            mWakeLock.release();
        }
        return removed;
    }

    @Override
    public void clear() {
        super.clear();
        while (mWakeLock.isHeld())
            mWakeLock.release();
    }

}

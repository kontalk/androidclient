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

package org.kontalk.service.msgcenter;

import com.google.android.gms.security.ProviderInstaller;

import android.content.Context;
import org.kontalk.Log;

import org.kontalk.Kontalk;


public class SecureConnectionManager {

    @SuppressWarnings("WeakerAccess")
    static final Object sLock = new Object();
    @SuppressWarnings("WeakerAccess")
    static volatile boolean sInit;

    public static void init(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (sLock) {
                    try {
                            ProviderInstaller.installIfNeeded(context);
                    }
                    catch (Exception e) {
                        Log.w(Kontalk.TAG,
                            "Unable to install Google Play security provider (" + e.toString() + ")");
                    }
                    finally {
                        sInit = true;
                        sLock.notifyAll();
                    }
                }
            }
        }).start();
    }

    public static void waitForInit() {
        synchronized (sLock) {
            if (!sInit) {
                try {
                    sLock.wait();
                }
                catch (InterruptedException ignored) {
                }
            }
        }
    }

}

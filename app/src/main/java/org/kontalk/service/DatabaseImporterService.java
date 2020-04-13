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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import android.app.Service;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.kontalk.util.DataUtils;


/**
 * An abstract service for importing a database from any location to a local
 * database.
 * @author Daniele Ricci
 */
public abstract class DatabaseImporterService extends Service {

    // actions
    public static final String ACTION_IMPORT  = "org.kontalk.database.IMPORT";

    public static final String EXTRA_ERROR = "org.kontalk.database.ERROR";

    // broadcasts
    public static final String ACTION_STARTED = "org.kontalk.database.STARTED";
    public static final String ACTION_FINISH = "org.kontalk.database.FINISH";

    private LocalBroadcastManager lbm;
    private ImporterThread mThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (lbm == null)
            lbm = LocalBroadcastManager.getInstance(getApplicationContext());

        String action = intent.getAction();
        if (ACTION_IMPORT.equals(action)) {
            Uri databaseFile = intent.getData();
            if (mThread == null) {
                mThread = new ImporterThread(this, databaseFile);
                mThread.start();

                broadcastStarted();
            }
        }

        return START_REDELIVER_INTENT;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected abstract void lockDestinationDatabase();

    protected abstract OutputStream getDestinationStream() throws IOException;

    protected abstract void reloadDatabase() throws SQLiteException;

    protected abstract void unlockDestinationDatabase();

    private void broadcastStarted() {
        lbm.sendBroadcast(new Intent(ACTION_STARTED));
    }

    void broadcastFinish(String error) {
        Intent i = new Intent(ACTION_FINISH);
        i.putExtra(EXTRA_ERROR, error);
        lbm.sendBroadcast(i);
    }

    static final class ImporterThread extends Thread {
        private WeakReference<DatabaseImporterService> s;
        private Uri origin;

        ImporterThread(DatabaseImporterService service, Uri databaseFile) {
            s = new WeakReference<>(service);
            origin = databaseFile;
        }

        @Override
        public void run() {
            // set a low priority
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            DatabaseImporterService service = s.get();
            if (service != null) {
                // first thing to do
                service.lockDestinationDatabase();

                InputStream in = null;
                OutputStream out = null;
                String error = null;

                try {
                    // open the databases
                    in = service.getContentResolver().openInputStream(origin);
                    out = service.getDestinationStream();

                    if (in == null || out == null)
                        throw new IOException("unable to open files");

                    // this is it
                    DataUtils.copy(in, out);

                    // reload will return without exceptions if everything is ok
                    service.reloadDatabase();
                }
                catch (FileNotFoundException e) {
                    error = e.toString();
                }
                catch (IOException e) {
                    error = e.toString();
                }
                catch (SQLiteException e) {
                    // TODO i18n
                    error = "Invalid database.";
                }
                finally {
                    DataUtils.close(in);
                    DataUtils.close(out);
                    service.unlockDestinationDatabase();
                    service.broadcastFinish(error);
                    service.stopSelf();
                }
            }
        }

    }

}

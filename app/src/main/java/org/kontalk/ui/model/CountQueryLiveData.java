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

package org.kontalk.ui.model;

import android.annotation.SuppressLint;
import androidx.lifecycle.LiveData;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Handler;
import androidx.core.os.CancellationSignal;
import androidx.core.os.OperationCanceledException;


/**
 * A LiveData that models a simple counting query to an integer.
 * Subclasses should return a cursor through {@link #doQuery(CancellationSignal)}.
 * The actual count will be read from the first column of the first row.
 */
public abstract class CountQueryLiveData extends LiveData<Integer> {

    private final ForceLoadContentObserver mObserver;
    private CancellationSignal mCancellationSignal;
    private Cursor mCursor;

    protected CountQueryLiveData() {
        mObserver = new ForceLoadContentObserver();
    }

    private void loadData() {
        loadData(false);
    }

    @SuppressLint("StaticFieldLeak")
    private void loadData(boolean forceQuery) {
        if (!forceQuery && getValue() != null) {
            return;
        }

        new AsyncTask<Void, Void, Integer>() {

            @Override
            protected Integer doInBackground(Void... params) {
                try {
                    synchronized (CountQueryLiveData.this) {
                        mCancellationSignal = new CancellationSignal();
                    }
                    Cursor cursor = null;
                    try {
                        cursor = doQuery(mCancellationSignal);
                        try {
                            if (!cursor.moveToNext())
                                throw new RuntimeException("No data found.");
                            cursor.registerContentObserver(mObserver);
                            return cursor.getInt(0);
                        }
                        catch (Exception e) {
                            if (cursor != null) {
                                cursor.close();
                                cursor = null;
                            }
                            throw e;
                        }
                        finally {
                            mCursor = cursor;
                        }
                    }
                    finally {
                        synchronized (CountQueryLiveData.this) {
                            mCancellationSignal = null;
                        }
                        mCursor = cursor;
                    }
                }
                catch (OperationCanceledException e) {
                    if (hasActiveObservers()) {
                        throw e;
                    }
                    return null;
                }
            }

            @Override
            protected void onPostExecute(Integer count) {
                setValue(count);
            }

        }.execute();
    }

    protected abstract Cursor doQuery(CancellationSignal cancellationSignal);

    @Override
    protected void onActive() {
        loadData();
    }

    @Override
    protected void onInactive() {
        synchronized (this) {
            if (mCancellationSignal != null) {
                mCancellationSignal.cancel();
            }
        }
    }

    public void close() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
    }

    final class ForceLoadContentObserver extends ContentObserver {

        ForceLoadContentObserver() {
            super(new Handler());
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            loadData(true);
        }

    }


}

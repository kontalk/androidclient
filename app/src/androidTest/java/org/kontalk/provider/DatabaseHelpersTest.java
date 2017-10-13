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

package org.kontalk.provider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;


/**
 * Automated database upgrade tests.
 * Inspired by https://riggaroo.co.za/automated-testing-sqlite-database-upgrades-android/
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DatabaseHelpersTest {

    @Test
    public void testMessagesDatabaseUpgrades() throws IOException {
        MessagesProvider.DatabaseHelper dbHelper = new MessagesProvider
            .DatabaseHelper(InstrumentationRegistry.getTargetContext());

        for (int i = 8; i < MessagesProvider.DatabaseHelper.DATABASE_VERSION; i++) {
            dbHelper.close();
            try {
                copyDatabase("messages", i);
                Log.d(getClass().getSimpleName(), "Testing upgrade from version: " + i);
            }
            catch (FileNotFoundException e) {
                Log.d(getClass().getSimpleName(), "Skipping upgrade from version: " + i);
            }

            MessagesProvider.DatabaseHelper dbHelperNew = new MessagesProvider
                .DatabaseHelper(InstrumentationRegistry.getTargetContext());
            Assert.assertEquals(MessagesProvider.DatabaseHelper.DATABASE_VERSION,
                dbHelperNew.getWritableDatabase().getVersion());
        }
    }

    @Test
    public void testUsersDatabaseUpgrades() throws IOException {
        UsersProvider.DatabaseHelper dbHelper = new UsersProvider
            .DatabaseHelper(InstrumentationRegistry.getTargetContext());

        for (int i = 7; i < UsersProvider.DATABASE_VERSION; i++) {
            dbHelper.close();
            try {
                copyDatabase("users", i);
                Log.d(getClass().getSimpleName(), "Testing upgrade from version: " + i);
            }
            catch (FileNotFoundException e) {
                Log.d(getClass().getSimpleName(), "Skipping upgrade from version: " + i);
            }

            UsersProvider.DatabaseHelper dbHelperNew = new UsersProvider
                .DatabaseHelper(InstrumentationRegistry.getTargetContext());
            Assert.assertEquals(UsersProvider.DATABASE_VERSION,
                dbHelperNew.getWritableDatabase().getVersion());
        }
    }

    private void copyDatabase(String prefix, int version) throws IOException {
        String dbPath = InstrumentationRegistry.getTargetContext()
            .getDatabasePath(MessagesProvider.DatabaseHelper.DATABASE_NAME).getAbsolutePath();

        String dbName = String.format(Locale.US, prefix + "_v%d.db", version);
        InputStream mInput = InstrumentationRegistry.getContext().getAssets().open(dbName);

        File db = new File(dbPath);
        if (!db.exists()){
            db.getParentFile().mkdirs();
            db.createNewFile();
        }
        OutputStream mOutput = new FileOutputStream(dbPath);
        byte[] mBuffer = new byte[1024];
        int mLength;
        while ((mLength = mInput.read(mBuffer)) > 0) {
            mOutput.write(mBuffer, 0, mLength);
        }
        mOutput.flush();
        mOutput.close();
        mInput.close();
    }

}

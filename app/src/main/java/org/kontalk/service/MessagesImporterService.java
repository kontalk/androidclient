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

package org.kontalk.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.net.Uri;

import org.kontalk.provider.MessagesProvider;


/**
 * Imports a messages.db file into the local content provider.
 * @author Daniele Ricci
 */
public class MessagesImporterService extends DatabaseImporterService {

    @Override
    protected void lockDestinationDatabase() {
        MessagesProvider.lockForImport(this);
    }

    @Override
    protected OutputStream getDestinationStream() throws IOException {
        return new FileOutputStream(MessagesProvider.getDatabaseUri(this));
    }

    @Override
    protected void reloadDatabase() throws SQLiteException {
        MessagesProvider.reload(this);
    }

    @Override
    protected void unlockDestinationDatabase() {
        MessagesProvider.unlockForImport(this);
    }

    public static void startImport(Context context, Uri origin) {
        context.startService(new Intent(ACTION_IMPORT, origin,
            context, MessagesImporterService.class));
    }

}

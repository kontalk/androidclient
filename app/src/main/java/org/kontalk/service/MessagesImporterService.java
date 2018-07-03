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

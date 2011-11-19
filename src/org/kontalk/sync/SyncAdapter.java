/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.sync;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.Protocol;
import org.kontalk.client.RequestClient;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.ui.MessagingPreferences;
import org.kontalk.util.MessageUtils;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.util.Log;


public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = SyncAdapter.class.getSimpleName();

    /** {@link Data} column for the display name. */
    public static final String DATA_COLUMN_DISPLAY_NAME = Data.DATA1;
    /** {@link Data} column for the account name. */
    public static final String DATA_COLUMN_ACCOUNT_NAME = Data.DATA2;
    /** {@link Data} column for the phone number. */
    public static final String DATA_COLUMN_PHONE = Data.DATA3;

    /** {@link RawContacts} column for the display name. */
    public static final String RAW_COLUMN_DISPLAY_NAME = RawContacts.SYNC1;
    /** {@link RawContacts} column for the phone number. */
    public static final String RAW_COLUMN_PHONE = RawContacts.SYNC2;
    /** {@link RawContacts} column for the user id (hashed phone number). */
    public static final String RAW_COLUMN_USERID = RawContacts.SYNC3;

    private final Context mContext;
    private final ContentResolver mContentResolver;
    private boolean mCanceled;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        Log.i(TAG, "sync started (authority=" + authority + ")");

        try {
            performSync(mContext, account, extras, authority, provider, syncResult);
        }
        catch (OperationCanceledException e) {
            Log.w(TAG, "sync canceled!", e);
        }
    }

    @Override
    public void onSyncCanceled() {
        super.onSyncCanceled();
        mCanceled = true;
    }

    private static final class RawPhoneNumberEntry {
        public final String displayName;
        public final String number;
        // TODO find a use for this
        public final String hash;

        public RawPhoneNumberEntry(String displayName, String number, String hash) {
            this.displayName = displayName;
            this.number = number;
            this.hash = hash;
        }
    }

    /**
     * The actual sync procedure.
     * This one uses the slowest method ever: it first checks for every phone
     * number in all contacts and it sends them to the server. Once a response
     * is received, it deletes all the raw contacts created by us and then
     * recreates only the ones the server has found a match for.
     */
    private void performSync(Context context, Account account, Bundle extras,
            String authority, ContentProviderClient provider, SyncResult syncResult)
            throws OperationCanceledException {

        // setup the request client
        final EndpointServer server = MessagingPreferences.getEndpointServer(context);
        final String token = Authenticator.getDefaultAccountToken(mContext);
        final RequestClient client = new RequestClient(mContext, server, token);
        final Map<String,RawPhoneNumberEntry> lookupNumbers = new HashMap<String,RawPhoneNumberEntry>();
        final List<String> hashList = new ArrayList<String>();

        final String countryCode = NumberValidator.getCountryPrefix(mContext);
        Log.i(TAG, "using country code: " + countryCode);

        // query all contacts
        final Cursor cursor = mContentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

        while (cursor.moveToNext()) {
            if (mCanceled) throw new OperationCanceledException();

            String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            String displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            //Log.w(TAG, "contact " + contactId + ", name: " + displayName);

            // query for phone numbers
            final Cursor phones = mContentResolver.query(Phone.CONTENT_URI, null,
                Phone.CONTACT_ID + " = ?", new String[] { contactId }, null);

            while (phones.moveToNext()) {
                if (mCanceled) throw new OperationCanceledException();

                String number = phones.getString(phones.getColumnIndex(Phone.NUMBER));

                // a phone number with less than 4 digits???
                if (number.length() < 4)
                    continue;

                // fix number
                number = NumberValidator.fixNumber(mContext, number);

                try {
                    String hash = MessageUtils.sha1(number);
                    lookupNumbers.put(hash, new RawPhoneNumberEntry(displayName, number, hash));
                    hashList.add(hash);
                } catch (NoSuchAlgorithmException e) {
                    Log.e(TAG, "unable to generate SHA-1 hash for " + number + " - skipping", e);
                    syncResult.stats.numIoExceptions++;
                }
            }
            phones.close();
        }
        cursor.close();

        if (mCanceled) throw new OperationCanceledException();

        Protocol.LookupResponse res = null;
        try {
            // request lookup to server
            res = client.lookup(hashList);
        }
        catch (IOException e) {
            Log.e(TAG, "error in user lookup", e);
            syncResult.stats.numIoExceptions++;
        }

        // last chance to quit
        if (mCanceled) throw new OperationCanceledException();

        if (res != null) {
            ArrayList<ContentProviderOperation> operations =
                new ArrayList<ContentProviderOperation>();

            // this is the time - delete all Kontalk raw contacts
            try {
                deleteAll(account, operations);
                provider.applyBatch(operations);
            }
            catch (Exception e) {
                Log.e(TAG, "contact delete error", e);
                syncResult.databaseError = true;
                return;
            }

            for (int i = 0; i < res.getEntryCount(); i++) {
                Protocol.LookupResponseEntry entry = res.getEntry(i);
                String userId = entry.getUserId().toString();
                final RawPhoneNumberEntry data = lookupNumbers.get(userId);
                if (data != null) {
                    operations = new ArrayList<ContentProviderOperation>();
                    addContact(account, data.displayName, data.number, -1, operations);

                    try {
                        provider.applyBatch(operations);
                    }
                    catch (Exception e) {
                        Log.e(TAG, "contact write error", e);
                        syncResult.stats.numSkippedEntries = res.getEntryCount();
                        syncResult.databaseError = true;
                        return;
                    }

                    syncResult.stats.numEntries++;
                }
                else {
                    syncResult.stats.numSkippedEntries++;
                }
            }
        }

    }

    private void deleteAll(Account account, List<ContentProviderOperation> operations) {
        Uri uri = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            .build();
        operations.add(ContentProviderOperation.newDelete(uri).build());
    }

    /*
    private int deleteContact(Account account, long rawContactId) {
        Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId)
            .buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            .build();
        ContentProviderClient client = mContext.getContentResolver().acquireContentProviderClient(ContactsContract.AUTHORITY_URI);
        try {
            return client.delete(uri, null, null);
        }
        catch (RemoteException e) {
            Log.e(TAG, "delete error", e);
        }
        finally {
            client.release();
        }

        return -1;
    }
    */

    private void addContact(Account account, String username, String phone,
            long rowContactId, List<ContentProviderOperation> operations) {
        Log.d(TAG, "adding contact username = \"" + username + "\", phone: " + phone);
        ContentProviderOperation.Builder builder;

        if (rowContactId < 0) {
            try {
                // create our RawContact
                builder = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
                builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
                builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
                builder.withValue(RAW_COLUMN_DISPLAY_NAME, username);
                builder.withValue(RAW_COLUMN_PHONE, phone);
                builder.withValue(RAW_COLUMN_USERID, MessageUtils.sha1(phone));

                operations.add(builder.build());
            }
            catch (Exception e) {
                Log.e(TAG, "sha1 digest failed", e);
            }
        }

        // create a Data record of common type 'StructuredName' for our RawContact
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        if (rowContactId < 0)
            builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
        else
            builder.withValue(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, rowContactId);

        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, username);
        operations.add(builder.build());

        // create a Data record of custom type 'org.kontalk.user' to display a link to the conversation
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        if (rowContactId < 0)
            builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
        else
            builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, rowContactId);

        builder.withValue(ContactsContract.Data.MIMETYPE, Users.CONTENT_ITEM_TYPE);
        builder.withValue(DATA_COLUMN_DISPLAY_NAME, username);
        builder.withValue(DATA_COLUMN_ACCOUNT_NAME, mContext.getString(R.string.app_name));
        builder.withValue(DATA_COLUMN_PHONE, phone);
        operations.add(builder.build());
    }

}

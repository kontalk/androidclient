package org.kontalk.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kontalk.R;
import org.kontalk.client.ClientConnection;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.Protocol.UserLookupResponse;
import org.kontalk.client.TxListener;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.service.ClientThread;
import org.kontalk.service.MessageCenterService;
import org.kontalk.service.MessageCenterService.MessageCenterInterface;
import org.kontalk.service.RequestJob;
import org.kontalk.service.RequestListener;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import com.google.protobuf.MessageLite;


/**
 * The syncer core.
 * @author Daniele Ricci
 */
public class Syncer {
    /** Singleton instance. */
    private static volatile Syncer instance;
    /** Singleton pending? */
    private static volatile boolean pending;

    // using SyncAdapter tag
    private static final String TAG = SyncAdapter.class.getSimpleName();
    private static final int MAX_WAIT_TIME = 60000;

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

    private volatile boolean mCanceled;
    private final Context mContext;

    /** Used for binding to the message center to send messages. */
    private final class ClientServiceConnection implements ServiceConnection {
        private MessageCenterService service;
        private List<String> hashList;
        private UserLookupResponse response;
        private Throwable lastError;

        public ClientServiceConnection(List<String> hashList) {
            this.hashList = hashList;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder ibinder) {
            MessageCenterInterface binder = (MessageCenterInterface) ibinder;
            service = binder.getService();
            RequestJob job = service.lookupUsers(hashList);
            job.setListener(new RequestListener() {
                @Override
                public void starting(ClientThread client, RequestJob job) {
                    // not used
                }

                @Override
                public void downloadProgress(ClientThread client, RequestJob job, long bytes) {
                    // not used
                }

                @Override
                public void uploadProgress(ClientThread client, RequestJob job, long bytes) {
                    // not used
                }

                @Override
                public boolean error(ClientThread client, RequestJob job, Throwable exc) {
                    lastError = exc;
                    synchronized (Syncer.this) {
                        Syncer.this.notifyAll();
                    }
                    return false;
                }

                @Override
                public void done(ClientThread client, RequestJob job, String txId) {
                    // listen for response :)
                    TxListener listener = new TxListener() {
                        @Override
                        public boolean tx(ClientConnection connection, String txId, MessageLite pack) {
                            synchronized (Syncer.this) {
                                response = (UserLookupResponse) pack;
                                Syncer.this.notifyAll();
                            }
                            return false;
                        }
                    };
                    client.setTxListener(txId, listener);
                }
            });
            mContext.unbindService(this);
        }

        public UserLookupResponse getResponse() {
            return response;
        }

        public Throwable getLastError() {
            return lastError;
        }
    }

    public static Syncer getInstance() {
        return instance;
    }

    public static Syncer getInstance(Context context) {
        if (instance == null)
            instance = new Syncer(context.getApplicationContext());
        return instance;
    }

    public static void setPending() {
        pending = true;
    }

    public static boolean isPending() {
        return pending;
    }

    public static void release() {
        instance = null;
        pending = false;
    }

    private Syncer(Context context) {
        mContext = context;
    }

    public void onSyncCanceled() {
        mCanceled = true;
    }

    public void onSyncResumed() {
        mCanceled = false;
    }

    private static final class RawPhoneNumberEntry {
        public final String number;
        public final String hash;
        public final String lookupKey;

        public RawPhoneNumberEntry(String lookupKey, String number, String hash) {
            this.lookupKey = lookupKey;
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
    public void performSync(Context context, Account account, String authority,
        ContentProviderClient provider, ContentProviderClient usersProvider,
        SyncResult syncResult)
            throws OperationCanceledException {

        final Map<String,RawPhoneNumberEntry> lookupNumbers = new HashMap<String,RawPhoneNumberEntry>();
        final List<String> hashList = new ArrayList<String>();

        // resync users database
        Log.v(TAG, "resyncing users database");
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

        // update users database
        Uri uri = Users.CONTENT_URI.buildUpon()
            .appendQueryParameter(Users.RESYNC, "true")
            .build();
        try {
            int count = usersProvider.update(uri, new ContentValues(), null, null);
            Log.d(TAG, "users database resynced (" + count + ")");
        }
        catch (RemoteException e) {
            Log.e(TAG, "error resyncing users database - aborting sync", e);
            syncResult.databaseError = true;
            return;
        }

        CharSequence countryCode = NumberValidator.getCountryPrefix(mContext);
        if (countryCode == null) {
            Log.w(TAG, "no SIM available and no saved country code - aborting sync");
            syncResult.stats.numIoExceptions++;
            return;
        }
        Log.i(TAG, "using country code: " + countryCode);

        // query all contacts
        Cursor cursor = null;
        Uri offlineUri = Users.CONTENT_URI.buildUpon()
            .appendQueryParameter(Users.OFFLINE, "true").build();
        try {
            cursor = usersProvider.query(offlineUri,
                new String[] { Users.HASH, Users.NUMBER, Users.LOOKUP_KEY },
                null, null, null);
        }
        catch (RemoteException e) {
            Log.e(TAG, "error querying users database - aborting sync", e);
            syncResult.databaseError = true;
            return;
        }

        while (cursor.moveToNext()) {
            if (mCanceled) {
                cursor.close();
                throw new OperationCanceledException();
            }

            String hash = cursor.getString(0);
            String number = cursor.getString(1);
            String lookupKey = cursor.getString(2);

            // a phone number with less than 4 digits???
            if (number.length() < 4)
                continue;

            // fix number
            try {
                number = NumberValidator.fixNumber(mContext, number, account.name, null);
            }
            catch (Exception e) {
                Log.e(TAG, "unable to normalize number: " + number + " - skipping", e);
                // skip number
                continue;
            }

            // avoid to send duplicates to server
            if (lookupNumbers.put(hash, new RawPhoneNumberEntry(lookupKey, number, hash)) == null)
                hashList.add(hash);
        }
        cursor.close();

        if (mCanceled) throw new OperationCanceledException();

        // empty contacts :-|
        if (hashList.size() == 0) {
            // delete all Kontalk raw contacts
            try {
                syncResult.stats.numDeletes += deleteAll(account, provider);
            }
            catch (Exception e) {
                Log.e(TAG, "contact delete error", e);
                syncResult.databaseError = true;
            }
            return;
        }

        else {
            // bind to message center to make it do the dirty stuff :)
            ClientServiceConnection conn = new ClientServiceConnection(hashList);
            if (!mContext.bindService(
                    new Intent(mContext, MessageCenterService.class), conn,
                    Context.BIND_AUTO_CREATE)) {
                // cannot bind :(
                Log.e(TAG, "unable to bind to message center!");
                syncResult.stats.numIoExceptions++;
            }

            // wait for the service connection to complete its job
            synchronized (this) {
                try {
                    wait(MAX_WAIT_TIME);
                }
                catch (InterruptedException e) {
                    // simulate canceled operation
                    throw new OperationCanceledException();
                }
            }

            // last chance to quit
            if (mCanceled) throw new OperationCanceledException();

            UserLookupResponse res = conn.getResponse();
            if (res != null) {
                ArrayList<ContentProviderOperation> operations =
                    new ArrayList<ContentProviderOperation>();
                // TODO operations.size() could be used instead (?)
                int op = 0;

                // this is the time - delete all Kontalk raw contacts
                try {
                    syncResult.stats.numDeletes += deleteAll(account, provider);
                }
                catch (Exception e) {
                    Log.e(TAG, "contact delete error", e);
                    syncResult.databaseError = true;
                    return;
                }

                ContentValues registeredValues = new ContentValues(3);
                registeredValues.put(Users.REGISTERED, 1);
                for (int i = 0; i < res.getEntryCount(); i++) {
                    UserLookupResponse.Entry entry = res.getEntry(i);
                    String userId = entry.getUserId().toString();
                    final RawPhoneNumberEntry data = lookupNumbers.get(userId);
                    if (data != null) {
                        // add contact
                        addContact(account,
                                getDisplayName(provider, data.lookupKey, data.number),
                                data.number, data.hash, -1, operations, op);
                        op++;
                    }
                    else {
                        syncResult.stats.numSkippedEntries++;
                    }
                    // update fields
                    try {
                        if (entry.hasStatus())
                            registeredValues.put(Users.STATUS, entry.getStatus());
                        else
                            registeredValues.remove(Users.STATUS);
                        if (entry.hasTimestamp())
                            registeredValues.put(Users.LAST_SEEN, entry.getTimestamp());
                        else
                            registeredValues.remove(Users.LAST_SEEN);

                        usersProvider.update(offlineUri, registeredValues,
                            Users.HASH + " = ?", new String[] { data.hash });
                    }
                    catch (RemoteException e) {
                        Log.e(TAG, "error updating users database", e);
                        // we shall continue here...
                    }
                }

                try {
                    provider.applyBatch(operations);
                    syncResult.stats.numInserts += op;
                    syncResult.stats.numEntries += op;
                }
                catch (Exception e) {
                    Log.e(TAG, "contact write error", e);
                    syncResult.stats.numSkippedEntries = op;
                    syncResult.databaseError = true;
                    return;
                }

                // commit users table
                uri = Users.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Users.RESYNC, "true")
                    .appendQueryParameter(Users.COMMIT, "true")
                    .build();
                try {
                    usersProvider.update(uri, new ContentValues(), null, null);
                    Log.d(TAG, "users database committed");
                }
                catch (RemoteException e) {
                    Log.e(TAG, "error committing users database - aborting sync", e);
                    syncResult.databaseError = true;
                    return;
                }
            }

            // timeout or error
            else {
                Throwable exc = conn.getLastError();
                if (exc != null) {
                    Log.e(TAG, "network error - aborting sync", exc);
                }
                else {
                    Log.w(TAG, "connection timeout - aborting sync");
                }

                syncResult.stats.numIoExceptions++;
            }
        }
    }

    public static boolean isError(SyncResult syncResult) {
        return syncResult.databaseError || syncResult.stats.numIoExceptions > 0;
    }

    private String getDisplayName(ContentProviderClient client, String lookupKey, String defaultValue) {
        String displayName = null;
        Cursor nameQuery = null;
        try {
            nameQuery = client.query(
                    Uri.withAppendedPath(ContactsContract.Contacts
                            .CONTENT_LOOKUP_URI, lookupKey),
                            new String[] { ContactsContract.Contacts.DISPLAY_NAME },
                            null, null, null);
            if (nameQuery.moveToFirst())
                displayName = nameQuery.getString(0);
        }
        catch (Exception e) {
            // ignored
        }
        finally {
            // close cursor
            try {
                nameQuery.close();
            }
            catch (Exception e) {}
        }

        return (displayName != null) ? displayName : defaultValue;
    }

    private int deleteAll(Account account, ContentProviderClient provider)
            throws RemoteException {
        return provider.delete(RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
            .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
            .build(), null, null);
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

    private void addContact(Account account, String username, String phone, String hash,
            long rowContactId, List<ContentProviderOperation> operations, int index) {
        Log.d(TAG, "adding contact username = \"" + username + "\", phone: " + phone);
        ContentProviderOperation.Builder builder;
        final int NUM_OPS = 3;

        if (rowContactId < 0) {
            // create our RawContact
            builder = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
            builder.withValue(RAW_COLUMN_DISPLAY_NAME, username);
            builder.withValue(RAW_COLUMN_PHONE, phone);
            builder.withValue(RAW_COLUMN_USERID, hash);

            operations.add(builder.build());
        }

        // create a Data record of common type 'StructuredName' for our RawContact
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        if (rowContactId < 0)
            builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, index * NUM_OPS);
        else
            builder.withValue(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, rowContactId);

        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, username);
        operations.add(builder.build());

        // create a Data record of custom type 'org.kontalk.user' to display a link to the conversation
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        if (rowContactId < 0)
            builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index * NUM_OPS);
        else
            builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, rowContactId);

        builder.withValue(ContactsContract.Data.MIMETYPE, Users.CONTENT_ITEM_TYPE);
        builder.withValue(DATA_COLUMN_DISPLAY_NAME, username);
        builder.withValue(DATA_COLUMN_ACCOUNT_NAME, mContext.getString(R.string.app_name));
        builder.withValue(DATA_COLUMN_PHONE, phone);

        builder.withYieldAllowed(true);
        operations.add(builder.build());
    }

}

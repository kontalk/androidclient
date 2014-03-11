/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.util.StringUtils;
import org.kontalk.R;
import org.kontalk.client.NumberValidator;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.service.MessageCenterService;
import org.kontalk.util.MessageUtils;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;


/**
 * The syncer core.
 * @author Daniele Ricci
 */
public class Syncer {
    // using SyncAdapter tag
    private static final String TAG = SyncAdapter.class.getSimpleName();

    // max time to wait for network response
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
    private LocalBroadcastManager mLocalBroadcastManager;

    private final static class PresenceItem {
        public String from;
        public String status;
        public long timestamp;
        public byte[] publicKey;
    }

    // FIXME this class should handle most recent/available presence stanzas
    private static final class PresenceBroadcastReceiver extends BroadcastReceiver {
        private List<PresenceItem> response;
        private final WeakReference<Syncer> notifyTo;
        private final String iq;
        private final List<String> hashList;
        private int presenceCount = -1;
        private int vCardCount = -1;
        private int rosterCount = -1;

        public PresenceBroadcastReceiver(String iq, List<String> hashList, Syncer notifyTo) {
            this.notifyTo = new WeakReference<Syncer>(notifyTo);
            this.iq = iq;
            this.hashList = hashList;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (MessageCenterService.ACTION_PRESENCE.equals(action)) {
                // consider only presences received *after* roster response
                if (response != null) {
                    String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                    // see if bare JID is present in roster response
                    String compare = StringUtils.parseBareAddress(jid);
                    for (PresenceItem item : response) {
                        if (StringUtils.parseBareAddress(item.from).equalsIgnoreCase(compare)) {
                            item.status = intent.getStringExtra(MessageCenterService.EXTRA_STATUS);
                            item.timestamp = intent.getLongExtra(MessageCenterService.EXTRA_STAMP, -1);

                            // increment presence count
                            if (presenceCount < 0)
                                presenceCount = 1;
                            else
                                presenceCount++;

                            break;
                        }
                    }

                    // done with presence data
                    if (rosterCount >= 0 && presenceCount >= rosterCount && vCardCount >= presenceCount)
                        finish();
                }
            }

            // roster result received
            else if (MessageCenterService.ACTION_ROSTER.equals(action)) {
                String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                if (iq.equals(id)) {
                    String[] list = intent.getStringArrayExtra(MessageCenterService.EXTRA_JIDLIST);
                    rosterCount = list.length;
                    // prepare list to be filled in with presence data
                    response = new ArrayList<PresenceItem>(rosterCount);
                    for (String jid : list) {
                        PresenceItem p = new PresenceItem();
                        p.from = jid;
                        response.add(p);
                    }

                    // all presence data already received (WHATT???)
                    if (rosterCount == 0 || (presenceCount >= 0 && rosterCount >= presenceCount))
                        finish();
                }
            }

            else if (MessageCenterService.ACTION_VCARD.equals(action)) {
                if (response != null) {
                    String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                    // see if bare JID is present in roster response
                    String compare = StringUtils.parseBareAddress(jid);
                    for (PresenceItem item : response) {
                        if (StringUtils.parseBareAddress(item.from).equalsIgnoreCase(compare)) {
                            item.publicKey = intent.getByteArrayExtra(MessageCenterService.EXTRA_PUBLIC_KEY);

                            // increment vcard count
                            if (vCardCount < 0)
                                vCardCount = 1;
                            else
                                vCardCount++;

                            break;
                        }
                    }

                    // done with presence data
                    if (rosterCount >= 0 && presenceCount >= rosterCount && vCardCount >= presenceCount)
                        finish();
                }

            }

            // connected! Retry...
            else if (MessageCenterService.ACTION_CONNECTED.equals(action)) {
                /*
                 * TODO there might be concurrency problem here. If we send the
                 * roster intent and in the meanwhile message center was already
                 * connecting, we risk sending it twice: one with the pending
                 * intent and one here.
                 *
                 * In order to cope with that, we would need another broadcast
                 * receiver only for CONNECTED/DISCONNECTED in order to keep
                 * track of message center connections/disconnections. That way
                 * we can distinguish a fresh connect with a race condition
                 * case.
                 */
                Syncer w = notifyTo.get();
                if (w != null)
                    w.sendRoster(iq, hashList);
            }
        }

        public List<PresenceItem> getResponse() {
            return (rosterCount >= 0) ? response : null;
        }

        private void finish() {
            Syncer w = notifyTo.get();
            if (w != null)
                synchronized (w) {
                    w.notifyAll();
                }
        }
    }

    public Syncer(Context context) {
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
                number = NumberValidator.fixNumber(mContext, number, account.name, 0);
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
            mLocalBroadcastManager = LocalBroadcastManager.getInstance(mContext);

            // register presence broadcast receiver
            String iq = Packet.nextID();
            PresenceBroadcastReceiver receiver = new PresenceBroadcastReceiver(iq, hashList, this);
            IntentFilter f = new IntentFilter();
            f.addAction(MessageCenterService.ACTION_PRESENCE);
            f.addAction(MessageCenterService.ACTION_ROSTER);
            f.addAction(MessageCenterService.ACTION_CONNECTED);
            f.addAction(MessageCenterService.ACTION_VCARD);
            mLocalBroadcastManager.registerReceiver(receiver, f);

            // request current connection status
            Intent intent = new Intent(mContext, MessageCenterService.class);
            intent.setAction(MessageCenterService.ACTION_CONNECTED);
            mContext.startService(intent);

            // wait for the service to complete its job
            synchronized (this) {
                // wait for connection
                try {
                    wait(MAX_WAIT_TIME);
                }
                catch (InterruptedException e) {
                    // simulate canceled operation
                    mCanceled = true;
                }
            }

            mLocalBroadcastManager.unregisterReceiver(receiver);

            // last chance to quit
            if (mCanceled) throw new OperationCanceledException();

            List<PresenceItem> res = receiver.getResponse();
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
                for (int i = 0; i < res.size(); i++) {
                    PresenceItem entry = res.get(i);
                    String userId = StringUtils.parseName(entry.from);

                    final RawPhoneNumberEntry data = lookupNumbers.get(userId);
                    if (data != null) {
                        // add contact
                        addContact(account,
                                getDisplayName(provider, data.lookupKey, data.number),
                                data.number, data.hash, operations, op);
                        op++;
                    }
                    else {
                        syncResult.stats.numSkippedEntries++;
                    }

                    // update fields
                    try {
                        String status = entry.status;

                        if (!TextUtils.isEmpty(status))
                            registeredValues.put(Users.STATUS, status);
                        else
                            registeredValues.putNull(Users.STATUS);

                        if (entry.timestamp >= 0)
                            registeredValues.put(Users.LAST_SEEN, entry.timestamp);
                        else
                            registeredValues.putNull(Users.LAST_SEEN);

                        if (entry.publicKey != null) {
                            registeredValues.put(Users.PUBLIC_KEY, entry.publicKey);

                            try {
	                            byte[] fp = PGP.getMasterKey(entry.publicKey).getFingerprint();
	                            registeredValues.put(Users.FINGERPRINT, MessageUtils.bytesToHex(fp));
                            }
                            catch (Exception e) {
                            	Log.w(TAG, "unable to parse public key", e);
                            	registeredValues.putNull(Users.FINGERPRINT);
                            }
                        }
                        else
                            registeredValues.putNull(Users.PUBLIC_KEY);

                        usersProvider.update(offlineUri, registeredValues,
                            Users.HASH + " = ?", new String[] { userId });
                    }
                    catch (RemoteException e) {
                        Log.e(TAG, "error updating users database", e);
                        // we shall continue here...
                    }
                }

                try {
                    if (operations.size() > 0)
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
                    usersProvider.update(uri, null, null, null);
                    Log.d(TAG, "users database committed");
                    Contact.invalidate();
                }
                catch (RemoteException e) {
                    Log.e(TAG, "error committing users database - aborting sync", e);
                    syncResult.databaseError = true;
                    return;
                }
            }

            // timeout or error
            else {
                /* TODO
                Throwable exc = conn.getLastError();
                if (exc != null) {
                    Log.e(TAG, "network error - aborting sync", exc);
                }
                else {*/
                    Log.w(TAG, "connection timeout - aborting sync");
                //}

                syncResult.stats.numIoExceptions++;
            }
        }
    }

    public static boolean isError(SyncResult syncResult) {
        return syncResult.databaseError || syncResult.stats.numIoExceptions > 0;
    }

    private void sendRoster(String id, List<String> list) {
        Intent i = new Intent(mContext, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_ROSTER);
        i.putExtra(MessageCenterService.EXTRA_PACKET_ID, id);
        i.putExtra(MessageCenterService.EXTRA_USERLIST, list.toArray(new String[0]));
        mContext.startService(i);
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
            List<ContentProviderOperation> operations, int index) {
        Log.d(TAG, "adding contact username = \"" + username + "\", phone: " + phone);
        ContentProviderOperation.Builder builder;
        final int NUM_OPS = 4;

        // create our RawContact
        builder = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT)
            .withValue(RawContacts.ACCOUNT_NAME, account.name)
            .withValue(RawContacts.ACCOUNT_TYPE, account.type)
            .withValue(RAW_COLUMN_DISPLAY_NAME, username)
            .withValue(RAW_COLUMN_PHONE, phone)
            .withValue(RAW_COLUMN_USERID, hash);
        operations.add(builder.build());

        // create a Data record of common type 'StructuredName' for our RawContact
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, index * NUM_OPS)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, username);
        operations.add(builder.build());

        // create a Data record of common type 'Phone' for our RawContact
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID, index * NUM_OPS)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            // not including phone type will crash on HTC devices
            .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_OTHER)
            .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone);
        operations.add(builder.build());

        // create a Data record of custom type 'org.kontalk.user' to display a link to the conversation
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, index * NUM_OPS)
            .withValue(ContactsContract.Data.MIMETYPE, Users.CONTENT_ITEM_TYPE)
            .withValue(DATA_COLUMN_DISPLAY_NAME, username)
            .withValue(DATA_COLUMN_ACCOUNT_NAME, mContext.getString(R.string.app_name))
            .withValue(DATA_COLUMN_PHONE, phone)
            .withYieldAllowed(true);
        operations.add(builder.build());
    }

}

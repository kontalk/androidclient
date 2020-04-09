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

package org.kontalk.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.greenrobot.eventbus.EventBus;
import org.jxmpp.util.XmppStringUtils;
import org.bouncycastle.openpgp.PGPPublicKey;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.authenticator.MyAccount;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PGPUserID;
import org.kontalk.data.Contact;
import org.kontalk.provider.Keyring;
import org.kontalk.provider.MyUsers;
import org.kontalk.provider.MyUsers.Users;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.XMPPUtils;


/**
 * The syncer core.
 * @author Daniele Ricci
 */
public class Syncer {
    // using SyncAdapter tag
    private static final String TAG = SyncAdapter.TAG;

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
    /** {@link RawContacts} column for the JID. */
    public static final String RAW_COLUMN_USERID = RawContacts.SYNC3;

    private volatile boolean mCanceled;
    private final Context mContext;

    private final EventBus mServiceBus = MessageCenterService.bus();

    Syncer(Context context) {
        mContext = context;
    }

    void onSyncCanceled() {
        mCanceled = true;
    }

    public void onSyncResumed() {
        mCanceled = false;
    }

    private static final class RawPhoneNumberEntry {
        public final String number;
        public final String jid;
        public final String lookupKey;

        public RawPhoneNumberEntry(String lookupKey, String number, String jid) {
            this.lookupKey = lookupKey;
            this.number = number;
            this.jid = jid;
        }
    }

    /**
     * The actual sync procedure.
     * This one uses the slowest method ever: it first checks for every phone
     * number in all contacts and it sends them to the server. Once a response
     * is received, it deletes all the raw contacts created by us and then
     * recreates only the ones the server has found a match for.
     */
    void performSync(Context context, Account account, String authority,
        ContentProviderClient provider, ContentProviderClient usersProvider,
        SyncResult syncResult)
            throws OperationCanceledException {

        final MyAccount myAccount = Authenticator.fromSystemAccount(context, account);
        final Map<String, RawPhoneNumberEntry> lookupNumbers = new HashMap<>();
        final List<String> jidList = new ArrayList<>();

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
        catch (Exception e) {
            Log.e(TAG, "error resyncing users database - aborting sync", e);
            syncResult.databaseError = true;
            return;
        }

        // query all contacts
        Cursor cursor;
        try {
            cursor = usersProvider.query(Users.CONTENT_URI_OFFLINE,
                new String[] { Users.JID, Users.NUMBER, Users.LOOKUP_KEY },
                null, null, null);
        }
        catch (Exception e) {
            Log.e(TAG, "error querying users database - aborting sync", e);
            syncResult.databaseError = true;
            return;
        }

        while (cursor.moveToNext()) {
            if (mCanceled) {
                cursor.close();
                throw new OperationCanceledException();
            }

            String jid = cursor.getString(0);
            String number = cursor.getString(1);
            String lookupKey = cursor.getString(2);

            // avoid to send duplicates to the server
            if (lookupNumbers.put(XmppStringUtils.parseLocalpart(jid),
                    new RawPhoneNumberEntry(lookupKey, number, jid)) == null)
                jidList.add(jid);
        }
        cursor.close();

        if (mCanceled) throw new OperationCanceledException();

        // empty contacts :-|
        if (jidList.size() == 0) {
            // delete all Kontalk raw contacts
            try {
                syncResult.stats.numDeletes += deleteAll(account, provider);
            }
            catch (Exception e) {
                Log.e(TAG, "contact delete error", e);
                syncResult.databaseError = true;
            }
            try {
                syncResult.stats.numDeletes += deleteProfile(account, provider);
            }
            catch (Exception e) {
                Log.e(TAG, "profile delete error", e);
                syncResult.databaseError = true;
            }

            commit(usersProvider, syncResult);
        }

        else {
            // register to events
            // registering will request current connection status and proceed
            SyncProcedure receiver = new SyncProcedure(jidList, this);
            mServiceBus.register(receiver);

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

            mServiceBus.unregister(receiver);

            // last chance to quit
            if (mCanceled) throw new OperationCanceledException();

            List<SyncProcedure.PresenceItem> res = receiver.getResponse();
            if (res != null) {
                ArrayList<ContentProviderOperation> operations =
                    new ArrayList<>();
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
                try {
                    syncResult.stats.numDeletes += deleteProfile(account, provider);
                }
                catch (Exception e) {
                    Log.e(TAG, "profile delete error", e);
                    syncResult.databaseError = true;
                }

                ContentValues registeredValues = new ContentValues();
                registeredValues.put(Users.REGISTERED, 1);
                for (int i = 0; i < res.size(); i++) {
                    SyncProcedure.PresenceItem entry = res.get(i);
                    if (entry.discarded)
                        continue;

                    final RawPhoneNumberEntry data = lookupNumbers
                        .get(entry.from.getLocalpartOrThrow().toString());
                    if (data != null && data.lookupKey != null) {
                        // add contact
                        addContact(account,
                                getDisplayName(provider, data.lookupKey, data.number),
                                data.number, data.jid, operations, op++);
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
                            try {
                                PGPPublicKey pubKey = PGP.getMasterKey(entry.publicKey);
                                // trust our own key blindly
                                int trustLevel = myAccount.isSelfJID(entry.from) ?
                                    MyUsers.Keys.TRUST_VERIFIED : -1;
                                // update keys table immediately
                                Keyring.setKey(mContext, entry.from.toString(), entry.publicKey, trustLevel);

                                // no data from system contacts, use name from public key
                                if (data == null) {
                                    PGPUserID uid = PGP.parseUserId(pubKey, entry.from.getDomain().toString());
                                    if (uid != null) {
                                        registeredValues.put(Users.DISPLAY_NAME, uid.getName());
                                    }
                                }
                            }
                            catch (Exception e) {
                                Log.w(TAG, "unable to parse public key", e);
                            }
                        }
                        else {
                            // use roster name if no contact data available
                            if (data == null && entry.rosterName != null) {
                                registeredValues.put(Users.DISPLAY_NAME, entry.rosterName);
                            }
                        }

                        // blocked status
                        registeredValues.put(Users.BLOCKED, entry.blocked);
                        // user JID as reported by the server
                        registeredValues.put(Users.JID, entry.from.toString());

                        /*
                         * Since UsersProvider.resync inserted the user row
                         * using our server name, it might have changed because
                         * of what the server reported. We already put into the
                         * values the new JID, but we need to use the old one
                         * in the where condition so we will have a match.
                         */
                        String origJid;
                        if (data != null)
                            origJid = XMPPUtils.createLocalJID(XmppStringUtils
                                .parseLocalpart(entry.from.toString()));
                        else
                            origJid = entry.from.toString();
                        usersProvider.update(Users.CONTENT_URI_OFFLINE, registeredValues,
                            Users.JID + " = ?", new String[] { origJid });

                        // clear data
                        registeredValues.remove(Users.DISPLAY_NAME);

                        // if this is our own contact, trust our own key later
                        if (myAccount.isSelfJID(entry.from)) {
                            // register our profile while we're at it
                            if (data != null) {
                                // add contact
                                String displayName = myAccount.getDisplayName();
                                addProfile(account, displayName,
                                    data.number, data.jid,
                                    operations, op++);
                            }
                        }
                    }
                    catch (Exception e) {
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
                    Log.w(TAG, "contact write error", e);
                    syncResult.stats.numSkippedEntries += op;
                    /*
                     * We do not consider system contacts failure a fatal error.
                     * This is actually a workaround for systems with disabled permissions or
                     * exotic firmwares. It can also protect against security 3rd party apps or
                     * non-Android platforms, such as Jolla/Alien Dalvik.
                     */
                }

                commit(usersProvider, syncResult);
            }

            // timeout or error
            else {
                Log.w(TAG, "connection timeout - aborting sync");

                syncResult.stats.numIoExceptions++;
            }
        }
    }

    private void commit(ContentProviderClient usersProvider, SyncResult syncResult) {
        // commit users table
        Uri uri = Users.CONTENT_URI.buildUpon()
            .appendQueryParameter(Users.RESYNC, "true")
            .appendQueryParameter(Users.COMMIT, "true")
            .build();
        try {
            usersProvider.update(uri, null, null, null);
            Log.d(TAG, "users database committed");
            Contact.invalidate();
        }
        catch (Exception e) {
            Log.e(TAG, "error committing users database - aborting sync", e);
            syncResult.databaseError = true;
        }
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
            catch (Exception ignored) {
            }
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

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private int deleteProfile(Account account, ContentProviderClient provider)
            throws RemoteException {
        return provider.delete(ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI.buildUpon()
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

    private void addContact(Account account, String username, String phone, String jid,
            List<ContentProviderOperation> operations, int index) {
        if (Log.isDebug()) {
            Log.d(TAG, "adding contact \"" + username + "\" <" + phone + ">");
        }

        // create our RawContact
        operations.add(insertRawContact(account, username, phone, jid,
            RawContacts.CONTENT_URI).build());

        // add contact data
        addContactData(username, phone, operations, index);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void addProfile(Account account, String username, String phone, String jid,
            List<ContentProviderOperation> operations, int index) {
        if (Log.isDebug()) {
            Log.d(TAG, "adding profile \"" + username + "\" <" + phone + ">");
        }

        // create our RawContact
        operations.add(insertRawContact(account, username, phone, jid,
            ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI).build());

        // add contact data
        addContactData(username, phone, operations, index);
    }

    private ContentProviderOperation.Builder insertRawContact(Account account, String username, String phone, String jid, Uri uri) {
        return ContentProviderOperation.newInsert(uri)
            .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT)
            .withValue(RawContacts.ACCOUNT_NAME, account.name)
            .withValue(RawContacts.ACCOUNT_TYPE, account.type)
            .withValue(RAW_COLUMN_DISPLAY_NAME, username)
            .withValue(RAW_COLUMN_PHONE, phone)
            .withValue(RAW_COLUMN_USERID, jid);
    }

    private void addContactData(String username, String phone, List<ContentProviderOperation> operations, int index) {
        ContentProviderOperation.Builder builder;
        final int opIndex = index * 3;

        // create a Data record of common type 'StructuredName' for our RawContact
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, opIndex)
            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, username);
        operations.add(builder.build());

        // create a Data record of custom type 'org.kontalk.user' to display a link to the conversation
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, opIndex)
            .withValue(ContactsContract.Data.MIMETYPE, Users.CONTENT_ITEM_TYPE)
            .withValue(DATA_COLUMN_DISPLAY_NAME, username)
            .withValue(DATA_COLUMN_ACCOUNT_NAME, mContext.getString(R.string.app_name))
            .withValue(DATA_COLUMN_PHONE, phone)
            .withYieldAllowed(true);
        operations.add(builder.build());
    }

}

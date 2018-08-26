/*
 * Kontalk Android client
 * Copyright (C) 2018 Kontalk Devteam <devteam@kontalk.org>

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.util.StringUtils;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKey;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.Process;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
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

    /** Random packet id used for requesting public keys. */
    static final String IQ_KEYS_PACKET_ID = StringUtils.randomString(10);

    /** Random packet id used for requesting the blocklist. */
    static final String IQ_BLOCKLIST_PACKET_ID = StringUtils.randomString(10);

    private volatile boolean mCanceled;
    private final Context mContext;

    private final static class PresenceItem {
        public String from;
        public String status;
        public String rosterName;
        public long timestamp;
        public byte[] publicKey;
        public boolean blocked;
        public boolean presence;
        /** True if found during roster match. */
        public boolean matched;
        /** Discard this entry: it has not been found on server. */
        public boolean discarded;
    }

    // FIXME this class should handle most recent/available presence stanzas
    private static final class PresenceBroadcastReceiver extends BroadcastReceiver {
        /** Max number of items in a roster match request. */
        private static final int MAX_ROSTER_MATCH_SIZE = 500;

        private List<PresenceItem> response;
        private final WeakReference<Syncer> notifyTo;

        private final List<String> jidList;
        private int rosterParts = -1;
        private String[] iq;
        private String presenceId;

        private int presenceCount;
        private int pubkeyCount;
        private int rosterCount;
        /** Packet id list for not matched contacts (in roster but not matched on server). */
        private Set<String> notMatched = new HashSet<>();
        private boolean blocklistReceived;

        public PresenceBroadcastReceiver(List<String> jidList, Syncer notifyTo) {
            this.notifyTo = new WeakReference<>(notifyTo);
            this.jidList = jidList;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (MessageCenterService.ACTION_PRESENCE.equals(action)) {

                // consider only presences received *after* roster response
                if (response != null && presenceId != null) {

                    String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                    String type = intent.getStringExtra(MessageCenterService.EXTRA_TYPE);
                    String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                    if (type != null && presenceId.equals(id)) {
                        // update presence item data
                        String bareJid = XmppStringUtils.parseBareJid(jid);
                        PresenceItem item = getPresenceItem(bareJid);
                        item.status = intent.getStringExtra(MessageCenterService.EXTRA_STATUS);
                        item.timestamp = intent.getLongExtra(MessageCenterService.EXTRA_STAMP, -1);
                        item.rosterName = intent.getStringExtra(MessageCenterService.EXTRA_ROSTER_NAME);
                        if (!item.presence) {
                            item.presence = true;
                            // increment presence count
                            presenceCount++;
                            // check user existance (only if subscription is "both")
                            if (!item.matched && intent.getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_FROM, false) &&
                                intent.getBooleanExtra(MessageCenterService.EXTRA_SUBSCRIBED_TO, false)) {
                                // verify actual user existance through last activity
                                String lastActivityId = StringUtils.randomString(6);
                                MessageCenterService.requestLastActivity(context, item.from, lastActivityId);
                                notMatched.add(lastActivityId);
                            }
                        }
                    }
                }
            }

            // roster match result received
            else if (MessageCenterService.ACTION_ROSTER_MATCH.equals(action)) {
                String id = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                for (String iqId : iq) {
                    if (iqId.equals(id)) {
                        // decrease roster parts counter
                        rosterParts--;

                        String[] list = intent.getStringArrayExtra(MessageCenterService.EXTRA_JIDLIST);
                        if (list != null) {
                            rosterCount += list.length;
                            if (response == null) {
                                // prepare list to be filled in with presence data
                                response = new ArrayList<>(rosterCount);
                            }
                            for (String jid : list) {
                                PresenceItem p = new PresenceItem();
                                p.from = jid;
                                p.matched = true;
                                response.add(p);
                            }
                        }

                        if (rosterParts <= 0) {
                            // all roster parts received

                            if (rosterCount == 0 && blocklistReceived) {
                                // no roster elements
                                finish();
                            }
                            else {
                                Syncer w = notifyTo.get();
                                if (w != null) {
                                    // request presence data for the whole roster
                                    presenceId = StringUtils.randomString(6);
                                    w.requestPresenceData(presenceId);
                                    // request public keys for the whole roster
                                    w.requestPublicKeys();
                                    // request block list
                                    w.requestBlocklist();
                                }
                            }
                        }

                        // no need to continue
                        break;
                    }
                }
            }

            else if (MessageCenterService.ACTION_PUBLICKEY.equals(action)) {
                if (response != null) {
                    String requestId = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                    if (IQ_KEYS_PACKET_ID.equals(requestId)) {
                        String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                        // see if bare JID is present in roster response
                        String compare = XmppStringUtils.parseBareJid(jid);
                        for (PresenceItem item : response) {
                            if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(compare)) {
                                item.publicKey = intent.getByteArrayExtra(MessageCenterService.EXTRA_PUBLIC_KEY);

                                // increment vcard count
                                pubkeyCount++;
                                break;
                            }
                        }

                        // done with presence data and blocklist
                        if (pubkeyCount == presenceCount && blocklistReceived && notMatched.size() == 0)
                            finish();
                    }
                }

            }

            else if (MessageCenterService.ACTION_BLOCKLIST.equals(action)) {
                String requestId = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                if (IQ_BLOCKLIST_PACKET_ID.equals(requestId)) {
                    blocklistReceived = true;

                    String[] list = intent.getStringArrayExtra(MessageCenterService.EXTRA_BLOCKLIST);
                    if (list != null) {

                        for (String jid : list) {
                            // see if bare JID is present in roster response
                            String compare = XmppStringUtils.parseBareJid(jid);
                            for (PresenceItem item : response) {
                                if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(compare)) {
                                    item.blocked = true;

                                    break;
                                }
                            }
                        }

                    }

                    // done with presence data and blocklist
                    if (pubkeyCount >= presenceCount && notMatched.size() == 0)
                        finish();
                }
            }

            // last activity (for user existance verification)
            else if (MessageCenterService.ACTION_LAST_ACTIVITY.equals(action)) {
                String requestId = intent.getStringExtra(MessageCenterService.EXTRA_PACKET_ID);
                if (notMatched.contains(requestId)) {
                    notMatched.remove(requestId);

                    String type = intent.getStringExtra(MessageCenterService.EXTRA_TYPE);
                    // consider only item-not-found (404) errors
                    if (type != null && type.equalsIgnoreCase(IQ.Type.error.toString()) &&
                            StanzaError.Condition.item_not_found.toString().equals(intent
                                .getStringExtra(MessageCenterService.EXTRA_ERROR_CONDITION))) {
                        // user does not exist!
                        String jid = intent.getStringExtra(MessageCenterService.EXTRA_FROM);
                        // discard entry
                        discardPresenceItem(jid);
                        // unsubscribe!
                        unsubscribe(context, jid);

                        if (pubkeyCount >= presenceCount && blocklistReceived && notMatched.size() == 0)
                            finish();
                    }
                }
            }

            // connected! Retry...
            else if (MessageCenterService.ACTION_CONNECTED.equals(action) && rosterParts < 0) {
                Syncer w = notifyTo.get();
                if (w != null) {
                    // request a roster match
                    rosterParts = getRosterParts(jidList);
                    iq = new String[rosterParts];
                    for (int i = 0; i < rosterParts; i++) {
                        int end = (i+1)*MAX_ROSTER_MATCH_SIZE;
                        if (end >= jidList.size())
                            end = jidList.size();
                        List<String> slice = jidList.subList(i*MAX_ROSTER_MATCH_SIZE, end);

                        iq[i] = StringUtils.randomString(6);
                        w.requestRosterMatch(iq[i], slice);
                    }
                }
            }
        }

        private void discardPresenceItem(String jid) {
            for (PresenceItem item : response) {
                if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(jid)) {
                    item.discarded = true;
                    return;
                }
            }
        }

        private PresenceItem getPresenceItem(String jid) {
            for (PresenceItem item : response) {
                if (XmppStringUtils.parseBareJid(item.from).equalsIgnoreCase(jid))
                    return item;
            }

            // add item if not found
            PresenceItem item = new PresenceItem();
            item.from = jid;
            response.add(item);
            return item;
        }

        private void unsubscribe(Context context, String jid) {
            Intent i = new Intent(context, MessageCenterService.class);
            i.setAction(MessageCenterService.ACTION_PRESENCE);
            i.putExtra(MessageCenterService.EXTRA_TO, jid);
            i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.unsubscribe.name());
            context.startService(i);
        }

        private int getRosterParts(List<String> jidList) {
            return (int) Math.ceil((double) jidList.size() / MAX_ROSTER_MATCH_SIZE);
        }

        public List<PresenceItem> getResponse() {
            return (rosterCount >= 0) ? response : null;
        }

        private void finish() {
            Syncer w = notifyTo.get();
            if (w != null) {
                synchronized (w) {
                    w.notifyAll();
                }
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
    public void performSync(Context context, Account account, String authority,
        ContentProviderClient provider, ContentProviderClient usersProvider,
        SyncResult syncResult)
            throws OperationCanceledException {

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
            final LocalBroadcastManager lbm = LocalBroadcastManager
                .getInstance(mContext);

            // register presence broadcast receiver
            PresenceBroadcastReceiver receiver = new PresenceBroadcastReceiver(jidList, this);
            IntentFilter f = new IntentFilter();
            f.addAction(MessageCenterService.ACTION_PRESENCE);
            f.addAction(MessageCenterService.ACTION_ROSTER_MATCH);
            f.addAction(MessageCenterService.ACTION_PUBLICKEY);
            f.addAction(MessageCenterService.ACTION_BLOCKLIST);
            f.addAction(MessageCenterService.ACTION_LAST_ACTIVITY);
            f.addAction(MessageCenterService.ACTION_CONNECTED);
            lbm.registerReceiver(receiver, f);

            // request current connection status
            MessageCenterService.requestConnectionStatus(mContext);

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

            lbm.unregisterReceiver(receiver);

            // last chance to quit
            if (mCanceled) throw new OperationCanceledException();

            List<PresenceItem> res = receiver.getResponse();
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
                    PresenceItem entry = res.get(i);
                    if (entry.discarded)
                        continue;

                    final RawPhoneNumberEntry data = lookupNumbers
                        .get(XmppStringUtils.parseLocalpart(entry.from));
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
                                int trustLevel = Authenticator.isSelfJID(mContext, entry.from) ?
                                    MyUsers.Keys.TRUST_VERIFIED : -1;
                                // update keys table immediately
                                Keyring.setKey(mContext, entry.from, entry.publicKey, trustLevel);

                                // no data from system contacts, use name from public key
                                if (data == null) {
                                    PGPUserID uid = PGP.parseUserId(pubKey, XmppStringUtils.parseDomain(entry.from));
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
                        registeredValues.put(Users.JID, entry.from);

                        /*
                         * Since UsersProvider.resync inserted the user row
                         * using our server name, it might have changed because
                         * of what the server reported. We already put into the
                         * values the new JID, but we need to use the old one
                         * in the where condition so we will have a match.
                         */
                        String origJid;
                        if (data != null)
                            origJid = XMPPUtils.createLocalJID(mContext,
                                XmppStringUtils.parseLocalpart(entry.from));
                        else
                            origJid = entry.from;
                        usersProvider.update(Users.CONTENT_URI_OFFLINE, registeredValues,
                            Users.JID + " = ?", new String[] { origJid });

                        // clear data
                        registeredValues.remove(Users.DISPLAY_NAME);

                        // if this is our own contact, trust our own key later
                        if (Authenticator.isSelfJID(mContext, entry.from)) {
                            // register our profile while we're at it
                            if (data != null) {
                                // add contact
                                addProfile(account,
                                    Authenticator.getDefaultDisplayName(mContext),
                                    data.number, data.jid, operations, op++);
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

    void requestRosterMatch(String id, List<String> list) {
        Intent i = new Intent(mContext, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_ROSTER_MATCH);
        i.putExtra(MessageCenterService.EXTRA_PACKET_ID, id);
        i.putExtra(MessageCenterService.EXTRA_JIDLIST, list.toArray(new String[list.size()]));
        mContext.startService(i);
    }

    void requestPresenceData(String id) {
        Intent i = new Intent(mContext, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_PRESENCE);
        i.putExtra(MessageCenterService.EXTRA_TYPE, Presence.Type.probe.toString());
        i.putExtra(MessageCenterService.EXTRA_PACKET_ID, id);
        mContext.startService(i);
    }

    void requestPublicKeys() {
        Intent i = new Intent(mContext, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_PUBLICKEY);
        i.putExtra(MessageCenterService.EXTRA_PACKET_ID, IQ_KEYS_PACKET_ID);
        mContext.startService(i);
    }

    void requestBlocklist() {
        Intent i = new Intent(mContext, MessageCenterService.class);
        i.setAction(MessageCenterService.ACTION_BLOCKLIST);
        i.putExtra(MessageCenterService.EXTRA_PACKET_ID, IQ_BLOCKLIST_PACKET_ID);
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
            catch (Exception ignored) {}
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

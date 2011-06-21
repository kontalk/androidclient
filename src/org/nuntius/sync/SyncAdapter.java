package org.nuntius.sync;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuntius.R;
import org.nuntius.authenticator.Authenticator;
import org.nuntius.client.EndpointServer;
import org.nuntius.client.RequestClient;
import org.nuntius.client.StatusResponse;
import org.nuntius.provider.MyUsers.Users;
import org.nuntius.ui.MessagingPreferences;
import org.nuntius.util.MessageUtils;
import org.xmlpull.v1.XmlSerializer;

import android.accounts.Account;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;

import com.google.i18n.phonenumbers.PhoneNumberUtil;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = SyncAdapter.class.getSimpleName();

    public static final String DATA_COLUMN_DISPLAY_NAME = Data.DATA1;
    public static final String DATA_COLUMN_ACCOUNT_NAME = Data.DATA2;
    public static final String DATA_COLUMN_PHONE = Data.DATA3;

    public static final String RAW_COLUMN_DISPLAY_NAME = RawContacts.SYNC1;
    public static final String RAW_COLUMN_PHONE = RawContacts.SYNC2;
    public static final String RAW_COLUMN_USERID = RawContacts.SYNC3;

    private final Context mContext;
    private final ContentResolver mContentResolver;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mContentResolver = context.getContentResolver();
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        Log.w(TAG, "sync started (authority=" + authority + ")");

        try {
            performSync(mContext, account, extras, authority, provider, syncResult);
        }
        catch (OperationCanceledException e) {
            Log.e(TAG, "sync canceled!", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void performSync(Context context, Account account, Bundle extras,
            String authority, ContentProviderClient provider, SyncResult syncResult)
            throws OperationCanceledException {

        // setup the request client
        final String serverUri = MessagingPreferences.getServerURI(context);
        final String token = Authenticator.getDefaultAccountToken(mContext);
        final RequestClient client = new RequestClient(new EndpointServer(serverUri), token);
        final Map<String,String[]> lookupNumbers = new HashMap<String,String[]>();

        final Cursor cursor = mContentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);

        // FIXME optimize queries
        while (cursor.moveToNext()) {
            String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
            String displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
            Log.w(TAG, "contact " + contactId + ", name: " + displayName);
            final Cursor phones = mContentResolver.query(Phone.CONTENT_URI, null,
                Phone.CONTACT_ID +" = ? AND " +
                Phone.TYPE + " = ?",
                new String[] { contactId, String.valueOf(Phone.TYPE_MOBILE) }, null);

            final TelephonyManager tm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
            final String countryCode = "+" + PhoneNumberUtil.getInstance()
                .getCountryCodeForRegion(tm.getSimCountryIso());

            Log.w(TAG, "using default country code: " + tm.getSimCountryIso() + "(" + countryCode + ")");

            while (phones.moveToNext()) {
                String number = phones.getString(phones.getColumnIndex(Phone.NUMBER));
                final int type = phones.getInt(phones.getColumnIndex(Phone.TYPE));
                switch (type) {
                    case Phone.TYPE_MOBILE:
                        number = PhoneNumberUtils.stripSeparators(number);
                        Log.w(TAG, "found mobile number " + number);

                        // add country code if not found
                        if (number.charAt(0) != '+')
                            number = countryCode + number;

                        Cursor exists = mContentResolver.query(RawContacts.CONTENT_URI,
                            new String[] {
                                RawContacts._ID,
                                RawContacts.ACCOUNT_NAME,
                                RawContacts.ACCOUNT_TYPE,
                                RawContacts.SYNC1,
                                RawContacts.SYNC2
                            },
                            RawContacts.ACCOUNT_NAME + " = ? AND " +
                            RawContacts.ACCOUNT_TYPE + " = ? AND " +
                            RawContacts.SYNC1        + " = ? AND " +
                            RawContacts.SYNC2        + " = ?",
                            new String[] {
                                account.name,
                                account.type,
                                displayName,
                                number
                            }, null);
                        if (!exists.moveToFirst()) {
                            Log.w(TAG, "local contact not present, looking up");
                            try {
                                lookupNumbers.put(MessageUtils.sha1(number), new String[] { displayName, number });
                            } catch (Exception e) {
                                Log.e(TAG, "unable to generate SHA-1 hash for " + number + " - skipping");
                            }
                        }
                        else {
                            Log.w(TAG, "contact already exists ("+
                                    exists.getString(0) + ", " +
                                    exists.getString(1) + ", " +
                                    exists.getString(2) + ", " +
                                    exists.getString(3) + ", " +
                                    exists.getString(4) +
                                    ")");
                            /*
                            TODO figure out how to readd or verify which part of the contact exists
                            Cursor cc2 = mContentResolver.query(ContactsContract.Data.CONTENT_URI, null, null, null, null);
                            while (cc2.moveToNext()) {
                                for (int i = 0; i < cc2.getColumnCount(); i++) {
                                    Log.i(TAG, "contact-" + i + ": " + cc2.getString(i));
                                }
                            }
                            cc2.close();
                            //String rawId = String.valueOf(exists.getLong(0));
                            int r1, r2 = -50;
                            r1 = deleteContact(exists.getLong(0));
                            //r1 = mContentResolver.delete(RawContacts.CONTENT_URI, null, null);
                            //r1 = mContentResolver.delete(RawContacts.CONTENT_URI, RawContacts._ID + " = ?",
                            //        new String[] { rawId } );
                            //r2 = mContentResolver.delete(ContactsContract.Data.CONTENT_URI, null, null);
                            //r2 = mContentResolver.delete(ContactsContract.Data.CONTENT_URI, ContactsContract.Data.RAW_CONTACT_ID + " = ?",
                            //        new String[] { rawId } );
                            Log.i(TAG, "raw count = " + r1 + ", contact count = " + r2);
                            addContact(account, displayName, number, exists.getLong(0));
                            */
                        }
                        exists.close();
                        break;
                    }
            }
            phones.close();
        }
        cursor.close();

        try {
            // build the xml data for the lookup request
            final String xmlData = buildXMLData(lookupNumbers.values());
            final List<StatusResponse> res = client.request("lookup", null, xmlData.getBytes());

            // get the first status - it will contain our <u> tags
            final StatusResponse status = res.get(0);
            if (status.code == StatusResponse.STATUS_SUCCESS && status.extra != null) {
                Object _numbers = status.extra.get("u");
                if (_numbers instanceof List<?>) {
                    final List<String> numbers = (List<String>) _numbers;
                    for (String hash : numbers) {
                        final String[] data = lookupNumbers.get(hash);
                        addContact(account, data[0], data[1], -1);
                    }
                }
                else {
                    final String[] data = lookupNumbers.get((String) _numbers);
                    addContact(account, data[0], data[1], -1);
                }
            }
        }
        catch (Exception e) {
            Log.e(TAG, "error in user lookup", e);
        }
    }

    /** Builds the XML data for a lookup request. */
    private String buildXMLData(Collection<String[]> lookupNumbers) throws Exception {
        String xmlContent = null;
        // build xml in a proper manner
        XmlSerializer xml = Xml.newSerializer();
        StringWriter xmlString = new StringWriter();

        xml.setOutput(xmlString);
        xml.startDocument("UTF-8", Boolean.TRUE);
        xml
            .startTag(null, "body");

        for (String[] data : lookupNumbers) {
            xml
                .startTag(null, "u")
                .text(MessageUtils.sha1(data[1]))
                .endTag(null, "u");
        }

        xml
            .endTag(null, "body")
            .endDocument();

        xmlContent = xmlString.toString();
        xmlString.close();

        return xmlContent;
    }

    private int deleteContact(long rawContactId) {
        Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId).buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
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

    private void addContact(Account account, String username, String phone, long rowContactId) {
        Log.i(TAG, "Adding contact username = \"" + username + "\", phone: " + phone);
        ArrayList<ContentProviderOperation> operationList = new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder;

        if (rowContactId < 0) {
            //Create our RawContact
            builder = ContentProviderOperation.newInsert(RawContacts.CONTENT_URI);
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
            builder.withValue(RAW_COLUMN_DISPLAY_NAME, username);
            builder.withValue(RAW_COLUMN_PHONE, phone);

            try {
                builder.withValue(RAW_COLUMN_USERID, MessageUtils.sha1(phone));
            }
            catch (Exception e) {
                Log.e(TAG, "sha1 digest failed", e);
            }

            operationList.add(builder.build());
        }

        //Create a Data record of common type 'StructuredName' for our RawContact
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        if (rowContactId < 0)
            builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
        else
            builder.withValue(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, rowContactId);

        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, username);
        operationList.add(builder.build());

        //Create a Data record of custom type 'org.nuntius.user' to display a link to our profile
        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        if (rowContactId < 0)
            builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
        else
            builder.withValue(ContactsContract.Data.RAW_CONTACT_ID, rowContactId);

        builder.withValue(ContactsContract.Data.MIMETYPE, Users.CONTENT_ITEM_TYPE);
        builder.withValue(DATA_COLUMN_DISPLAY_NAME, username);
        builder.withValue(DATA_COLUMN_ACCOUNT_NAME, mContext.getString(R.string.app_name));
        builder.withValue(DATA_COLUMN_PHONE, phone);
        operationList.add(builder.build());
        try {
            mContentResolver.applyBatch(ContactsContract.AUTHORITY, operationList);
        } catch (Exception e) {
            Log.e(TAG, "Something went wrong during creation!", e);
        }
    }

}

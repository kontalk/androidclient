/*
 * Kontalk Android client
 * Copyright (C) 2015 Kontalk Devteam <devteam@kontalk.org>

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

package org.kontalk.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.jivesoftware.smack.util.StringUtils;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPException;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.NumberValidator.NumberValidatorListener;
import org.kontalk.crypto.PGPUidMismatchException;
import org.kontalk.crypto.PGPUserID;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyImporter;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.service.KeyPairGeneratorService.KeyGeneratorReceiver;
import org.kontalk.service.KeyPairGeneratorService.PersonalKeyRunnable;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.adapter.CountryCodesAdapter;
import org.kontalk.ui.adapter.CountryCodesAdapter.CountryCode;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;


/** Number validation activity. */
public class NumberValidation extends AccountAuthenticatorActionBarActivity
        implements NumberValidatorListener {
    static final String TAG = NumberValidation.class.getSimpleName();

    public static final int REQUEST_MANUAL_VALIDATION = 771;
    public static final int REQUEST_VALIDATION_CODE = 772;

    public static final int RESULT_FALLBACK = RESULT_FIRST_USER+1;

    public static final String PARAM_FROM_INTERNAL = "org.kontalk.internal";

    public static final String PARAM_PUBLICKEY = "org.kontalk.publickey";
    public static final String PARAM_PRIVATEKEY = "org.kontalk.privatekey";
    public static final String PARAM_SERVER_URI = "org.kontalk.server";

    private AccountManager mAccountManager;
    private EditText mNameText;
    private Spinner mCountryCode;
    private EditText mPhone;
    private Button mValidateButton;
    private ProgressDialog mProgress;
    private CharSequence mProgressMessage;
    private NumberValidator mValidator;
    private Handler mHandler;

    private String mPhoneNumber;
    private String mName;

    private PersonalKey mKey;
    private String mPassphrase;
    private byte[] mImportedPublicKey;
    private byte[] mImportedPrivateKey;
    private boolean mForce;

    private LocalBroadcastManager lbm;

    /** Will be true when resuming for a fallback registration. */
    private boolean mClearState;
    private boolean mFromInternal;
    /** Runnable for delaying initial manual sync starter. */
    private Runnable mSyncStart;
    private boolean mSyncing;

    private KeyGeneratorReceiver mKeyReceiver;

    private static final class RetainData {
        NumberValidator validator;
        /** @deprecated Use saved instance state. */
        @Deprecated
        CharSequence progressMessage;
        /** @deprecated Use saved instance state. */
        @Deprecated
        boolean syncing;
    }

    /**
     * Compatibility method for {@link PhoneNumberUtil#getSupportedRegions()}.
     * This was introduced because crappy Honeycomb has an old version of
     * libphonenumber, therefore Dalvik will insist on we using it.
     * In case getSupportedRegions doesn't exist, getSupportedCountries will be
     * used.
     */
    @SuppressWarnings("unchecked")
    private Set<String> getSupportedRegions(PhoneNumberUtil util) {
        try {
            return (Set<String>) util.getClass()
                .getMethod("getSupportedRegions")
                .invoke(util);
        }
        catch (NoSuchMethodException e) {
            try {
                return (Set<String>) util.getClass()
                    .getMethod("getSupportedCountries")
                    .invoke(util);
            }
            catch (Exception helpme) {
                // ignored
            }
        }
        catch (Exception e) {
            // ignored
        }

        return new HashSet<String>();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.number_validation);

        mAccountManager = AccountManager.get(this);
        mHandler = new Handler();

        lbm = LocalBroadcastManager.getInstance(getApplicationContext());

        final Intent intent = getIntent();
        mFromInternal = intent.getBooleanExtra(PARAM_FROM_INTERNAL, false);

        mNameText = (EditText) findViewById(R.id.name);
        mCountryCode = (Spinner) findViewById(R.id.phone_cc);
        mPhone = (EditText) findViewById(R.id.phone_number);
        mValidateButton = (Button) findViewById(R.id.button_validate);

        // populate country codes
        final CountryCodesAdapter ccList = new CountryCodesAdapter(this, R.layout.country_item, R.layout.country_dropdown_item);
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        Set<String> ccSet = getSupportedRegions(util);
        for (String cc : ccSet)
            ccList.add(cc);

        ccList.sort(new Comparator<CountryCodesAdapter.CountryCode>() {
            public int compare(CountryCodesAdapter.CountryCode lhs, CountryCodesAdapter.CountryCode rhs) {
                return lhs.regionName.compareTo(rhs.regionName);
            }
        });
        mCountryCode.setAdapter(ccList);
        mCountryCode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ccList.setSelected(position);
            }

            public void onNothingSelected(AdapterView<?> parent) {
                // TODO Auto-generated method stub
            }
        });

        // FIXME this doesn't consider creation because of configuration change
        PhoneNumber myNum = NumberValidator.getMyNumber(this);
        if (myNum != null) {
            CountryCode cc = new CountryCode();
            cc.regionCode = util.getRegionCodeForNumber(myNum);
            if (cc.regionCode == null)
                cc.regionCode = util.getRegionCodeForCountryCode(myNum.getCountryCode());
            mCountryCode.setSelection(ccList.getPositionForId(cc));
            mPhone.setText(String.valueOf(myNum.getNationalNumber()));
        }
        else {
            final TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            final String regionCode = tm.getSimCountryIso().toUpperCase(Locale.US);
            CountryCode cc = new CountryCode();
            cc.regionCode = regionCode;
            cc.countryCode = util.getCountryCodeForRegion(regionCode);
            mCountryCode.setSelection(ccList.getPositionForId(cc));
        }

        // listener for autoselecting country code from typed phone number
        mPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // unused
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // unused
            }

            @Override
            public void afterTextChanged(Editable s) {
                syncCountryCodeSelector();
            }
        });

        // configuration change??
        RetainData data = (RetainData) getLastCustomNonConfigurationInstance();
        if (data != null) {
            synchronized (this) {
                // sync starter was queued, we can exit
                if (data.syncing) {
                    delayedSync();
                }

                mValidator = data.validator;
                if (mValidator != null)
                    mValidator.setListener(this);
            }
            if (data.progressMessage != null) {
                setProgressMessage(data.progressMessage, true);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("name", mName);
        state.putString("phoneNumber", mPhoneNumber);
        state.putParcelable("key", mKey);
        state.putString("passphrase", mPassphrase);
        state.putByteArray("importedPrivateKey", mImportedPrivateKey);
        state.putByteArray("importedPublicKey", mImportedPublicKey);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mName = savedInstanceState.getString("name");
        mPhoneNumber = savedInstanceState.getString("phoneNumber");
        mKey = savedInstanceState.getParcelable("key");
        mPassphrase = savedInstanceState.getString("passphrase");
        mImportedPublicKey = savedInstanceState.getByteArray("importedPublicKey");
        mImportedPrivateKey = savedInstanceState.getByteArray("importedPrivateKey");
    }

    /** Returning the validator thread. */
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        RetainData data = new RetainData();
        data.validator = mValidator;
        if (mProgress != null) data.progressMessage = mProgressMessage;
        data.syncing = mSyncing;
        return data;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.number_validation_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_settings: {
                Intent intent = new Intent(this, BootstrapPreferences.class);
                startActivityIfNeeded(intent, -1);
                break;
            }
            case R.id.menu_import_key: {
                importKey();
                break;
            }
            case R.id.menu_manual_verification: {
                validateCode();
                break;
            }
            default:
                return true;
        }
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();

        Preferences.RegistrationProgress saved = null;
        if (mClearState) {
            Preferences.clearRegistrationProgress(this);
            mClearState = false;
        }
        else {
            try {
                saved = Preferences.getRegistrationProgress(this);
            }
            catch (Exception e) {
                Log.w(TAG, "unable to restore registration progress");
                Preferences.clearRegistrationProgress(this);
            }
        }
        if (saved != null) {
            mName = saved.name;
            mPhoneNumber = saved.phone;
            mKey = saved.key;
            mPassphrase = saved.passphrase;
            mImportedPublicKey = saved.importedPublicKey;
            mImportedPrivateKey = saved.importedPrivateKey;

            // update UI
            mNameText.setText(mName);
            mPhone.setText(mPhoneNumber);
            syncCountryCodeSelector();

            startValidationCode(REQUEST_MANUAL_VALIDATION, saved.sender, saved.server, false);
        }

        if (mKey == null) {
            PersonalKeyRunnable action = new PersonalKeyRunnable() {
                public void run(PersonalKey key) {
                    if (key != null) {
                        mKey = key;
                        if (mValidator != null)
                            // this will release the waiting lock
                            mValidator.setKey(mKey);
                    }

                    // no key, key pair generation started
                    else if (BuildConfig.DEBUG) {
                        Toast.makeText(NumberValidation.this,
                            R.string.msg_generating_keypair,
                            Toast.LENGTH_LONG).show();
                    }
                }
            };

            // random passphrase (40 characters!!!!)
            mPassphrase = StringUtils.randomString(40);

            mKeyReceiver = new KeyGeneratorReceiver(mHandler, action);

            IntentFilter filter = new IntentFilter(KeyPairGeneratorService.ACTION_GENERATE);
            filter.addAction(KeyPairGeneratorService.ACTION_STARTED);
            lbm.registerReceiver(mKeyReceiver, filter);

            Intent i = new Intent(this, KeyPairGeneratorService.class);
            i.setAction(KeyPairGeneratorService.ACTION_GENERATE);
            startService(i);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        keepScreenOn(false);

        stopKeyReceiver();

        if (mProgress != null) {
            if (isFinishing())
                mProgress.cancel();
            else
                mProgress.dismiss();
        }
    }

    private void stopKeyReceiver() {
        if (mKeyReceiver != null)
            lbm.unregisterReceiver(mKeyReceiver);
    }

    @Override
    protected void onUserLeaveHint() {
        keepScreenOn(false);
        if (mProgress != null)
            mProgress.cancel();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MANUAL_VALIDATION) {
            if (resultCode == RESULT_OK) {
                finishLogin(data.getStringExtra(PARAM_SERVER_URI),
                    data.getByteArrayExtra(PARAM_PRIVATEKEY),
                    data.getByteArrayExtra(PARAM_PUBLICKEY),
                    true);
            }
            else if (resultCode == RESULT_FALLBACK) {
                mClearState = true;
                startValidation(data.getBooleanExtra("force", false), true);
            }
        }
    }

    private void keepScreenOn(boolean active) {
        if (active)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Starts the validation activity. */
    public static void start(Context context) {
        Intent i = new Intent(context, NumberValidation.class);
        i.putExtra(PARAM_FROM_INTERNAL, true);
        context.startActivity(i);
    }

    /** Sync country code with text entered by the user, if possible. */
    private void syncCountryCodeSelector() {
        try {
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            CountryCode cc = (CountryCode) mCountryCode.getSelectedItem();
            PhoneNumber phone = util.parse(mPhone.getText().toString(), cc != null ? cc.regionCode : null);
            // autoselect correct country if user entered country code too
            if (phone.hasCountryCode()) {
                CountryCode ccLookup = new CountryCode();
                ccLookup.regionCode = util.getRegionCodeForNumber(phone);
                ccLookup.countryCode = phone.getCountryCode();
                int position = ((CountryCodesAdapter) mCountryCode.getAdapter()).getPositionForId(ccLookup);
                if (position >= 0) {
                    mCountryCode.setSelection(position);
                }
            }
        }
        catch (NumberParseException e) {
            // ignored
        }
    }

    private void enableControls(boolean enabled) {
        mValidateButton.setEnabled(enabled);
        mCountryCode.setEnabled(enabled);
        mPhone.setEnabled(enabled);
    }

    private void error(int title, int message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNeutralButton(android.R.string.ok, null)
            .show();
    }

    private boolean checkInput(boolean importing) {
        mPhoneNumber = null;
        String phoneStr = null;

        // check name first
        if (!importing) {
            mName = mNameText.getText().toString().trim();
            if (mName.length() == 0) {
                error(R.string.title_no_name, R.string.msg_no_name);
                return false;
            }
        }
        else {
            // we will use the one in the imported key
            mName = null;
        }

        PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        CountryCode cc = (CountryCode) mCountryCode.getSelectedItem();
        if (!BuildConfig.DEBUG) {
            PhoneNumber phone;
            try {
                phone = util.parse(mPhone.getText().toString(), cc.regionCode);
                // autoselect correct country if user entered country code too
                if (phone.hasCountryCode()) {
                    CountryCode ccLookup = new CountryCode();
                    ccLookup.regionCode = util.getRegionCodeForNumber(phone);
                    ccLookup.countryCode = phone.getCountryCode();
                    int position = ((CountryCodesAdapter) mCountryCode.getAdapter()).getPositionForId(ccLookup);
                    if (position >= 0) {
                        mCountryCode.setSelection(position);
                        cc = (CountryCode) mCountryCode.getItemAtPosition(position);
                    }
                }
                if (!util.isValidNumberForRegion(phone, cc.regionCode)) {
                    throw new NumberParseException(ErrorType.INVALID_COUNTRY_CODE, "invalid number for region " + cc.regionCode);
                }
            }
            catch (NumberParseException e1) {
                error(R.string.title_invalid_number, R.string.msg_invalid_number);
                return false;
            }

            // check phone number format
            if (phone != null) {
                phoneStr = util.format(phone, PhoneNumberFormat.E164);
                if (!PhoneNumberUtils.isWellFormedSmsAddress(phoneStr)) {
                    Log.i(TAG, "not a well formed SMS address");
                }
            }
        }
        else {
            phoneStr = String.format(Locale.US, "+%d%s", cc.countryCode, mPhone.getText().toString());
        }

        // phone is null - invalid number
        if (phoneStr == null) {
            Toast.makeText(this, R.string.warn_invalid_number, Toast.LENGTH_SHORT)
                .show();
            return false;
        }

        Log.v(TAG, "Using phone number to register: " + phoneStr);
        mPhoneNumber = phoneStr;
        return true;
    }

    private void startValidation(boolean force, boolean fallback) {
        mForce = force;
        enableControls(false);

        if (!checkInput(false) || !startValidationNormal(null, force, fallback, false)) {
            enableControls(true);
        }
    }

    private boolean startValidationNormal(String manualServer, boolean force, boolean fallback, boolean testImport) {
        if (!SystemUtils.isNetworkConnectionAvailable(this)) {
            error(R.string.title_nonetwork, R.string.err_validation_nonetwork);
            return false;
        }

        // start async request
        Log.d(TAG, "phone number checked, sending validation request");
        startProgress(testImport ? getText(R.string.msg_importing_key) : null);

        EndpointServer.EndpointServerProvider provider;
        if (manualServer != null) {
            provider = new EndpointServer.SingleServerProvider(manualServer);
        }
        else {
            provider = Preferences.getEndpointServerProvider(this);
        }

        boolean imported = (mImportedPrivateKey != null && mImportedPublicKey != null);

        mValidator = new NumberValidator(this, provider, mName, mPhoneNumber,
            imported ? null : mKey, mPassphrase);
        mValidator.setListener(this);
        mValidator.setForce(force);
        mValidator.setFallback(fallback);
        if (imported)
            mValidator.importKey(mImportedPrivateKey, mImportedPublicKey);

        if (testImport)
            mValidator.testImport();

        mValidator.start();
        return true;
    }

    /**
     * Begins validation of the phone number.
     * Also used by the view definition as the {@link OnClickListener}.
     * @param v not used
     */
    public void validatePhone(View v) {
        keepScreenOn(true);
        startValidation(false, false);
    }

    /** Opens manual validation window immediately. */
    public void validateCode() {
        if (checkInput(false))
            startValidationCode(REQUEST_VALIDATION_CODE, null);
    }

    /** Opens import keys from another device wizard. */
    private void importKey() {
        if (checkInput(true)) {
            // import keys -- number verification with server is still needed
            // though because of key rollback protection
            // TODO allow for manual validation too
            // TODO we should verify the number against the user ID

            new AlertDialog.Builder(this)
                .setTitle(R.string.pref_import_keypair)
                .setMessage(getString(R.string.msg_import_keypair, PersonalKeyImporter.KEYPACK_FILENAME))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do not wait for the generated key
                        stopKeyReceiver();

                        ZipInputStream zip = null;
                        try {
                            zip = new ZipInputStream(new FileInputStream
                                (PersonalKeyImporter.DEFAULT_KEYPACK));

                            // ask passphrase to user and assign to mPassphrase
                            importAskPassphrase(zip);
                        }
                        catch (Exception e) {
                            Log.e(TAG, "error importing keys", e);
                            mImportedPublicKey = mImportedPrivateKey = null;

                            try {
                                if (zip != null)
                                    zip.close();
                            }
                            catch (IOException ignored) {
                                // ignored.
                            }

                            Toast.makeText(NumberValidation.this,
                                R.string.err_import_keypair_failed,
                                Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
        }
    }

    private void importAskPassphrase(final ZipInputStream zip) {
        new InputDialog.Builder(this,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .setTitle(R.string.title_passphrase)
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        zip.close();
                    }
                    catch (IOException e) {
                        // ignored
                    }
                }
            })
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    startImport(zip, InputDialog.getInputText
                        ((Dialog) dialog).toString());
                }
            })
            .show();
    }

    private void startImport(ZipInputStream zip, String passphrase) {
        PersonalKeyImporter importer = null;
        String manualServer = Preferences.getServerURI(this);

        try {
            importer = new PersonalKeyImporter(zip, passphrase);
            importer.load();

            // we do not save this test key into the mKey field
            // we need it to be clear so the validator will use the imported data
            // createPersonalKey is called only to make sure data is valid
            PersonalKey key = importer.createPersonalKey();
            if (key == null)
                throw new PGPException("unable to load imported personal key.");

            String uidStr = key.getUserId(null);
            PGPUserID uid = PGPUserID.parse(uidStr);
            if (uid == null)
                throw new PGPException("malformed user ID: " + uidStr);

            // check that uid matches phone number
            String email = uid.getEmail();
            String numberHash = MessageUtils.sha1(mPhoneNumber);
            String localpart = XmppStringUtils.parseLocalpart(email);
            if (!numberHash.equalsIgnoreCase(localpart))
                throw new PGPUidMismatchException("email does not match phone number: " + email);

            // use server from the key only if we didn't set our own
            if (TextUtils.isEmpty(manualServer))
                manualServer = XmppStringUtils.parseDomain(email);

            mName = uid.getName();
            mImportedPublicKey = importer.getPublicKeyData();
            mImportedPrivateKey = importer.getPrivateKeyData();
        }

        catch (PGPUidMismatchException e) {
            Log.w(TAG, "uid mismatch!");
            mImportedPublicKey = mImportedPrivateKey = null;
            mName = null;

            Toast.makeText(this,
                R.string.err_import_keypair_uid_mismatch,
                Toast.LENGTH_LONG).show();
        }

        catch (Exception e) {
            Log.e(TAG, "error importing keys", e);
            mImportedPublicKey = mImportedPrivateKey = null;
            mName = null;

            Toast.makeText(this,
                R.string.err_import_keypair_failed,
                Toast.LENGTH_LONG).show();
        }

        finally {
            try {
                if (importer != null)
                    importer.close();
            }
            catch (Exception e) {
                // ignored
            }
        }

        if (mImportedPublicKey != null && mImportedPrivateKey != null) {
            // we can now store the passphrase
            mPassphrase = passphrase;

            // begin usual validation
            // TODO implement fallback usage
            if (!startValidationNormal(manualServer, true, false, true)) {
                enableControls(true);
            }
        }
    }

    /** No search here. */
    @Override
    public boolean onSearchRequested() {
        return false;
    }

    public void startProgress() {
        startProgress(null);
    }

    private void startProgress(CharSequence message) {
        if (mProgress == null) {
            mProgress = new NonSearchableProgressDialog(this);
            mProgress.setIndeterminate(true);
            mProgress.setCanceledOnTouchOutside(false);
            setProgressMessage(message != null ? message : getText(R.string.msg_validating_phone));
            mProgress.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    keepScreenOn(false);
                    Toast.makeText(NumberValidation.this, R.string.msg_validation_canceled, Toast.LENGTH_LONG).show();
                    abort();
                }
            });
            mProgress.setOnDismissListener(new OnDismissListener() {
                public void onDismiss(DialogInterface dialog) {
                    // remove sync starter
                    if (mSyncStart != null) {
                        mHandler.removeCallbacks(mSyncStart);
                        mSyncStart = null;
                    }
                }
            });
        }
        mProgress.show();
    }

    public void abortProgress() {
        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }
    }

    public void abortProgress(boolean enableControls) {
        abortProgress();
        enableControls(enableControls);
    }

    public void abort() {
        abort(false);
    }

    public void abort(boolean ending) {
        if (!ending) {
            abortProgress(true);
        }

        mForce = false;
        if (mValidator != null) {
            mValidator.shutdown();
            mValidator = null;
        }
    }

    private void setProgressMessage(CharSequence message) {
        setProgressMessage(message, false);
    }

    private void setProgressMessage(CharSequence message, boolean create) {
        if (mProgress == null && create) {
            startProgress(message);
        }

        if (mProgress != null) {
            mProgressMessage = message;
            mProgress.setMessage(message);
        }
    }

    private void delayedSync() {
        mSyncing = true;
        mSyncStart = new Runnable() {
            public void run() {
                // start has been requested
                mSyncStart = null;

                // enable services
                Kontalk.setServicesEnabled(NumberValidation.this, true);

                // start sync
                SyncAdapter.requestSync(NumberValidation.this, true);

                // if we have been called internally, start ConversationList
                if (mFromInternal)
                    startActivity(new Intent(getApplicationContext(), ConversationsActivity.class));

                Toast.makeText(getApplicationContext(), R.string.msg_authenticated, Toast.LENGTH_LONG).show();

                // end this
                abortProgress();
                finish();
            }
        };

        /*
         * This is a workaround for API level... I don't know since when :D
         * Seems that requesting sync too soon after account creation has no
         * effect. We delay sync by some time to give it time to settle.
         */
        mHandler.postDelayed(mSyncStart, 2000);
    }

    /** Used only if imported key was tested successfully. */
    @Override
    public void onAuthTokenReceived(final NumberValidator v, final byte[] privateKey, final byte[] publicKey) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abort(true);
                finishLogin(v.getServer().toString(), privateKey, publicKey, false);
            }
        });
    }

    @Override
    public void onAuthTokenFailed(NumberValidator v, int reason) {
        Log.e(TAG, "authentication token request failed (" + reason + ")");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                keepScreenOn(false);
                Toast.makeText(NumberValidation.this,
                    R.string.err_authentication_failed,
                    Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

    private void statusInitializing() {
        if (mProgress == null)
            startProgress();
        mProgress.setCancelable(false);
        setProgressMessage(getString(R.string.msg_initializing));
    }

    protected void finishLogin(final String serverUri, final byte[] privateKeyData, final byte[] publicKeyData, boolean updateKey) {
        Log.v(TAG, "finishing login");
        statusInitializing();

        if (updateKey) {
            // update public key
            try {
                mKey.update(publicKeyData);
            }
            catch (IOException e) {
                // abort
                throw new RuntimeException("error decoding public key", e);
            }
        }

        completeLogin(serverUri, privateKeyData, publicKeyData);
    }

    private void completeLogin(String serverUri, byte[] privateKeyData, byte[] publicKeyData) {
        // generate the bridge certificate
        byte[] bridgeCertData;
        try {
            // TODO subjectAltName?
            bridgeCertData = X509Bridge.createCertificate(privateKeyData, publicKeyData, mPassphrase, null).getEncoded();
        }
        catch (Exception e) {
            // abort
            throw new RuntimeException("unable to build X.509 bridge certificate", e);
        }

        final Account account = new Account(mPhoneNumber, Authenticator.ACCOUNT_TYPE);

        // workaround for bug in AccountManager (http://stackoverflow.com/a/11698139/1045199)
        // procedure will continue in removeAccount callback
        mAccountManager.removeAccount(account,
            new AccountRemovalCallback(this, account, mPassphrase,
                privateKeyData, publicKeyData, bridgeCertData, mName, serverUri),
            mHandler);
    }

    @Override
    public void onError(NumberValidator v, final Throwable e) {
        Log.e(TAG, "validation error.", e);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                keepScreenOn(false);
                int msgId;
                if (e instanceof SocketException)
                    msgId = R.string.err_validation_network_error;
                else
                    msgId = R.string.err_validation_error;
                Toast.makeText(NumberValidation.this, msgId, Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

    @Override
    public void onServerCheckFailed(NumberValidator v) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(NumberValidation.this, R.string.err_validation_server_not_supported, Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

    @Override
    public void onValidationFailed(NumberValidator v, final int reason) {
        Log.e(TAG, "phone number validation failed (" + reason + ")");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (reason == NumberValidator.ERROR_USER_EXISTS) {
                    userExistsWarning();
                }
                else {
                    int msg;
                    if (reason == NumberValidator.ERROR_THROTTLING)
                        msg = R.string.err_validation_retry_later;
                    else
                        msg = R.string.err_validation_failed;

                    Toast.makeText(NumberValidation.this, msg, Toast.LENGTH_LONG).show();
                }
                abort();
            }
        });
    }

    @Override
    public void onValidationRequested(NumberValidator v, String sender) {
        Log.d(TAG, "validation has been requested, requesting validation code to user");
        proceedManual(sender);
    }

    private void userExistsWarning() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.title_user_exists)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.err_validation_user_exists)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startValidation(true, false);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Proceeds to the next step in manual validation. */
    private void proceedManual(final String sender) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abortProgress(true);
                startValidationCode(REQUEST_MANUAL_VALIDATION, sender);
            }
        });
    }

    private void startValidationCode(int requestCode, String sender) {
        startValidationCode(requestCode, sender, null, true);
    }

    private void startValidationCode(int requestCode, String sender, EndpointServer server, boolean saveProgress) {
        // validator might be null if we are skipping verification code request
        String serverUri = null;
        if (server != null)
            serverUri = server.toString();
        else if (mValidator != null)
            serverUri = mValidator.getServer().toString();

        // save state to preferences
        if (saveProgress) {
            Preferences.saveRegistrationProgress(this,
                mName, mPhoneNumber, mKey, mPassphrase,
                mImportedPublicKey, mImportedPrivateKey,
                serverUri, sender, mForce);
        }

        Intent i = new Intent(NumberValidation.this, CodeValidation.class);
        i.putExtra("requestCode", requestCode);
        i.putExtra("name", mName);
        i.putExtra("phone", mPhoneNumber);
        i.putExtra("force", mForce);
        i.putExtra("passphrase", mPassphrase);
        i.putExtra("importedPublicKey", mImportedPublicKey);
        i.putExtra("importedPrivateKey", mImportedPrivateKey);
        i.putExtra("server", serverUri);
        i.putExtra("sender", sender);
        i.putExtra(KeyPairGeneratorService.EXTRA_KEY, mKey);

        startActivityForResult(i, REQUEST_MANUAL_VALIDATION);
    }

    private static class AccountRemovalCallback implements AccountManagerCallback<Boolean> {
        private WeakReference<NumberValidation> a;
        private final Account account;
        private final String passphrase;
        private final byte[] privateKeyData;
        private final byte[] publicKeyData;
        private final byte[] bridgeCertData;
        private final String name;
        private final String serverUri;

        public AccountRemovalCallback(NumberValidation activity, Account account,
                String passphrase, byte[] privateKeyData, byte[] publicKeyData,
                byte[] bridgeCertData, String name, String serverUri) {
            this.a = new WeakReference<NumberValidation>(activity);
            this.account = account;
            this.passphrase = passphrase;
            this.privateKeyData = privateKeyData;
            this.publicKeyData = publicKeyData;
            this.bridgeCertData = bridgeCertData;
            this.name = name;
            this.serverUri = serverUri;
        }

        @Override
        public void run(AccountManagerFuture<Boolean> result) {
            NumberValidation ctx = a.get();
            if (ctx != null) {
                AccountManager am = (AccountManager) ctx
                    .getSystemService(Context.ACCOUNT_SERVICE);

                // account userdata
                Bundle data = new Bundle();
                data.putString(Authenticator.DATA_PRIVATEKEY, Base64.encodeToString(privateKeyData, Base64.NO_WRAP));
                data.putString(Authenticator.DATA_PUBLICKEY, Base64.encodeToString(publicKeyData, Base64.NO_WRAP));
                data.putString(Authenticator.DATA_BRIDGECERT, Base64.encodeToString(bridgeCertData, Base64.NO_WRAP));
                data.putString(Authenticator.DATA_NAME, name);
                data.putString(Authenticator.DATA_SERVER_URI, serverUri);

                // this is the password to the private key
                am.addAccountExplicitly(account, passphrase, data);

                // put data once more (workaround for Android bug http://stackoverflow.com/a/11698139/1045199)
                am.setUserData(account, Authenticator.DATA_PRIVATEKEY, data.getString(Authenticator.DATA_PRIVATEKEY));
                am.setUserData(account, Authenticator.DATA_PUBLICKEY, data.getString(Authenticator.DATA_PUBLICKEY));
                am.setUserData(account, Authenticator.DATA_BRIDGECERT, data.getString(Authenticator.DATA_BRIDGECERT));
                am.setUserData(account, Authenticator.DATA_NAME, data.getString(Authenticator.DATA_NAME));
                am.setUserData(account, Authenticator.DATA_SERVER_URI, serverUri);

                // Set contacts sync for this account.
                ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
                ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);

                // send back result
                final Intent intent = new Intent();
                intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
                intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Authenticator.ACCOUNT_TYPE);

                ctx.setAccountAuthenticatorResult(intent.getExtras());
                ctx.setResult(RESULT_OK, intent);

                // manual sync starter
                ctx.delayedSync();
            }
        }

    }
}

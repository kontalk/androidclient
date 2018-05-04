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

package org.kontalk.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.SocketException;
import java.security.cert.X509Certificate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.folderselector.FileChooserDialog;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.jivesoftware.smack.util.StringUtils;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.DisplayMetrics;
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
import android.widget.TextView;
import android.widget.Toast;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import org.kontalk.BuildConfig;
import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.NumberValidator.NumberValidatorListener;
import org.kontalk.crypto.PGPUidMismatchException;
import org.kontalk.crypto.PGPUserID;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyImporter;
import org.kontalk.crypto.PersonalKeyPack;
import org.kontalk.crypto.X509Bridge;
import org.kontalk.provider.Keyring;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.service.KeyPairGeneratorService.KeyGeneratorReceiver;
import org.kontalk.service.KeyPairGeneratorService.PersonalKeyRunnable;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.adapter.CountryCodesAdapter;
import org.kontalk.ui.adapter.CountryCodesAdapter.CountryCode;
import org.kontalk.ui.prefs.PreferencesActivity;
import org.kontalk.util.MessageUtils;
import org.kontalk.util.ParameterRunnable;
import org.kontalk.util.Permissions;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;
import org.kontalk.util.XMPPUtils;


/** Number validation activity. */
public class NumberValidation extends AccountAuthenticatorActionBarActivity
        implements NumberValidatorListener, FileChooserDialog.FileCallback {
    static final String TAG = NumberValidation.class.getSimpleName();

    static final int REQUEST_MANUAL_VALIDATION = 771;
    static final int REQUEST_VALIDATION_CODE = 772;
    static final int REQUEST_SCAN_TOKEN = 773;
    static final int REQUEST_ASK_TOKEN = 774;

    public static final int RESULT_FALLBACK = RESULT_FIRST_USER + 1;

    public static final String PARAM_FROM_INTERNAL = "org.kontalk.internal";

    public static final String PARAM_PUBLICKEY = "org.kontalk.publickey";
    public static final String PARAM_PRIVATEKEY = "org.kontalk.privatekey";
    public static final String PARAM_SERVER_URI = "org.kontalk.server";
    public static final String PARAM_CHALLENGE = "org.kontalk.challenge";
    public static final String PARAM_TRUSTED_KEYS = "org.kontalk.trustedkeys";

    private AccountManager mAccountManager;
    private EditText mNameText;
    private Spinner mCountryCode;
    private EditText mPhone;
    private Button mValidateButton;
    private MaterialDialog mProgress;
    private CharSequence mProgressMessage;
    NumberValidator mValidator;
    Handler mHandler;

    private String mPhoneNumber;
    private String mName;

    PersonalKey mKey;
    private String mPassphrase;
    private byte[] mImportedPublicKey;
    private byte[] mImportedPrivateKey;
    Map<String, Keyring.TrustedFingerprint> mTrustedKeys;
    private boolean mForce;

    private LocalBroadcastManager lbm;

    /** Will be true when resuming for a fallback registration. */
    private boolean mClearState;
    boolean mFromInternal;
    /** Runnable for delaying initial manual sync starter. */
    Runnable mSyncStart;
    private boolean mSyncing;

    private boolean mPermissionsAsked;

    private KeyGeneratorReceiver mKeyReceiver;

    private static final class RetainData {
        NumberValidator validator;
        /** @deprecated Use saved instance state. */
        @Deprecated
        CharSequence progressMessage;
        /** @deprecated Use saved instance state. */
        @Deprecated
        boolean syncing;

        RetainData() {
        }
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

        return new HashSet<>();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.number_validation);
        setupToolbar(false, false);

        mAccountManager = AccountManager.get(this);
        mHandler = new Handler();

        lbm = LocalBroadcastManager.getInstance(getApplicationContext());

        final Intent intent = getIntent();
        mFromInternal = intent.getBooleanExtra(PARAM_FROM_INTERNAL, false);

        mNameText = findViewById(R.id.name);
        mCountryCode = findViewById(R.id.phone_cc);
        mPhone = findViewById(R.id.phone_number);
        mValidateButton = findViewById(R.id.button_validate);

        // populate country codes
        final CountryCodesAdapter ccList = new CountryCodesAdapter(this,
                android.R.layout.simple_list_item_1,
                android.R.layout.simple_spinner_dropdown_item);
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
            String country = tm.getSimCountryIso();
            if (country != null) {
                final String regionCode = country.toUpperCase(Locale.US);
                CountryCode cc = new CountryCode();
                cc.regionCode = regionCode;
                cc.countryCode = util.getCountryCodeForRegion(regionCode);
                mCountryCode.setSelection(ccList.getPositionForId(cc));
            }
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

    /** Not used. */
    @Override
    protected boolean isNormalUpNavigation() {
        return false;
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
        state.putBoolean("permissionsAsked", mPermissionsAsked);
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
        mPermissionsAsked = savedInstanceState.getBoolean("permissionsAsked");
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
        menu.findItem(R.id.menu_manual_verification).setVisible(BuildConfig.DEBUG);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_settings: {
                PreferencesActivity.start(this);
                break;
            }
            case R.id.menu_import_key: {
                importKey();
                break;
            }
            case R.id.menu_import_device: {
                importDevice();
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
            Preferences.clearRegistrationProgress();
            mClearState = false;
        }
        else {
            try {
                saved = Preferences.getRegistrationProgress();
            }
            catch (Exception e) {
                Log.w(TAG, "unable to restore registration progress");
                Preferences.clearRegistrationProgress();
            }
        }
        if (saved != null) {
            mName = saved.name;
            mPhoneNumber = saved.phone;
            mKey = saved.key;
            mPassphrase = saved.passphrase;
            mImportedPublicKey = saved.importedPublicKey;
            mImportedPrivateKey = saved.importedPrivateKey;
            mTrustedKeys = saved.trustedKeys;

            // update UI
            mNameText.setText(mName);
            mPhone.setText(mPhoneNumber);
            syncCountryCodeSelector();

            startValidationCode(REQUEST_MANUAL_VALIDATION, saved.sender,
                saved.brandImage, saved.brandLink, saved.challenge, saved.canFallback, saved.server, false);
        }
        else {
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

    @Override
    protected void onResume() {
        super.onResume();
        // ask for access to contacts
        askPermissions();
    }

    void stopKeyReceiver() {
        if (mKeyReceiver != null)
            lbm.unregisterReceiver(mKeyReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onUserLeaveHint() {
        keepScreenOn(false);
        if (mProgress != null)
            mProgress.cancel();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MANUAL_VALIDATION) {
            if (resultCode == RESULT_OK) {
                Map<String, Keyring.TrustedFingerprint> trustedKeys = null;
                Map<String, String> keys = (HashMap) data.getSerializableExtra(PARAM_TRUSTED_KEYS);
                if (keys != null) {
                    trustedKeys = Keyring.fromTrustedFingerprintMap(keys);
                }
                finishLogin(data.getStringExtra(PARAM_SERVER_URI),
                    data.getStringExtra(PARAM_CHALLENGE),
                    data.getByteArrayExtra(PARAM_PRIVATEKEY),
                    data.getByteArrayExtra(PARAM_PUBLICKEY),
                    true,
                    trustedKeys);
            }
            else if (resultCode == RESULT_FALLBACK) {
                mClearState = true;
                startValidation(data.getBooleanExtra("force", false), true);
            }
        }
        else if (requestCode == REQUEST_SCAN_TOKEN) {
            if (resultCode == RESULT_OK && data != null) {
                String token = data.getStringExtra("text");
                if (!TextUtils.isEmpty(token)) {
                    RegisterDeviceActivity.PrivateKeyToken privateKeyToken =
                        RegisterDeviceActivity.parseTokenText(token);

                    if (privateKeyToken != null) {
                        requestPrivateKey(privateKeyToken.account, privateKeyToken.server, privateKeyToken.token);
                        return;
                    }
                }

                new MaterialDialog.Builder(this)
                    .content(R.string.import_device_invalid_barcode)
                    .positiveText(android.R.string.ok)
                    .show();
            }
        }
        else if (requestCode == REQUEST_ASK_TOKEN) {
            if (resultCode == RESULT_OK && data != null) {
                String account = data.getStringExtra(ImportDeviceActivity.EXTRA_ACCOUNT);
                String server = data.getStringExtra(ImportDeviceActivity.EXTRA_SERVER);
                String token = data.getStringExtra(ImportDeviceActivity.EXTRA_TOKEN);
                // no need to verify the data, import device activity already checked

                requestPrivateKey(account, server, token);
            }
        }
    }

    private void askPermissions() {
        if (mPermissionsAsked)
            return;

        if (!Permissions.canWriteContacts(this)) {
            Permissions.requestContacts(this, getString(R.string.err_validation_contacts_denied));
            mPermissionsAsked = true;
        }
    }

    void keepScreenOn(boolean active) {
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
    void syncCountryCodeSelector() {
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
        mNameText.setEnabled(enabled);
        mValidateButton.setEnabled(enabled);
        mCountryCode.setEnabled(enabled);
        mPhone.setEnabled(enabled);
    }

    void error(int message) {
        new MaterialDialog.Builder(this)
            .content(message)
            .positiveText(android.R.string.ok)
            .show();
    }

    private void checkInput(boolean importing, final ParameterRunnable<Boolean> callback) {
        final String phoneStr;

        // check name first
        if (!importing) {
            mName = mNameText.getText().toString().trim();
            if (mName.length() == 0) {
                error(R.string.msg_no_name);
                callback.run(false);
                return;
            }
        }

        String phoneInput = mPhone.getText().toString();
        String phoneConfirm;
        // if the user entered a phone number use it even when importing for backward compatibility
        if (!importing || !phoneInput.isEmpty()){
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            CountryCode cc = (CountryCode) mCountryCode.getSelectedItem();
            if (cc == null) {
                error(R.string.msg_invalid_cc);
                callback.run(false);
                return;
            }

            if (!BuildConfig.DEBUG) {
                PhoneNumber phone;
                try {
                    phone = util.parse(phoneInput, cc.regionCode);
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
                    // handle special cases
                    NumberValidator.handleSpecialCases(phone);
                    if (!util.isValidNumberForRegion(phone, cc.regionCode) && !NumberValidator.isSpecialNumber(phone))
                        throw new NumberParseException(ErrorType.INVALID_COUNTRY_CODE, "invalid number for region " + cc.regionCode);
                }
                catch (NumberParseException e1) {
                    error(R.string.msg_invalid_number);
                    callback.run(false);
                    return;
                }

                // check phone number format
                phoneStr = util.format(phone, PhoneNumberFormat.E164);
                if (!PhoneNumberUtils.isWellFormedSmsAddress(phoneStr)) {
                    Log.i(TAG, "not a well formed SMS address");
                }

                // for the confirmation dialog
                phoneConfirm = util.format(phone, PhoneNumberFormat.INTERNATIONAL);
            }
            else {
                phoneStr = phoneConfirm = String.format(Locale.US, "+%d%s", cc.countryCode, mPhone.getText().toString());
            }

            // phone is null - invalid number
            if (phoneStr == null) {
                Toast.makeText(this, R.string.warn_invalid_number, Toast.LENGTH_SHORT)
                    .show();
                callback.run(false);
                return;
            }

            Log.v(TAG, "Using phone number to register: " + phoneStr);

            // confirmation dialog only if not importing
            if (!importing && !mForce) {
                MaterialDialog dialog = new MaterialDialog.Builder(this)
                    .customView(R.layout.dialog_register_confirm, false)
                    .positiveText(android.R.string.ok)
                    .positiveColorRes(R.color.button_success)
                    .negativeText(android.R.string.cancel)
                    .onAny(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            switch (which) {
                                case POSITIVE:
                                    mPhoneNumber = phoneStr;
                                    callback.run(true);
                                    break;
                                case NEGATIVE:
                                    callback.run(false);
                                    break;
                            }
                        }
                    })
                    .cancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            callback.run(false);
                        }
                    })
                    .build();

                TextView phoneTextView = (TextView) dialog.findViewById(R.id.bigtext);
                phoneTextView.setText(phoneConfirm);

                dialog.show();
            }
            else {
                callback.run(true);
            }
        }
        else {
            // we will use the data from the imported key
            mName = null;
            mPhoneNumber = null;

            callback.run(true);
        }
    }

    void startValidation(final boolean force, final boolean fallback) {
        mForce = force;
        if (!fallback) {
            mImportedPublicKey = null;
            mImportedPrivateKey = null;
        }
        enableControls(false);

        checkInput(false, new ParameterRunnable<Boolean>() {
            @Override
            public void run(Boolean result) {
                if (!result || !startValidationNormal(null, force, fallback, false)) {
                    enableControls(true);
                }
            }
        });
    }

    @NumberValidator.BrandImageSize
    private int getBrandImageSize() {
        int density = getResources().getDisplayMetrics().densityDpi;
        if (density <= DisplayMetrics.DENSITY_MEDIUM) {
            return NumberValidator.BRAND_IMAGE_SMALL;
        }
        else if (density > DisplayMetrics.DENSITY_MEDIUM && density <= DisplayMetrics.DENSITY_HIGH) {
            return NumberValidator.BRAND_IMAGE_MEDIUM;
        }
        else if (density > DisplayMetrics.DENSITY_HIGH && density <= DisplayMetrics.DENSITY_XXHIGH) {
            return NumberValidator.BRAND_IMAGE_LARGE;
        }
        else if (density > DisplayMetrics.DENSITY_XXHIGH) {
            return NumberValidator.BRAND_IMAGE_HD;
        }

        return NumberValidator.BRAND_IMAGE_MEDIUM;
    }

    private boolean startValidationNormal(String manualServer, boolean force, boolean fallback, boolean testImport) {
        if (!SystemUtils.isNetworkConnectionAvailable(this)) {
            error(R.string.err_validation_nonetwork);
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
            imported ? null : mKey, mPassphrase, getBrandImageSize());
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
        checkInput(false, new ParameterRunnable<Boolean>() {
            @Override
            public void run(Boolean result) {
                if (result)
                    startValidationCode(REQUEST_VALIDATION_CODE, null, null, null, null, false);
            }
        });
    }

    /** Opens import keys from another device wizard. */
    private void importKey() {
        checkInput(true, new ParameterRunnable<Boolean>() {
            @Override
            public void run(Boolean result) {
                if (result) {
                    // import keys -- number verification with server is still needed
                    // though because of key rollback protection
                    // TODO allow for manual validation too

                    // do not wait for the generated key
                    stopKeyReceiver();

                    if (!Permissions.canReadExternalStorage(NumberValidation.this)) {
                        // TODO rationale
                        Permissions.requestReadExternalStorage(NumberValidation.this, null);
                    }
                    else {
                        browseImportKey();
                    }
                }
            }
        });
    }

    @AfterPermissionGranted(Permissions.RC_READ_EXT_STORAGE)
    void browseImportKey() {
        new FileChooserDialog.Builder(NumberValidation.this)
            .initialPath(PersonalKeyPack.DEFAULT_KEYPACK.getParent())
            .mimeType(PersonalKeyPack.KEYPACK_MIME)
            .show(getSupportFragmentManager());
    }

    /**
     * Opens a screen for shooting a QR code or typing in a secure token from
     * another device.
     */
    void importDevice() {
        PackageManager pm = getPackageManager();
        boolean hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);

        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
            .title(R.string.menu_import_device)
            .content(R.string.import_device_message)
            .neutralText(R.string.import_device_btn_input)
            .onAny(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    switch (which) {
                        case POSITIVE:
                            scanToken();
                            break;
                        case NEUTRAL:
                            askToken();
                            break;
                    }
                }
            });

        if (hasCamera) {
            builder.positiveText(R.string.import_device_btn_scan);
        }

        builder.show();
    }

    void scanToken() {
        ScanTextActivity.start(this, getString(R.string.import_device_scan_screen_title),
            REQUEST_SCAN_TOKEN);
    }

    void askToken() {
        ImportDeviceActivity.start(this, REQUEST_ASK_TOKEN);
    }

    void requestPrivateKey(String account, String server, String token) {
        if (!SystemUtils.isNetworkConnectionAvailable(this)) {
            error(R.string.err_validation_nonetwork);
            return;
        }

        enableControls(false);
        startProgress(getString(R.string.import_device_requesting));

        EndpointServer.EndpointServerProvider provider =
            new EndpointServer.SingleServerProvider(server);
        mValidator = new NumberValidator(this, provider, account, token);
        mValidator.setListener(this);
        mValidator.requestPrivateKey();
        mValidator.start();
    }

    private void importAskPassphrase(final ZipInputStream zip) {
        new MaterialDialog.Builder(this)
            .title(R.string.title_passphrase)
            .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .input(null, null, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(MaterialDialog dialog, CharSequence input) {
                    startImport(zip, dialog.getInputEditText().getText().toString());
                }
            })
            .onNegative(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog materialDialog, @NonNull DialogAction dialogAction) {
                    try {
                        zip.close();
                    }
                    catch (IOException e) {
                        // ignored
                    }
                }
            })
            .negativeText(android.R.string.cancel)
            .positiveText(android.R.string.ok)
            .show();
    }

    private void startImport(InputStream in) {
        ZipInputStream zip = null;
        try {
            zip = new ZipInputStream(in);

            // ask passphrase to user and assign to mPassphrase
            importAskPassphrase(zip);
        }
        catch (Exception e) {
            Log.e(TAG, "error importing keys", e);
            ReportingManager.logException(e);
            mImportedPublicKey = mImportedPrivateKey = null;
            mTrustedKeys = null;

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

    @Override
    public void onFileSelection(@NonNull FileChooserDialog fileChooserDialog, @NonNull File file) {
        try {
            startImport(new FileInputStream(file));
        }
        catch (FileNotFoundException e) {
            Log.e(TAG, "error importing keys", e);
            Toast.makeText(this,
                R.string.err_import_keypair_read,
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onFileChooserDismissed(@NonNull FileChooserDialog dialog) {
    }

    void startImport(ZipInputStream zip, String passphrase) {
        PersonalKeyImporter importer = null;
        String manualServer = Preferences.getServerURI();

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

            Map<String, String> accountInfo = importer.getAccountInfo();
            if (accountInfo != null) {
                String phoneNumber = accountInfo.get("phoneNumber");
                if (!TextUtils.isEmpty(phoneNumber)) {
                    mPhoneNumber = phoneNumber;
                }
            }

            if (mPhoneNumber == null) {
                Toast.makeText(this, R.string.warn_invalid_number, Toast.LENGTH_SHORT).show();
                return;
            }

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

            try {
                mTrustedKeys = importer.getTrustedKeys();
            }
            catch (Exception e) {
                // this is not a critical error so we can just ignore it
                Log.w(TAG, "unable to load trusted keys from key pack", e);
                ReportingManager.logException(e);
            }
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
            ReportingManager.logException(e);
            mImportedPublicKey = mImportedPrivateKey = null;
            mTrustedKeys = null;
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

    /**
     * Final step in the import device process: load key data as a PersonalKey
     * and initiate a normal import process.
     */
    boolean startImport(EndpointServer server, String account, byte[] privateKeyData, byte[] publicKeyData, String passphrase) {
        String manualServer = null;
        try {
            PersonalKey key = PersonalKey.load(privateKeyData, publicKeyData, passphrase, (X509Certificate) null);

            String uidStr = key.getUserId(null);
            PGPUserID uid = PGPUserID.parse(uidStr);
            if (uid == null)
                throw new PGPException("malformed user ID: " + uidStr);

            // check that uid matches phone number
            String email = uid.getEmail();
            String numberHash = XMPPUtils.createLocalpart(account);
            String localpart = XmppStringUtils.parseLocalpart(email);
            if (!numberHash.equalsIgnoreCase(localpart))
                throw new PGPUidMismatchException("email does not match phone number: " + email);

            // use server from the key only if we didn't set our own
            if (server == null)
                manualServer = XmppStringUtils.parseDomain(email);
            else
                manualServer = server.toString();

            mName = uid.getName();
            mPhoneNumber = account;
            mImportedPublicKey = publicKeyData;
            mImportedPrivateKey = privateKeyData;
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
            ReportingManager.logException(e);
            mImportedPublicKey = mImportedPrivateKey = null;
            mTrustedKeys = null;
            mName = null;

            Toast.makeText(this,
                R.string.err_import_keypair_failed,
                Toast.LENGTH_LONG).show();
        }

        if (mImportedPublicKey != null && mImportedPrivateKey != null) {
            // we can now store the passphrase
            mPassphrase = passphrase;

            // begin usual validation
            // TODO implement fallback usage
            if (!startValidationNormal(manualServer, true, false, true)) {
                return false;
            }

            return true;
        }

        return false;
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
            mProgress = new NonSearchableDialog.Builder(this)
                .progress(true, 0)
                .cancelListener(new OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        keepScreenOn(false);
                        Toast.makeText(NumberValidation.this, R.string.msg_validation_canceled, Toast.LENGTH_LONG).show();
                        abort();
                    }
                })
                .dismissListener(new OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        // remove sync starter
                        if (mSyncStart != null) {
                            mHandler.removeCallbacks(mSyncStart);
                            mSyncStart = null;
                        }
                    }
                })
                .build();
            mProgress.setCanceledOnTouchOutside(false);
            setProgressMessage(message != null ? message : getText(R.string.msg_validating_phone));
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
            mProgress.setContent(message);
        }
    }

    void delayedSync() {
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
                finishLogin(v.getServer().toString(), v.getServerChallenge(), privateKey, publicKey, false, mTrustedKeys);
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

    @Override
    public void onPrivateKeyReceived(final NumberValidator v, final byte[] privateKey, final byte[] publicKey) {
        // ask for a passphrase
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new MaterialDialog.Builder(NumberValidation.this)
                    .title(R.string.title_passphrase)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                    .input(null, null, new MaterialDialog.InputCallback() {
                        @Override
                        public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                            if (!startImport(v.getServer(), v.getPhone(), privateKey, publicKey, input.toString())) {
                                abort();
                            }
                        }
                    })
                    .negativeText(android.R.string.cancel)
                    .positiveText(android.R.string.ok)
                    .show();
            }
        });
    }

    @Override
    public void onPrivateKeyRequestFailed(NumberValidator v, int reason) {
        // TODO
    }

    private void statusInitializing() {
        if (mProgress == null)
            startProgress();
        mProgress.setCancelable(false);
        setProgressMessage(getString(R.string.msg_initializing));
    }

    protected void finishLogin(final String serverUri, final String challenge,
            final byte[] privateKeyData, final byte[] publicKeyData, boolean updateKey,
            Map<String, Keyring.TrustedFingerprint> trustedKeys) {
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

        completeLogin(serverUri, challenge, privateKeyData, publicKeyData, trustedKeys);
    }

    private void completeLogin(String serverUri, String challenge,
            byte[] privateKeyData, byte[] publicKeyData, Map<String, Keyring.TrustedFingerprint> trustedKeys) {
        // generate the bridge certificate
        byte[] bridgeCertData;
        try {
            bridgeCertData = X509Bridge.createCertificate(privateKeyData, publicKeyData, mPassphrase).getEncoded();
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
                privateKeyData, publicKeyData, bridgeCertData,
                mName, serverUri, challenge, trustedKeys),
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
    public void onValidationRequested(NumberValidator v, String sender, String challenge, String brandImage, String brandLink, boolean canFallback) {
        Log.d(TAG, "validation has been requested, requesting validation code to user");
        proceedManual(sender, challenge, brandImage, brandLink, canFallback);
    }

    void userExistsWarning() {
        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
            .content(R.string.err_validation_user_exists)
            .positiveText(R.string.btn_device_import)
            .positiveColorRes(R.color.button_success)
            .negativeText(android.R.string.cancel)
            .neutralText(R.string.btn_device_overwrite)
            .neutralColorRes(R.color.button_danger)
            .onAny(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    switch (which) {
                        case POSITIVE:
                            importDevice();
                            break;
                        case NEUTRAL:
                            startValidation(true, false);
                            break;
                    }
                }
            });
        try {
            builder.show();
        }
        catch (Exception ignored) {
        }
    }

    /** Proceeds to the next step in manual validation. */
    private void proceedManual(final String sender, final String challenge, final String brandImage, final String brandLink, final boolean canFallback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abortProgress(true);
                startValidationCode(REQUEST_MANUAL_VALIDATION, sender, challenge, brandImage, brandLink, canFallback);
            }
        });
    }

    void startValidationCode(int requestCode, String sender, String challenge, String brandImage, String brandLink, boolean canFallback) {
        startValidationCode(requestCode, sender, challenge, brandImage, brandLink, canFallback, null, true);
    }

    private void startValidationCode(int requestCode, String sender, String challenge,
            String brandImage, String brandLink, boolean canFallback, EndpointServer server, boolean saveProgress) {
        // validator might be null if we are skipping verification code request
        String serverUri = null;
        if (server != null)
            serverUri = server.toString();
        else if (mValidator != null)
            serverUri = mValidator.getServer().toString();

        // save state to preferences
        if (saveProgress) {
            Preferences.saveRegistrationProgress(
                mName, mPhoneNumber, mKey, mPassphrase,
                mImportedPublicKey, mImportedPrivateKey,
                serverUri, sender, challenge, brandImage, brandLink,
                canFallback, mForce, mTrustedKeys);
        }

        Intent i = new Intent(NumberValidation.this, CodeValidation.class);
        i.putExtra("requestCode", requestCode);
        i.putExtra("name", mName);
        i.putExtra("phone", mPhoneNumber);
        i.putExtra("force", mForce);
        i.putExtra("passphrase", mPassphrase);
        i.putExtra("importedPublicKey", mImportedPublicKey);
        i.putExtra("importedPrivateKey", mImportedPrivateKey);
        i.putExtra("trustedKeys", mTrustedKeys != null ? (HashMap) Keyring.toTrustedFingerprintMap(mTrustedKeys) : null);
        i.putExtra("server", serverUri);
        i.putExtra("sender", sender);
        i.putExtra("challenge", challenge);
        i.putExtra("canFallback", canFallback);
        i.putExtra(KeyPairGeneratorService.EXTRA_KEY, mKey);
        i.putExtra("brandImage", brandImage);
        i.putExtra("brandLink", brandLink);

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
        private final String challenge;
        private final Map<String, Keyring.TrustedFingerprint> trustedKeys;

        AccountRemovalCallback(NumberValidation activity, Account account,
            String passphrase, byte[] privateKeyData, byte[] publicKeyData,
            byte[] bridgeCertData, String name, String serverUri, String challenge,
            Map<String, Keyring.TrustedFingerprint> trustedKeys) {
            this.a = new WeakReference<>(activity);
            this.account = account;
            this.passphrase = passphrase;
            this.privateKeyData = privateKeyData;
            this.publicKeyData = publicKeyData;
            this.bridgeCertData = bridgeCertData;
            this.name = name;
            this.serverUri = serverUri;
            this.challenge = challenge;
            this.trustedKeys = trustedKeys;
        }

        @Override
        public void run(AccountManagerFuture<Boolean> result) {
            NumberValidation ctx = a.get();
            if (ctx != null) {
                // store trusted keys
                if (trustedKeys != null) {
                    Keyring.setTrustedKeys(ctx, trustedKeys);
                }

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

                ReportingManager.logSignUp(challenge);

                // manual sync starter
                ctx.delayedSync();
            }
        }

    }
}

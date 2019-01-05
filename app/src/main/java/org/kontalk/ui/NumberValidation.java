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

package org.kontalk.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.folderselector.FileChooserDialog;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jivesoftware.smack.util.StringUtils;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.text.HtmlCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import butterknife.BindView;
import butterknife.ButterKnife;
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
import org.kontalk.crypto.PersonalKey;
import org.kontalk.crypto.PersonalKeyPack;
import org.kontalk.provider.Keyring;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.DatabaseImporterService;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.service.KeyPairGeneratorService.KeyGeneratorReceiver;
import org.kontalk.service.KeyPairGeneratorService.PersonalKeyRunnable;
import org.kontalk.service.MessagesImporterService;
import org.kontalk.service.registration.RegistrationService;
import org.kontalk.service.registration.event.AcceptTermsRequest;
import org.kontalk.service.registration.event.AccountCreatedEvent;
import org.kontalk.service.registration.event.FallbackVerificationRequest;
import org.kontalk.service.registration.event.ImportKeyError;
import org.kontalk.service.registration.event.ImportKeyRequest;
import org.kontalk.service.registration.event.KeyReceivedEvent;
import org.kontalk.service.registration.event.LoginTestEvent;
import org.kontalk.service.registration.event.PassphraseInputEvent;
import org.kontalk.service.registration.event.RetrieveKeyError;
import org.kontalk.service.registration.event.RetrieveKeyRequest;
import org.kontalk.service.registration.event.ServerCheckError;
import org.kontalk.service.registration.event.TermsAcceptedEvent;
import org.kontalk.service.registration.event.VerificationError;
import org.kontalk.service.registration.event.VerificationRequest;
import org.kontalk.service.registration.event.VerificationRequestedEvent;
import org.kontalk.sync.SyncAdapter;
import org.kontalk.ui.adapter.CountryCodesAdapter;
import org.kontalk.ui.adapter.CountryCodesAdapter.CountryCode;
import org.kontalk.ui.prefs.PreferencesActivity;
import org.kontalk.util.ParameterRunnable;
import org.kontalk.util.Permissions;
import org.kontalk.util.Preferences;
import org.kontalk.util.SystemUtils;


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
    public static final String PARAM_TERMS_URL = "org.kontalk.termsURL";
    public static final String PARAM_CHALLENGE = "org.kontalk.challenge";
    public static final String PARAM_TRUSTED_KEYS = "org.kontalk.trustedkeys";

    private static final String CHOOSER_TAG_MESSAGES_DB = "messages.db";

    private AccountManager mAccountManager;

    @BindView(R.id.name)
    EditText mNameText;
    @BindView(R.id.phone_cc)
    Spinner mCountryCode;
    @BindView(R.id.phone_number)
    EditText mPhone;
    @BindView(R.id.button_validate)
    Button mValidateButton;

    private MaterialDialog mProgress;
    private CharSequence mProgressMessage;

    @Deprecated
    NumberValidator mValidator;
    Handler mHandler;

    private String mPhoneNumber;
    private String mName;

    @Deprecated
    PersonalKey mKey;
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
    private BroadcastReceiver mMessagesImporterReceiver;

    private EventBus mServiceBus = RegistrationService.bus();

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
        ButterKnife.bind(this);
        setupToolbar(false, false);

        mAccountManager = AccountManager.get(this);
        mHandler = new Handler();

        lbm = LocalBroadcastManager.getInstance(getApplicationContext());

        final Intent intent = getIntent();
        mFromInternal = intent.getBooleanExtra(PARAM_FROM_INTERNAL, false);

        // populate country codes
        final CountryCodesAdapter ccList = new CountryCodesAdapter(this,
                R.layout.countrycode_spinner_item,
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
                // unused
            }
        });

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

        // start registration service immediately
        RegistrationService.start(this);
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
        state.putBoolean("permissionsAsked", mPermissionsAsked);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mName = savedInstanceState.getString("name");
        mPhoneNumber = savedInstanceState.getString("phoneNumber");
        mKey = savedInstanceState.getParcelable("key");
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
        menu.findItem(R.id.menu_import_messages_database).setVisible(BuildConfig.DEBUG);
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
            case R.id.menu_import_messages_database: {
                importMessagesDatabase();
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

        // TODO instead of reading our current progress, wait for the
        // Registration service to send as an event for restoring the current
        // state. Be careful to the mClearState flag (I don't remember what was for :)

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
            mForce = saved.force;

            // update UI
            mNameText.setText(mName);
            mPhone.setText(mPhoneNumber);
            syncCountryCodeSelector();

            startValidationCode(REQUEST_MANUAL_VALIDATION, saved.sender,
                saved.brandImage, saved.brandLink, saved.challenge, saved.canFallback);
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
                String mPassphrase = StringUtils.randomString(40);

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
        mServiceBus.unregister(this);

        stopKeyReceiver();
        stopMessagesImporterReceiver();

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
        if (mKeyReceiver != null) {
            lbm.unregisterReceiver(mKeyReceiver);
            mKeyReceiver = null;
        }
    }

    void stopMessagesImporterReceiver() {
        if (mMessagesImporterReceiver != null) {
            lbm.unregisterReceiver(mMessagesImporterReceiver);
            mMessagesImporterReceiver = null;
        }
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
                    data.getStringExtra(PARAM_TERMS_URL),
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

    @AfterPermissionGranted(Permissions.RC_PHONE_STATE)
    @SuppressLint("MissingPermission")
    void detectMyNumber() {
        PhoneNumberUtil util = PhoneNumberUtil.getInstance();

        // FIXME this doesn't consider creation because of configuration change
        CountryCodesAdapter ccList = (CountryCodesAdapter) mCountryCode.getAdapter();
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
    }

    private void askPermissions() {
        if (mPermissionsAsked)
            return;

        Permissions.requestPhoneState(this, getString(R.string.err_validation_phone_state_denied));

        if (!Permissions.canWriteContacts(this)) {
            Permissions.requestContacts(this, getString(R.string.err_validation_contacts_denied));
        }

        mPermissionsAsked = true;
    }

    private void keepScreenOn(boolean active) {
        Log.i(TAG, "keeping screen " + (active ? "ON" : "OFF"));
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
        Log.i(TAG, "setting controls " + (enabled ? "ENABLED" : "DISABLED"));
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

    void onGenericError(Throwable e) {
        keepScreenOn(false);
        int msgId;
        if (e instanceof SocketException) {
            msgId = R.string.err_validation_network_error;
        }
        else {
            msgId = R.string.err_validation_error;
        }
        Toast.makeText(NumberValidation.this, msgId, Toast.LENGTH_LONG).show();
        abort();
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
        enableControls(false);

        checkInput(false, new ParameterRunnable<Boolean>() {
            @Override
            public void run(Boolean result) {
                if (result) {
                    if (fallback) {
                        startFallbackValidation();
                    }
                    else {
                        startValidationNormal(null, force);
                    }
                }
                else {
                    enableControls(true);
                }
            }
        });
    }

    @RegistrationService.BrandImageSize
    private int getBrandImageSize() {
        int density = getResources().getDisplayMetrics().densityDpi;
        if (density <= DisplayMetrics.DENSITY_MEDIUM) {
            return RegistrationService.BRAND_IMAGE_SMALL;
        }
        else if (density <= DisplayMetrics.DENSITY_HIGH) {
            return RegistrationService.BRAND_IMAGE_MEDIUM;
        }
        else if (density <= DisplayMetrics.DENSITY_XXHIGH) {
            return RegistrationService.BRAND_IMAGE_LARGE;
        }
        else {
            return RegistrationService.BRAND_IMAGE_HD;
        }
    }

    private boolean startValidationNormal(String manualServer, boolean force) {
        if (!SystemUtils.isNetworkConnectionAvailable(this)) {
            error(R.string.err_validation_nonetwork);
            return false;
        }

        // start async request
        Log.d(TAG, "phone number checked, sending validation request");
        startProgress();

        EndpointServer.EndpointServerProvider provider;
        if (manualServer != null) {
            provider = new EndpointServer.SingleServerProvider(manualServer);
        }
        else {
            provider = Preferences.getEndpointServerProvider(this);
        }

        mServiceBus.register(this);
        mServiceBus.post(new VerificationRequest(mPhoneNumber, mName,
            provider, force, getBrandImageSize()));
        return true;
    }

    private boolean startFallbackValidation() {
        if (!SystemUtils.isNetworkConnectionAvailable(this)) {
            error(R.string.err_validation_nonetwork);
            return false;
        }

        // start async request
        Log.d(TAG, "sending fallback validation request");
        startProgress();

        mServiceBus.register(this);
        mServiceBus.post(new FallbackVerificationRequest());
        return true;
    }

    /**
     * Begins validation of the phone number.
     * Also used by the view definition as the {@link OnClickListener}.
     * @param v not used
     */
    public void validatePhone(View v) {
        startValidation(false, false);
    }

    private void importMessagesDatabase() {
        if (!Permissions.canReadExternalStorage(NumberValidation.this)) {
            // TODO rationale
            Permissions.requestReadExternalStorage(NumberValidation.this, null, 1);
        }
        else {
            browseImportMessagesDatabase();
        }
    }

    // Permissions added 1*100 because of index 1 provided in the call
    @AfterPermissionGranted(Permissions.RC_READ_EXT_STORAGE + 100)
    void browseImportMessagesDatabase() {
        new FileChooserDialog.Builder(NumberValidation.this)
            .mimeType("*/*")
            .tag(CHOOSER_TAG_MESSAGES_DB)
            .show(getSupportFragmentManager());
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

        startProgress(getString(R.string.import_device_requesting));

        mServiceBus.register(this);
        mServiceBus.post(new RetrieveKeyRequest(new EndpointServer(server), account, token));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onKeyReceived(final KeyReceivedEvent event) {
        new MaterialDialog.Builder(NumberValidation.this)
            .title(R.string.title_passphrase)
            .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .input(null, null, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                    mServiceBus.post(new PassphraseInputEvent(input.toString()));
                }
            })
            .negativeText(android.R.string.cancel)
            .positiveText(android.R.string.ok)
            .show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRetrieveKeyError(RetrieveKeyError event) {
        // FIXME maybe a somewhat more detailed explaination
        onGenericError(event.exception);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onImportKeyError(ImportKeyError event) {
        if (event.exception instanceof RegistrationService.NoPhoneNumberFoundException) {
            Toast.makeText(this, R.string.warn_invalid_number, Toast.LENGTH_SHORT).show();
        }
        else {
            onGenericError(event.exception);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLoginTest(LoginTestEvent event) {
        if (event.exception != null) {
            if (RegistrationService.currentState().workflow == RegistrationService.Workflow.IMPORT_KEY) {
                // We are importing a key but the server refused it.
                // Proceed with normal verification but using the imported key
                // which will be signed by the server again
                // TODO proceed with normal verification flow
            }
            else {
                onGenericError(event.exception);
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAccountCreated(AccountCreatedEvent event) {
        // send back result
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, event.account.name);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Authenticator.ACCOUNT_TYPE);

        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);

        ReportingManager.logSignUp(RegistrationService.currentState().challenge);

        // manual sync starter
        delayedSync();
    }

    private void importAskPassphrase(final InputStream zip) {
        new MaterialDialog.Builder(this)
            .title(R.string.title_passphrase)
            .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .input(null, null, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                    startProgress(getString(R.string.msg_importing_key));

                    mServiceBus.register(NumberValidation.this);
                    mServiceBus.post(new ImportKeyRequest(Preferences
                        .getEndpointServer(NumberValidation.this),
                        zip, input.toString()));
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

    @Override
    public void onFileSelection(@NonNull FileChooserDialog fileChooserDialog, @NonNull File file) {
        if (CHOOSER_TAG_MESSAGES_DB.equals(fileChooserDialog.getTag())) {
            // FIXME this whole progress and report system is for debug purposes only
            if (mMessagesImporterReceiver == null) {
                mMessagesImporterReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        stopMessagesImporterReceiver();

                        String error = intent.getStringExtra(DatabaseImporterService.EXTRA_ERROR);
                        if (error == null)
                            // TODO i18n
                            error = "Messages imported successfully.";

                        new MaterialDialog.Builder(NumberValidation.this)
                            // TODO i18n
                            .title("Import messages")
                            .content(error)
                            .positiveText(android.R.string.ok)
                            .show();
                    }
                };
                lbm.registerReceiver(mMessagesImporterReceiver,
                    new IntentFilter(DatabaseImporterService.ACTION_FINISH));
            }

            // TODO setup progress dialog
            MessagesImporterService.startImport(this, Uri.fromFile(file));
        }
        else {
            try {
                importAskPassphrase(new FileInputStream(file));
            }
            catch (FileNotFoundException e) {
                Log.e(TAG, "error importing keys", e);
                Toast.makeText(this,
                    R.string.err_import_keypair_read,
                    Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onFileChooserDismissed(@NonNull FileChooserDialog dialog) {
    }

    /**
     * Final step in the import device process: load key data as a PersonalKey
     * and initiate a normal import process.

    @Deprecated
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
    */

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
        enableControls(false);
        keepScreenOn(true);
        mProgress.show();
    }

    public void abort() {
        mForce = false;
        if (mValidator != null) {
            mValidator.shutdown();
            mValidator = null;
        }
        keepScreenOn(false);
        enableControls(true);
        mServiceBus.unregister(this);
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

    /** Mainly for dismissing the cancel listener for the progress dialog. */
    @Override
    public void finish() {
        if (mProgress != null) {
            mProgress.setOnCancelListener(null);
        }
        super.finish();
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
    @Deprecated
    public void onAuthTokenReceived(final NumberValidator v, final byte[] privateKey, final byte[] publicKey) {
    }

    @Override
    @Deprecated
    public void onAuthTokenFailed(NumberValidator v, int reason) {
    }

    @Override
    @Deprecated
    public void onPrivateKeyReceived(final NumberValidator v, final byte[] privateKey, final byte[] publicKey) {
    }

    @Override
    @Deprecated
    public void onPrivateKeyRequestFailed(NumberValidator v, int reason) {
    }

    private void statusInitializing() {
        if (mProgress == null)
            startProgress();
        mProgress.setCancelable(false);
        setProgressMessage(getString(R.string.msg_initializing));
    }

    protected void finishLogin(final String serverUri, final String termsUrl, final String challenge,
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

        completeLogin(serverUri, termsUrl, challenge, privateKeyData, publicKeyData, trustedKeys);
    }

    private void completeLogin(String serverUri, String termsUrl, String challenge,
            byte[] privateKeyData, byte[] publicKeyData, Map<String, Keyring.TrustedFingerprint> trustedKeys) {
    }

    @Override
    @Deprecated
    public void onError(NumberValidator v, final Throwable e) {
    }

    @Override
    @Deprecated
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
    @Deprecated
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

    @Deprecated
    @Override
    public void onAcceptTermsRequired(final NumberValidator v, final String termsUrl) {
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAcceptTermsRequested(AcceptTermsRequest request) {
        // build dialog text
        RegistrationService.CurrentState cstate = RegistrationService.currentState();
        String baseText = getString(R.string.registration_accept_terms_text,
            cstate.server.getNetwork(), request.termsUrl);

        Spanned text = HtmlCompat.fromHtml(baseText, 0);

        MaterialDialog dialog = new MaterialDialog.Builder(this)
            .title(R.string.registration_accept_terms_title)
            .content(text)
            .positiveText(R.string.yes)
            .positiveColorRes(R.color.button_success)
            .negativeText(R.string.no)
            .negativeColorRes(R.color.button_danger)
            .onAny(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    switch (which) {
                        case POSITIVE:
                            mServiceBus.post(new TermsAcceptedEvent());
                            break;
                        case NEGATIVE:
                            abort();
                            break;
                    }
                }
            })
            .build();

        try {
            dialog.show();
        }
        catch (Exception ignored) {
        }
    }

    @Deprecated
    @Override
    public void onValidationRequested(NumberValidator v, String sender, String challenge, String brandImage, String brandLink, boolean canFallback) {
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVerificationRequested(VerificationRequestedEvent event) {
        Log.d(TAG, "validation has been requested, requesting validation code to user");
        proceedManual(event.sender, event.challenge, event.brandImageUrl, event.brandLink, event.canFallback);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVerificationError(VerificationError error) {
        if (error instanceof ServerCheckError) {
            Toast.makeText(this, R.string.err_validation_server_not_supported,
                Toast.LENGTH_LONG).show();
        }
        else {
            Log.e(TAG, "validation error.", error.exception);
            keepScreenOn(false);
            int msgId;
            if (error.exception instanceof SocketException)
                msgId = R.string.err_validation_network_error;
            else
                msgId = R.string.err_validation_error;
            Toast.makeText(this, msgId, Toast.LENGTH_LONG).show();
        }
        abort();
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
                startValidationCode(REQUEST_MANUAL_VALIDATION, sender, challenge, brandImage, brandLink, canFallback);
            }
        });
    }

    void startValidationCode(int requestCode, String sender, String challenge,
            String brandImage, String brandLink, boolean canFallback) {

        Intent i = new Intent(NumberValidation.this, CodeValidation.class);
        i.putExtra("requestCode", requestCode);
        i.putExtra("sender", sender);
        i.putExtra("challenge", challenge);
        i.putExtra("canFallback", canFallback);
        i.putExtra("brandImage", brandImage);
        i.putExtra("brandLink", brandLink);

        startActivityForResult(i, REQUEST_MANUAL_VALIDATION);
    }

}

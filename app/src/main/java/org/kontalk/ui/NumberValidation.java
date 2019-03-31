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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Dialog;
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
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
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
import org.kontalk.crypto.PersonalKeyPack;
import org.kontalk.reporting.ReportingManager;
import org.kontalk.service.DatabaseImporterService;
import org.kontalk.service.MessagesImporterService;
import org.kontalk.service.registration.RegistrationService;
import org.kontalk.service.registration.event.AbortRequest;
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
import org.kontalk.service.registration.event.ThrottlingError;
import org.kontalk.service.registration.event.UserConflictError;
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
        implements FileChooserDialog.FileCallback {
    static final String TAG = NumberValidation.class.getSimpleName();

    static final int REQUEST_MANUAL_VALIDATION = 771;
    static final int REQUEST_VALIDATION_CODE = 772;
    static final int REQUEST_SCAN_TOKEN = 773;
    static final int REQUEST_ASK_TOKEN = 774;

    public static final int RESULT_FALLBACK = RESULT_FIRST_USER + 1;

    public static final String PARAM_FROM_INTERNAL = "org.kontalk.internal";

    public static final String PARAM_ACCOUNT_NAME = "org.kontalk.accountName";
    public static final String PARAM_PUBLICKEY = "org.kontalk.publickey";
    public static final String PARAM_PRIVATEKEY = "org.kontalk.privatekey";
    public static final String PARAM_SERVER_URI = "org.kontalk.server";
    public static final String PARAM_TERMS_URL = "org.kontalk.termsURL";
    public static final String PARAM_CHALLENGE = "org.kontalk.challenge";
    public static final String PARAM_TRUSTED_KEYS = "org.kontalk.trustedkeys";

    private static final String CHOOSER_TAG_MESSAGES_DB = "messages.db";

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

    Handler mHandler;

    private boolean mForce;

    private LocalBroadcastManager lbm;

    /** Will be true when resuming for a fallback registration. */
    private boolean mClearState;
    boolean mFromInternal;
    /** Runnable for delaying initial manual sync starter. */
    Runnable mSyncStart;
    private boolean mSyncing;

    private boolean mWaitingAcceptTerms;
    private boolean mPermissionsAsked;

    /**
     * A list of servers for which the user accepted the service terms.
     * We need to keep a record so we don't ask twice for the same server
     * (that's because two attempts might get two different random servers).
     */
    private Set<String> mAcceptedTermsServers = new HashSet<>();

    private BroadcastReceiver mMessagesImporterReceiver;

    private EventBus mServiceBus = RegistrationService.bus();

    private static final class RetainData {
        /** @deprecated Use saved instance state. */
        @Deprecated
        CharSequence progressMessage;
        /** @deprecated Use saved instance state. */
        @Deprecated
        boolean syncing;

        RetainData() {
        }
    }

    private static final class RegistrationInput {
        final String displayName;
        final String phoneNumber;

        RegistrationInput(String displayName, String phoneNumber) {
            this.displayName = displayName;
            this.phoneNumber = phoneNumber;
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
        state.putBoolean("waitingAcceptTerms", mWaitingAcceptTerms);
        state.putBoolean("permissionsAsked", mPermissionsAsked);
        state.putStringArrayList("acceptedTermsServers", new ArrayList<>(mAcceptedTermsServers));
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mWaitingAcceptTerms = savedInstanceState.getBoolean("waitingAcceptTerms");
        mPermissionsAsked = savedInstanceState.getBoolean("permissionsAsked");
        List<String> acceptedTermsServers = savedInstanceState.getStringArrayList("acceptedTermsServers");
        if (acceptedTermsServers != null) {
            mAcceptedTermsServers = new HashSet<>(acceptedTermsServers);
        }
    }

    /** Returning the validator thread. */
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        RetainData data = new RetainData();
        if (mProgress != null) data.progressMessage = mProgressMessage;
        data.syncing = mSyncing;
        return data;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.number_validation_menu, menu);
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

        if (RegistrationService.hasSavedState()) {
            if (mClearState) {
                RegistrationService.clearSavedState();
                mClearState = false;
            }
            else {
                // register to the bus immediately if we have saved state
                register();
            }
        }

        // start registration service immediately
        RegistrationService.start(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        keepScreenOn(false);
        mServiceBus.unregister(this);

        stopMessagesImporterReceiver();

        if (mProgress != null) {
            if (!isWaitingAcceptTerms() && isFinishing())
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

    private void register() {
        if (!mServiceBus.isRegistered(this)) {
            mServiceBus.register(this);
        }
    }

    private void register(Object postEvent) {
        register();
        mServiceBus.post(postEvent);
    }

    private void unregister() {
        if (mServiceBus.isRegistered(this)) {
            mServiceBus.unregister(this);
        }
    }

    private boolean isWaitingAcceptTerms() {
        return mWaitingAcceptTerms;
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
    @SuppressWarnings("unchecked")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MANUAL_VALIDATION) {
            if (resultCode == RESULT_OK) {
                finishLogin(data.getStringExtra(PARAM_ACCOUNT_NAME));
            }
            else if (resultCode == RESULT_FALLBACK) {
                mClearState = true;
                startValidation(RegistrationService.currentState().force, true);
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

    private void checkInput(boolean importing, final ParameterRunnable<RegistrationInput> callback) {
        final String phoneStr;
        final String displayName;

        // check name first
        if (!importing) {
            displayName = mNameText.getText().toString().trim();
            if (displayName.length() == 0) {
                error(R.string.msg_no_name);
                callback.run(null);
                return;
            }
        }
        else {
            // retrieve from imported key later
            displayName = null;
        }

        String phoneInput = mPhone.getText().toString();
        String phoneConfirm;
        // if the user entered a phone number use it even when importing for backward compatibility
        if (!importing || !phoneInput.isEmpty()){
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            CountryCode cc = (CountryCode) mCountryCode.getSelectedItem();
            if (cc == null) {
                error(R.string.msg_invalid_cc);
                callback.run(null);
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
                    callback.run(null);
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
                callback.run(null);
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
                                    callback.run(new RegistrationInput(displayName, phoneStr));
                                    break;
                                case NEGATIVE:
                                    callback.run(null);
                                    break;
                            }
                        }
                    })
                    .cancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            callback.run(null);
                        }
                    })
                    .build();

                TextView phoneTextView = (TextView) dialog.findViewById(R.id.bigtext);
                phoneTextView.setText(phoneConfirm);

                dialog.show();
            }
            else {
                callback.run(new RegistrationInput(displayName, phoneStr));
            }
        }
        else {
            // we will use the data from the imported key
            callback.run(new RegistrationInput(null, null));
        }
    }

    void startValidation(final boolean force, final boolean fallback) {
        mForce = force;
        enableControls(false);

        checkInput(false, new ParameterRunnable<RegistrationInput>() {
            @Override
            public void run(RegistrationInput result) {
                if (result != null) {
                    if (fallback) {
                        startFallbackValidation();
                    }
                    else {
                        startValidationNormal(null, result.displayName, result.phoneNumber, force);
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

    private boolean startValidationNormal(String manualServer, String displayName, String phoneNumber, boolean force) {
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

        register(new VerificationRequest(phoneNumber, displayName,
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

        register(new FallbackVerificationRequest());
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
        checkInput(true, new ParameterRunnable<RegistrationInput>() {
            @Override
            public void run(RegistrationInput result) {
                if (result != null) {
                    // import keys -- number verification with server is still needed
                    // though because of key rollback protection
                    // TODO allow for manual validation too

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

        register(new RetrieveKeyRequest(new EndpointServer(server), account, token));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onKeyReceived(final KeyReceivedEvent event) {
        new MaterialDialog.Builder(NumberValidation.this)
            .title(R.string.title_passphrase)
            .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
            .input(null, null, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                    register(new PassphraseInputEvent(input.toString()));
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
        RegistrationService.stop(this);

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

                    register(new ImportKeyRequest(Preferences
                        .getEndpointServer(NumberValidation.this),
                        zip, input.toString(), true, getBrandImageSize()));
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
            MaterialDialog.Builder builder = new NonSearchableDialog
                .Builder(this)
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
                });

            if (!BuildConfig.TESTING.get()) {
                builder.progress(true, 0);
            }

            mProgress = builder.build();
            mProgress.setCanceledOnTouchOutside(false);
            setProgressMessage(message != null ? message : getText(R.string.msg_validating_phone));
        }
        enableControls(false);
        keepScreenOn(true);
        mProgress.show();
    }

    private void abortProgress() {
        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }
        keepScreenOn(false);
        enableControls(true);
    }

    public void abort() {
        abort(false);
    }

    public void abort(boolean ending) {
        if (!ending) {
            abortProgress();
        }

        unregister();
        mServiceBus.post(new AbortRequest());

        mForce = false;
        keepScreenOn(false);
        enableControls(true);
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

    private void statusInitializing() {
        if (mProgress == null)
            startProgress();
        mProgress.setCancelable(false);
        setProgressMessage(getString(R.string.msg_initializing));
    }

    protected void finishLogin(final String accountName) {
        Log.v(TAG, "finishing login");
        statusInitializing();

        onAccountCreated(new AccountCreatedEvent(Authenticator.getDefaultAccount(this)));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAcceptTermsRequested(AcceptTermsRequest request) {
        RegistrationService.CurrentState cstate = RegistrationService.currentState();
        if (mAcceptedTermsServers.contains(cstate.server.getNetwork())) {
            // terms already accepted for this server
            register(new TermsAcceptedEvent());
            return;
        }

        mWaitingAcceptTerms = true;

        DialogFragment dialog = AcceptTermsDialogFragment.newInstance(cstate.server.getNetwork(), request.termsUrl);
        dialog.show(getSupportFragmentManager(), "accept_terms");
    }

    public void onAcceptTermsConfirmed(String network) {
        mAcceptedTermsServers.add(network);
        startProgress();
        register(new TermsAcceptedEvent());
    }

    public void onAcceptTermsCancel() {
        abort();
    }

    public void onAcceptTermsDismiss() {
        mWaitingAcceptTerms = false;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVerificationRequested(VerificationRequestedEvent event) {
        RegistrationService.CurrentState state = RegistrationService.currentState();
        if (state.restored) {
            mForce = state.force;

            // update UI
            mNameText.setText(state.displayName);
            mPhone.setText(state.phoneNumber);
            syncCountryCodeSelector();
        }

        Log.d(TAG, "validation has been requested, requesting validation code to user");
        proceedManual(event.sender, event.brandImageUrl, event.brandLink, event.canFallback);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVerificationError(VerificationError error) {
        if (error instanceof ServerCheckError) {
            Toast.makeText(this, R.string.err_validation_server_not_supported,
                Toast.LENGTH_LONG).show();
        }
        else if (error instanceof UserConflictError) {
            userExistsWarning();
        }
        else if (error instanceof ThrottlingError) {
            Toast.makeText(this, R.string.err_validation_retry_later,
                Toast.LENGTH_LONG).show();
        }
        else {
            Log.e(TAG, "validation error.", error.exception);
            keepScreenOn(false);
            int msgId;
            if (error.exception instanceof SocketException) {
                msgId = R.string.err_validation_network_error;
            }
            else {
                msgId = R.string.err_validation_error;
            }
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
    private void proceedManual(final String sender, final String brandImage, final String brandLink, final boolean canFallback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abortProgress();
                startValidationCode(REQUEST_MANUAL_VALIDATION, sender, brandImage, brandLink, canFallback);
            }
        });
    }

    void startValidationCode(int requestCode, String sender,
            String brandImage, String brandLink, boolean canFallback) {

        Intent i = new Intent(NumberValidation.this, CodeValidation.class);
        i.putExtra("requestCode", requestCode);
        i.putExtra("sender", sender);
        i.putExtra("canFallback", canFallback);
        i.putExtra("brandImage", brandImage);
        i.putExtra("brandLink", brandLink);

        startActivityForResult(i, REQUEST_MANUAL_VALIDATION);
    }

    public static final class AcceptTermsDialogFragment extends DialogFragment {

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            // build dialog text
            String network = getArguments().getString("network");
            String termsUrl = getArguments().getString("termsURL");
            String baseText = getString(R.string.registration_accept_terms_text,
                network, termsUrl);

            Spanned text = HtmlCompat.fromHtml(baseText, 0);

            return new MaterialDialog.Builder(getActivity())
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
                                ((NumberValidation) getActivity())
                                    .onAcceptTermsConfirmed(getArguments().getString("network"));
                                break;
                            case NEGATIVE:
                                ((NumberValidation) getActivity())
                                    .onAcceptTermsCancel();
                                break;
                        }
                    }
                })
                .dismissListener(new OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        ((NumberValidation) getActivity())
                            .onAcceptTermsDismiss();
                    }
                })
                .build();
        }

        public static AcceptTermsDialogFragment newInstance(String network, String termsUrl) {
            AcceptTermsDialogFragment f = new AcceptTermsDialogFragment();
            Bundle args = new Bundle();
            args.putString("network", network);
            args.putString("termsURL", termsUrl);
            f.setArguments(args);
            return f;
        }
    }

}

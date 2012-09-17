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

package org.kontalk.ui;

import java.net.SocketException;

import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.NumberValidator.NumberValidatorListener;
import org.kontalk.client.Protocol.RegistrationResponse.RegistrationStatus;
import org.kontalk.client.Protocol.ValidationResponse.ValidationStatus;
import org.kontalk.service.MessageCenterService;
import org.kontalk.util.SyncerUI;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;


public class NumberValidation extends SherlockAccountAuthenticatorActivity
        implements NumberValidatorListener {
    private static final String TAG = NumberValidation.class.getSimpleName();

    public static final int REQUEST_MANUAL_VALIDATION = 771;
    public static final int REQUEST_VALIDATION_CODE = 772;

    public static final String ACTION_LOGIN = "org.kontalk.sync.LOGIN";

    public static final String PARAM_AUTHTOKEN_TYPE = "org.kontalk.authtokenType";
    public static final String PARAM_FROM_INTERNAL = "org.kontalk.internal";

    public static final String PARAM_AUTHTOKEN = "org.kontalk.authtoken";

    private AccountManager mAccountManager;
    private EditText mCountryCode;
    private EditText mPhone;
    private Button mValidateButton;
    private Button mManualButton;
    private ProgressDialog mProgress;
    private CharSequence mProgressMessage;
    private NumberValidator mValidator;
    private Handler mHandler;

    private String mAuthtoken;
    private String mAuthtokenType;
    private String mPhoneNumber;
    private boolean mManualValidation;

    private boolean mFromInternal;

    private static final class RetainData {
        NumberValidator validator;
        CharSequence progressMessage;
        String phoneNumber;
        boolean syncWasRunning;
    }

    private final Runnable mSyncFinish = new Runnable() {
        public void run() {
            // if we have been called internally, start ConversationList
            if (mFromInternal)
                startActivity(new Intent(getApplicationContext(), ConversationList.class));

            Toast.makeText(NumberValidation.this, R.string.msg_authenticated, Toast.LENGTH_LONG).show();

            // end this
            abortProgress();
            finish();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.number_validation);

        mAccountManager = AccountManager.get(this);
        mHandler = new Handler();

        final Intent intent = getIntent();
        mAuthtokenType = intent.getStringExtra(PARAM_AUTHTOKEN_TYPE);
        mFromInternal = intent.getBooleanExtra(PARAM_FROM_INTERNAL, false);

        mCountryCode = (EditText) findViewById(R.id.phone_cc);
        mPhone = (EditText) findViewById(R.id.phone_number);
        mValidateButton = (Button) findViewById(R.id.button_validate);
        mManualButton = (Button) findViewById(R.id.button_manual);

        PhoneNumber myNum = NumberValidator.getMyNumber(this);
        if (myNum != null) {
            mPhone.setText(String.valueOf(myNum.getNationalNumber()));
            mCountryCode.setText(String.valueOf(myNum.getCountryCode()));
        }
        else {
            mCountryCode.setText(NumberValidator.getCountryCode(this));
        }

        // configuration change??
        RetainData data = (RetainData) getLastNonConfigurationInstance();
        if (data != null) {
            mPhoneNumber = data.phoneNumber;
            mValidator = data.validator;
            if (mValidator != null)
                mValidator.setListener(this, mHandler);

            if (data.progressMessage != null) {
                setProgressMessage(data.progressMessage, true);
            }
            if (data.syncWasRunning) {
                SyncerUI.execute(this, mSyncFinish, false);
            }
        }
    }

    /** Returning the validator thread. */
    @Override
    public Object onRetainNonConfigurationInstance() {
        RetainData data = new RetainData();
        data.validator = mValidator;
        data.phoneNumber = mPhoneNumber;
        if (mProgress != null) data.progressMessage = mProgressMessage;
        data.syncWasRunning = SyncerUI.isRunning();
        return data;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.number_validation_menu, menu);
        MenuItem item = menu.findItem(R.id.menu_settings);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
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
            default:
                return true;
        }
        return false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        keepScreenOn(false);
        if (mProgress != null) {
            if (isFinishing())
                mProgress.cancel();
            else
                mProgress.dismiss();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        keepScreenOn(false);
        if (mProgress != null)
            mProgress.cancel();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MANUAL_VALIDATION && resultCode == RESULT_OK) {
            finishLogin(data.getStringExtra(PARAM_AUTHTOKEN));
        }
    }

    private void keepScreenOn(boolean active) {
        if (active)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** Starts the validation activity. */
    public static void startValidation(Context context) {
        Intent i = new Intent(context, NumberValidation.class);
        i.putExtra(PARAM_FROM_INTERNAL, true);
        context.startActivity(i);
    }

    private void enableControls(boolean enabled) {
        mValidateButton.setEnabled(enabled);
        mManualButton.setEnabled(enabled);
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

    private boolean checkInput() {
        // check country code input
        String cc = mCountryCode.getText().toString().trim();
        if (cc.length() < 1) {
            error(R.string.title_invalid_number, R.string.msg_invalid_cc);
            return false;
        }

        // check number input
        String phone = null;
        try {
            String p = mPhone.getText().toString().trim();
            if (p.length() < 3 || !TextUtils.isDigitsOnly(p)) throw new Exception();
            String t = "+" + cc + p;
            phone = NumberValidator.fixNumber(this, t, t, cc);
        }
        catch (Exception e) {
            error(R.string.title_invalid_number, R.string.msg_invalid_number);
            return false;
        }
        // exposing sensitive data - Log.d(TAG, "checking phone number: \"" + phone + "\"");

        // empty number :S
        if (phone.length() == 0) {
            phone = null;
        }

        // check phone number format
        if (phone != null) {
            if (!PhoneNumberUtils.isWellFormedSmsAddress(phone)) {
                Log.i(TAG, "not a well formed SMS address");
                phone = null;
            }
        }

        // phone is null - invalid number
        if (phone == null) {
            Toast.makeText(this, R.string.warn_invalid_number, Toast.LENGTH_SHORT)
                .show();
            return false;
        }

        mPhoneNumber = phone;
        return true;
    }

    private void startValidation() {
        enableControls(false);

        if (!checkInput()) {
            enableControls(true);
        }
        else {
            // start async request
            Log.d(TAG, "phone number checked, sending validation request");
            startProgress();

            EndpointServer server = MessagingPreferences.getEndpointServer(this);
            mValidator = new NumberValidator(this, server, mPhoneNumber, mManualValidation);
            mValidator.setListener(this, mHandler);
            mValidator.start();
        }
    }

    /**
     * Opens the manual validation window for manual input of the validation code.
     * Also used by the view definition as the {@link OnClickListener}.
     * @param v not used
     */
    public void validateManual(View v) {
        // we are starting a manual validation
        mManualValidation = true;
        startValidation();
    }

    /**
     * Begins validation of the phone number.
     * Also used by the view definition as the {@link OnClickListener}.
     * @param v not used
     */
    public void validatePhone(View v) {
        keepScreenOn(true);
        // we are starting an automatic validation
        mManualValidation = false;
        startValidation();
    }

    /**
     * Opens manual validation window immediately.
     * Also used by the view definition as the {@link OnClickListener}.
     * @param v not used
     */
    public void validateCode(View v) {
        if (checkInput())
            startValidationCode(REQUEST_VALIDATION_CODE);
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

    @Override
    public void onAuthTokenFailed(NumberValidator v, ValidationStatus reason) {
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

    protected void finishLogin(String token) {
        Log.v(TAG, "finishing login");
        final Account account = new Account(mPhoneNumber, Authenticator.ACCOUNT_TYPE);
        mAuthtoken = token;

        // the password is actually the auth token
        mAccountManager.addAccountExplicitly(account, mAuthtoken, null);
        // Set contacts sync for this account.
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);

        // send back result
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mPhoneNumber);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Authenticator.ACCOUNT_TYPE);
        if (mAuthtokenType != null
            && mAuthtokenType.equals(Authenticator.AUTHTOKEN_TYPE)) {
            intent.putExtra(AccountManager.KEY_AUTHTOKEN, mAuthtoken);
        }
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);

        // ok, start message center
        MessageCenterService.startMessageCenter(getApplicationContext());

        if (mProgress == null)
            startProgress();
        mProgress.setCancelable(false);
        mProgress.setCanceledOnTouchOutside(false);
        setProgressMessage(getString(R.string.msg_initializing));

        // manual sync
        SyncerUI.execute(this, mSyncFinish, false);
    }

    @Override
    public void onAuthTokenReceived(NumberValidator v, final CharSequence token) {
        Log.d(TAG, "got authentication token!");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abort(true);
                finishLogin(token.toString());
            }
        });
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
                // TODO i18n
                Toast.makeText(NumberValidation.this, "Selected server does not support registration. Please configure another one in Settings screen.", Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

    @Override
    public void onValidationFailed(NumberValidator v, RegistrationStatus reason) {
        Log.e(TAG, "phone number validation failed (" + reason + ")");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(NumberValidation.this, R.string.err_validation_failed, Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

    @Override
    public void onValidationRequested(NumberValidator v) {
        if (mManualValidation) {
            Log.d(TAG, "validation has been requested, requesting validation code to user");
            proceedManual();
        }
        else
            Log.d(TAG, "validation has been requested, waiting for SMS");
    }

    @Override
    public void onValidationCodeReceived(NumberValidator v, CharSequence code) {
        Log.d(TAG, "validation SMS received, restarting validator thread");
        // start again!
        if (mValidator != null)
            mValidator.start();
    }

    @Override
    public void onValidationCodeTimeout(NumberValidator v) {
        Log.d(TAG, "validation SMS still not received, going manual");
        proceedManual();
    }

    /** Proceeds to the next step in manual validation. */
    private void proceedManual() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abortProgress(true);
                startValidationCode(REQUEST_MANUAL_VALIDATION);
            }
        });
    }

    private void startValidationCode(int requestCode) {
        Intent i = new Intent(NumberValidation.this, CodeValidation.class);
        i.putExtra("requestCode", requestCode);
        startActivityForResult(i, REQUEST_MANUAL_VALIDATION);
    }
}

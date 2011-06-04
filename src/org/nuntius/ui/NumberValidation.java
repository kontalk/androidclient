package org.nuntius.ui;

import org.nuntius.R;
import org.nuntius.authenticator.Authenticator;
import org.nuntius.client.EndpointServer;
import org.nuntius.client.NumberValidator;
import org.nuntius.client.NumberValidator.NumberValidatorListener;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class NumberValidation extends AccountAuthenticatorActivity implements NumberValidatorListener {
    private static final String TAG = NumberValidation.class.getSimpleName();

    public static final String ACTION_LOGIN = "org.nuntius.sync.LOGIN";

    public static final String PARAM_AUTHTOKEN_TYPE = "authtokenType";
    public static final String PARAM_CONFIRMCREDENTIALS = "confirmCredentials";
    public static final String PARAM_PHONENUMBER = "phoneNumber";

    private AccountManager mAccountManager;
    private EditText mPhone;
    private Button mButton;
    private ProgressDialog mProgress;
    private NumberValidator mValidator;

    private String mAuthtoken;
    private String mAuthtokenType;
    private String mPhoneNumber;

    /**
     * If set we are just checking that the user knows their credentials; this
     * doesn't cause the user's password to be changed on the device.
     */
    private Boolean mConfirmCredentials = false;

    /** Was the original caller asking for an entirely new account? */
    protected boolean mRequestNewAccount = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.number_validation);

        mAccountManager = AccountManager.get(this);

        final Intent intent = getIntent();
        mPhoneNumber = intent.getStringExtra(PARAM_PHONENUMBER);
        mAuthtokenType = intent.getStringExtra(PARAM_AUTHTOKEN_TYPE);
        mRequestNewAccount = (mPhoneNumber == null);
        mConfirmCredentials =
            intent.getBooleanExtra(PARAM_CONFIRMCREDENTIALS, false);

        mPhone = (EditText) findViewById(R.id.phone_number);
        mPhone.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // input has done!
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    Log.i(TAG, "IME closed, starting validation");
                    validatePhone(null);
                }
                return false;
            }
        });

        mButton = (Button) findViewById(R.id.button_validate);
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
            default:
                return true;
        }
        return false;
    }

    /**
     * Begins validation of the phone number.
     * Also used by the view definition as the {@link OnClickListener}.
     * @param v not used
     */
    public void validatePhone(View v) {
        mButton.setEnabled(false);

        // check number input
        String phone = PhoneNumberUtils.stripSeparators(
                mPhone.getText().toString().trim());
        Log.i(TAG, "checking phone number: \"" + phone + "\"");

        // empty number :S
        if (phone.length() == 0) {
            phone = null;
            // event not coming from IME done action
            if (v == null) return;
        }

        // check phone number format
        if (phone != null) {
            if (!PhoneNumberUtils.isWellFormedSmsAddress(phone)) {
                Log.w(TAG, "not a well formed SMS address");
                phone = null;
            }
        }

        // phone is null - invalid number
        if (phone == null) {
            Toast.makeText(this, R.string.warn_invalid_number, Toast.LENGTH_SHORT)
                .show();
            return;
        }

        mPhoneNumber = phone;

        // start async request
        Log.i(TAG, "phone number checked, sending validation request");
        if (mProgress == null) {
            mProgress = new ProgressDialog(this);
            mProgress.setIndeterminate(true);
            mProgress.setMessage(getText(R.string.msg_validating_phone));
            mProgress.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Log.i(TAG, "progress dialog canceled.");
                    Toast.makeText(NumberValidation.this, "Validation canceled. You might receive a SMS anyway.", Toast.LENGTH_LONG).show();
                    abort();
                }
            });
        }
        mProgress.show();

        EndpointServer server = new EndpointServer
            (MessagingPreferences.getServerURI(this));
        mValidator = new NumberValidator(this, server, phone);
        mValidator.setListener(this);
        mValidator.start();
    }

    /**
     * No search here.
     */
    @Override
    public boolean onSearchRequested() {
        return false;
    }

    public void abort() {
        abort(false);
    }

    public void abort(boolean ending) {
        if (!ending)
            mButton.setEnabled(true);

        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }
        if (mValidator != null) {
            mValidator.shutdown();
            mValidator = null;
        }
    }

    @Override
    public void onAuthTokenFailed(NumberValidator v, int reason) {
        Log.e(TAG, "authorization token request failed (" + reason + ")");
        // TODO handle error
        abort();
    }

    protected void finishLogin(String token) {
        Log.i(TAG, "finishLogin()");
        final Account account = new Account(mPhoneNumber, Authenticator.ACCOUNT_TYPE);
        mAuthtoken = token;

        if (mRequestNewAccount) {
            // the password is actually the auth token
            mAccountManager.addAccountExplicitly(account, mAuthtoken, null);
            // Set contacts sync for this account.
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);
        } else {
            // TODO what here??
        }

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

        finish();
    }

    protected void finishConfirmCredentials(boolean result) {
        Log.i(TAG, "finishConfirmCredentials()");

        // the password is actually the auth token
        final Account account = new Account(mPhoneNumber, Authenticator.ACCOUNT_TYPE);
        mAccountManager.setPassword(account, mAuthtoken);

        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onAuthTokenReceived(NumberValidator v, final String token) {
        Log.i(TAG, "got authorization token! (" + token + ")");
        abort(true);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!mConfirmCredentials) {
                    Toast.makeText(NumberValidation.this, R.string.msg_authenticated, Toast.LENGTH_LONG).show();
                    finishLogin(token);
                }
                else {
                    finishConfirmCredentials(true);
                }
            }
        });
    }

    @Override
    public void onError(NumberValidator v, Throwable e) {
        Log.e(TAG, "validation error.", e);
        // TODO handle error
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abort();
            }
        });
    }

    @Override
    public void onValidationFailed(NumberValidator v, int reason) {
        Log.e(TAG, "phone number validation failed (" + reason + ")");
        // TODO handle error
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abort();
            }
        });
    }

    @Override
    public void onValidationRequested(NumberValidator v) {
        Log.i(TAG, "validation has been requested, waiting for SMS");
        // TODO what here??
    }

    @Override
    public void onValidationCodeReceived(NumberValidator v, String code) {
        Log.i(TAG, "validation SMS received, restarting validator thread");
        // start again!
        if (mValidator != null)
            mValidator.start();
    }
}

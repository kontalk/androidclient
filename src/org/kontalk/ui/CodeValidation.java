package org.kontalk.ui;

import java.net.SocketException;

import org.kontalk.xmpp.R;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.NumberValidator.NumberValidatorListener;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;


/** Manual validation code input. */
public class CodeValidation extends SherlockAccountAuthenticatorActivity
        implements NumberValidatorListener {
    private static final String TAG = CodeValidation.class.getSimpleName();

    private EditText mCode;
    private Button mButton;
    private NumberValidator mValidator;

    private static final class RetainData {
        NumberValidator validator;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.code_validation_screen);

        setSupportProgressBarIndeterminate(true);
        // HACK this is for crappy honeycomb :)
        setSupportProgressBarIndeterminateVisibility(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mCode = (EditText) findViewById(R.id.validation_code);
        mButton = (Button) findViewById(R.id.send_button);

        // configuration change??
        RetainData data = (RetainData) getLastNonConfigurationInstance();
        if (data != null) {
            mValidator = data.validator;
            if (mValidator != null) {
                startProgress();
                mValidator.setListener(this, null);
            }
        }

        int requestCode = getIntent().getIntExtra("requestCode", -1);
        if (requestCode == NumberValidation.REQUEST_VALIDATION_CODE) {
            ((TextView) findViewById(R.id.code_validation_intro))
                .setText(R.string.code_validation_intro2);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** No search here. */
    @Override
    public boolean onSearchRequested() {
        return false;
    }

    /** Returning the validator thread. */
    @Override
    public Object onRetainNonConfigurationInstance() {
        RetainData data = new RetainData();
        data.validator = mValidator;
        return data;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing())
            abort();
    }

    private void keepScreenOn(boolean active) {
        if (active)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void error(int title, int message) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNeutralButton(android.R.string.ok, null)
            .show();
    }

    public void validateCode(View view) {
        String code = mCode.getText().toString().trim();
        if (code.length() == 0) {
            error(R.string.title_invalid_code, R.string.msg_invalid_code);
            return;
        }

        startProgress();

        // send the code
        EndpointServer server = MessagingPreferences.getEndpointServer(this);
        mValidator = new NumberValidator(this, server, null, true);
        mValidator.setListener(this, null);

        mValidator.manualInput(code);
        mValidator.start();
    }

    private void enableControls(boolean enabled) {
        mButton.setEnabled(enabled);
        mCode.setEnabled(enabled);
    }

    private void startProgress() {
        setSupportProgressBarIndeterminateVisibility(true);
        enableControls(false);
        keepScreenOn(true);
    }

    private void abort() {
        setSupportProgressBarIndeterminateVisibility(false);
        enableControls(true);
        keepScreenOn(false);
        if (mValidator != null) {
            mValidator.shutdown();
            mValidator = null;
        }
    }

    @Override
    public void onError(NumberValidator v, final Throwable e) {
        Log.e(TAG, "validation error.", e);
        runOnUiThread(new Runnable() {
            public void run() {
                int msgId;
                if (e instanceof SocketException)
                    msgId = R.string.err_validation_network_error;
                else
                    msgId = R.string.err_validation_error;
                Toast.makeText(CodeValidation.this, msgId, Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

    @Override
    public void onServerCheckFailed(NumberValidator v) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(CodeValidation.this, R.string.err_validation_server_not_supported, Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

    @Override
    public void onValidationRequested(NumberValidator v) {
        // not used.
    }

    @Override
    public void onValidationFailed(NumberValidator v, int reason) {
        // not used.
    }

    @Override
    public void onValidationCodeReceived(NumberValidator v, CharSequence code) {
        // not used.
    }

    @Override
    public void onValidationCodeTimeout(NumberValidator v) {
        // not used.
    }

    @Override
    public void onAuthTokenReceived(NumberValidator v, final CharSequence token) {
        Log.d(TAG, "got authentication token!");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abort();
                Intent i = new Intent();
                i.putExtra(NumberValidation.PARAM_AUTHTOKEN, token);
                setResult(RESULT_OK, i);
                finish();
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
                Toast.makeText(CodeValidation.this,
                        R.string.err_authentication_failed,
                        Toast.LENGTH_LONG).show();
                abort();
            }
        });
    }

}

/*
 * Kontalk Android client
 * Copyright (C) 2014 Kontalk Devteam <devteam@kontalk.org>

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
import org.kontalk.client.EndpointServer;
import org.kontalk.client.NumberValidator;
import org.kontalk.client.NumberValidator.NumberValidatorListener;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.service.KeyPairGeneratorService;
import org.kontalk.util.Preferences;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


/** Manual validation code input. */
public class CodeValidation extends AccountAuthenticatorActionBarActivity
        implements NumberValidatorListener {
    private static final String TAG = CodeValidation.class.getSimpleName();

    private EditText mCode;
    private Button mButton;
    private NumberValidator mValidator;
    private PersonalKey mKey;
    private String mName;
    private String mPhone;
    private String mPassphrase;
    private EndpointServer mServer;

    private static final class RetainData {
        NumberValidator validator;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.code_validation_screen);

        //setSupportProgressBarIndeterminate(true);
        // HACK this is for crappy honeycomb :)
        setSupportProgressBarIndeterminateVisibility(false);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mCode = (EditText) findViewById(R.id.validation_code);
        mButton = (Button) findViewById(R.id.send_button);

        // configuration change??
        RetainData data = (RetainData) getLastCustomNonConfigurationInstance();
        if (data != null) {
            mValidator = data.validator;
            if (mValidator != null) {
                startProgress();
                mValidator.setListener(this);
            }
        }

        int requestCode = getIntent().getIntExtra("requestCode", -1);
        if (requestCode == NumberValidation.REQUEST_VALIDATION_CODE) {
            ((TextView) findViewById(R.id.code_validation_intro))
                .setText(R.string.code_validation_intro2);
        }

        Intent i = getIntent();
        mKey = i.getParcelableExtra(KeyPairGeneratorService.EXTRA_KEY);
        mName = i.getStringExtra("name");
        mPhone = i.getStringExtra("phone");
        mPassphrase = i.getStringExtra("passphrase");

        String server = i.getStringExtra("server");
        if (server != null)
            mServer = new EndpointServer(server);
        else
            /*
             * FIXME HUGE problem here. If we already have a verification code,
             * how are we supposed to know from what server it came from??
             * @see issue 184.
             * http://code.google.com/p/kontalk/issues/detail?id=184
             */
            mServer = Preferences.getEndpointServer(this);
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
    public Object onRetainCustomNonConfigurationInstance() {
        RetainData data = new RetainData();
        data.validator = mValidator;
        return data;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing())
            abort(true);
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
        mValidator = new NumberValidator(this, mServer, mName, mPhone, mKey, mPassphrase);
        mValidator.setListener(this);

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

    private void abort(boolean ending) {
        if (!ending) {
            setSupportProgressBarIndeterminateVisibility(false);
            enableControls(true);
        }
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
                abort(false);
            }
        });
    }

    @Override
    public void onServerCheckFailed(NumberValidator v) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(CodeValidation.this, R.string.err_validation_server_not_supported, Toast.LENGTH_LONG).show();
                abort(false);
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
    public void onAuthTokenReceived(final NumberValidator v, final byte[] privateKeyData, final byte[] publicKeyData) {
        Log.d(TAG, "got authentication token!");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                abort(true);
                Intent i = new Intent();
                i.putExtra(NumberValidation.PARAM_PUBLICKEY, publicKeyData);
                i.putExtra(NumberValidation.PARAM_PRIVATEKEY, privateKeyData);
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
                abort(false);
            }
        });
    }

}

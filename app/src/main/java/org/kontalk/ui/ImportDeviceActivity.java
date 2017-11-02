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

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.client.NumberValidator;


/**
 * Allows the user to input the server name and the secret code for importing
 * the personal key from another device.
 * @author Daniele Ricci
 */
public class ImportDeviceActivity extends ToolbarActivity implements View.OnClickListener {

    public static final String EXTRA_ACCOUNT = "org.kontalk.import_device.account";
    public static final String EXTRA_SERVER = "org.kontalk.import_device.server";
    public static final String EXTRA_TOKEN = "org.kontalk.import_device.token";

    private EditText mTextAccount;
    private EditText mTextServer;
    private EditText mTextToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_device_screen);

        mTextAccount = findViewById(R.id.account);
        mTextServer = findViewById(R.id.servername);
        mTextToken = findViewById(R.id.token);

        findViewById(R.id.button_import).setOnClickListener(this);

        setupToolbar(true, true);
    }

    /** Used only for the main button. */
    @Override
    public void onClick(View view) {
        String account = mTextAccount.getText().toString().trim();
        String server = mTextServer.getText().toString().trim();
        String token = mTextToken.getText().toString().trim();
        if (TextUtils.isEmpty(account) || TextUtils.isEmpty(server) || TextUtils.isEmpty(token)) {
            error(R.string.err_import_device_fill_all);
            return;
        }

        if (!BuildConfig.DEBUG) {
            // verify the phone number just in case
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            Phonenumber.PhoneNumber phone;
            try {
                phone = util.parse(account, null);
                // handle special cases
                NumberValidator.handleSpecialCases(phone);

                String regionCode = util.getRegionCodeForCountryCode(phone.getCountryCode());
                if (!util.isValidNumberForRegion(phone, regionCode) && !NumberValidator.isSpecialNumber(phone))
                    throw new NumberParseException(NumberParseException.ErrorType.INVALID_COUNTRY_CODE, "invalid number for region " + regionCode);
            }
            catch (Exception e) {
                error(R.string.msg_invalid_number);
                return;
            }

            account = util.format(phone, PhoneNumberUtil.PhoneNumberFormat.E164);
        }

        token = preprocessToken(token);

        Intent data = new Intent();
        data.putExtra(EXTRA_ACCOUNT, account);
        data.putExtra(EXTRA_SERVER, server);
        data.putExtra(EXTRA_TOKEN, token);
        setResult(RESULT_OK, data);
        finish();
    }

    /** Preprocess a token typed in by the user (e.g. remove spaces). */
    private String preprocessToken(CharSequence rawToken) {
        return rawToken.toString().replace(" ", "");
    }

    private void error(@StringRes int text) {
        new MaterialDialog.Builder(this)
            .content(text)
            .positiveText(android.R.string.ok)
            .show();
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }

    public static void start(@NonNull Activity activity, int requestCode) {
        activity.startActivityForResult(new Intent(activity,
            ImportDeviceActivity.class), requestCode);
    }

}

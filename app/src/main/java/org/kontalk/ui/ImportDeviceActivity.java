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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;

import org.kontalk.R;


/**
 * Allows the user to input the server name and the secret code for importing
 * the personal key from another device.
 * @author Daniele Ricci
 */
public class ImportDeviceActivity extends ToolbarActivity implements View.OnClickListener {

    public static final String EXTRA_SERVER = "org.kontalk.import_device.server";
    public static final String EXTRA_TOKEN = "org.kontalk.import_device.token";

    private EditText mTextServer;
    private EditText mTextToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.import_device_screen);

        mTextServer = findViewById(R.id.servername);
        mTextToken = findViewById(R.id.token);

        findViewById(R.id.button_import).setOnClickListener(this);

        setupToolbar(true, true);
    }

    /** Used only for the main button. */
    @Override
    public void onClick(View view) {
        String server = mTextServer.getText().toString().trim();
        String token = mTextToken.getText().toString().trim();
        if (TextUtils.isEmpty(server) || TextUtils.isEmpty(token)) {
            new MaterialDialog.Builder(this)
                // TODO i18n
                .content("Please fill in all fields.")
                .positiveText(android.R.string.ok)
                .show();
            return;
        }

        Intent data = new Intent();
        data.putExtra(EXTRA_SERVER, server);
        data.putExtra(EXTRA_TOKEN, token);
        setResult(RESULT_OK, data);
        finish();
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

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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.kontalk.Kontalk;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.util.ViewUtils;


/**
 * Shows the secure token for registering another device with the same key.
 * @author Daniele Ricci
 */
public class RegisterDeviceActivity extends ToolbarActivity {
    private static final String TAG = Kontalk.TAG;

    private View mViewport;
    private TextView mAccountName;
    private TextView mTextServer;
    private TextView mTextToken;
    private ImageView mQRCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_device_screen);

        setupToolbar(true, true);

        mViewport = findViewById(R.id.scroller);
        mAccountName = findViewById(R.id.account);
        mTextServer = findViewById(R.id.servername);
        mTextToken = findViewById(R.id.token);
        mQRCode = findViewById(R.id.qrcode);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onStart() {
        super.onStart();

        String account = Authenticator.getDefaultAccountName(this);
        mAccountName.setText(account);

        String from = getIntent().getStringExtra("from");
        mTextServer.setText(from);

        String token = getIntent().getStringExtra("token");

        if ((token.length() % 2) == 0) {
            mTextToken.setText(token.substring(0, token.length() / 2) + " " +
                token.substring(token.length() / 2));
        }
        else {
            mTextToken.setText(token);
        }

        ViewUtils.getQRCodeBitmapAsync(this, mViewport, mQRCode,
            generateTokenText(account, token, from));
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }

    /** This must match the parsing done in {@link #parseTokenText}. */
    private static String generateTokenText(String account, String token, String from) {
        return account + ";" + from + ";" + token;
    }

    /** This must match the generator in {@link #generateTokenText}. */
    public static PrivateKeyToken parseTokenText(String tokenText) {
        String[] parsed = tokenText.split(";", 3);
        return (parsed.length == 3) ?
            new PrivateKeyToken(parsed[0], parsed[1], parsed[2]) : null;
    }

    /** A token parsed by {@link #parseTokenText}. */
    public static final class PrivateKeyToken {
        public final String account;
        public final String server;
        public final String token;

        PrivateKeyToken(String account, String server, String token) {
            this.account = account;
            this.server = server;
            this.token = token;
        }
    }

    public static void start(@NonNull Context context, @NonNull String token, @NonNull String server) {
        Intent i = new Intent(context, RegisterDeviceActivity.class);
        i.putExtra("token", token);
        i.putExtra("from", server);
        context.startActivity(i);
    }

}

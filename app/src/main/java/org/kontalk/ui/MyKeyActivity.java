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

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.kontalk.Kontalk;
import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PGP;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.util.ViewUtils;


/**
 * My key activity.
 * @author Daniele Ricci
 */
public class MyKeyActivity extends ToolbarActivity {
    private static final String TAG = Kontalk.TAG;

    private View mViewport;
    private TextView mAccountName;
    private TextView mTextName;
    private TextView mTextFingerprint;
    private ImageView mQRCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mykey_screen);

        setupToolbar(true, true);

        mViewport = findViewById(R.id.scroller);
        mAccountName = findViewById(R.id.account);
        mTextName = findViewById(R.id.name);
        mTextFingerprint = findViewById(R.id.fingerprint);
        mQRCode = findViewById(R.id.qrcode);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mAccountName.setText(Authenticator.getDefaultAccountName(this));

        // load personal key
        PersonalKey key;
        try {
            key = Kontalk.get().getPersonalKey();
        }
        catch (Exception e) {
            // TODO handle errors
            Log.w(TAG, "unable to load personal key");
            return;
        }

        // TODO network
        mTextName.setText(key.getUserId(null));

        String fingerprint = key.getFingerprint();
        mTextFingerprint.setText(PGP.formatFingerprint(fingerprint)
            .replaceFirst("  ", "\n"));

        ViewUtils.getQRCodeBitmapAsync(this, mViewport, mQRCode,
            PGP.createFingerprintURI(fingerprint));
    }

    @Override
    protected boolean isNormalUpNavigation() {
        return true;
    }

}

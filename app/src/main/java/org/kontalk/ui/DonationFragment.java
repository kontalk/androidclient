/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.kontalk.R;
import org.kontalk.util.SystemUtils;


/**
 * Donation fragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class DonationFragment extends Fragment implements OnClickListener {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_donation, container, false);

        view.findViewById(R.id.donate_paypal).setOnClickListener(this);
        view.findViewById(R.id.donate_bitcoin).setOnClickListener(this);
        view.findViewById(R.id.donate_flattr).setOnClickListener(this);

        return view;
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.donate_paypal:
                donatePaypal();
                break;

            case R.id.donate_bitcoin:
                donateBitcoin();
                break;

            case R.id.donate_flattr:
                donateFlattr();
                break;
        }
    }

    private void donateFlattr() {
        SystemUtils.openURL(getContext(), getString(R.string.flattr_url));
    }

    private void donateBitcoin() {
        final String address = getString(R.string.bitcoin_address);
        Uri uri = Uri.parse("bitcoin:" + address);
        Intent intent = SystemUtils.externalIntent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));

        final Context ctx = getContext();
        final PackageManager pm = ctx.getPackageManager();
        if (pm.resolveActivity(intent, 0) != null)
            startActivity(intent);
        else
            new MaterialDialog.Builder(ctx)
                .title(R.string.title_bitcoin_dialog)
                .content(getString(R.string.text_bitcoin_dialog, address))
                .positiveText(android.R.string.ok)
                .neutralText(R.string.copy_clipboard)
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        Context ctx = dialog.getContext();
                        ClipboardManager cpm = (ClipboardManager) ctx
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                        cpm.setPrimaryClip(ClipData.newPlainText(null, address));

                        Toast.makeText(ctx, R.string.bitcoin_clipboard_copied,
                            Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void donatePaypal() {
        // just start Paypal donate button URL
        SystemUtils.openURL(getContext(), getString(R.string.paypal_url));
    }

    private void alert(String message) {
        new MaterialDialog.Builder(getContext())
            .content(message)
            .positiveText(android.R.string.ok)
            .show();
    }
}

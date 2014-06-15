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

import java.util.Arrays;

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.billing.IabHelper;
import org.kontalk.billing.IabResult;
import org.kontalk.billing.Inventory;
import org.kontalk.billing.Purchase;
import org.kontalk.billing.SkuDetails;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.ClipboardManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Toast;


/**
 * Donation fragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class DonationFragment extends Fragment implements OnClickListener {

    // for Google Play
    private IabHelper mIabHelper;
    private static final int IAB_REQUEST_CODE = 10001;

    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {

        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            if (mIabHelper == null) return;

            // end async operation
            mIabHelper.flagEndAsync();

            if (result.isSuccess())
                Toast.makeText(getActivity(), R.string.msg_iab_thankyou, Toast.LENGTH_LONG).show();
        }
    };

    public IabHelper getIabHelper() {
		return mIabHelper;
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_donation, container, false);

        view.findViewById(R.id.donate_google).setOnClickListener(this);
        view.findViewById(R.id.donate_paypal).setOnClickListener(this);
        view.findViewById(R.id.donate_bitcoin).setOnClickListener(this);
        view.findViewById(R.id.donate_flattr).setOnClickListener(this);

        return view;
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.donate_google:
                donateGoogle();
                break;

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
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.flattr_url))));
    }

    private void donateBitcoin() {
        final String address = getString(R.string.bitcoin_address);
        Uri uri = Uri.parse("bitcoin:" + address);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));

        Activity ctx = getActivity();
        final PackageManager pm = ctx.getPackageManager();
        if (pm.resolveActivity(intent, 0) != null)
            startActivity(intent);
        else
            new AlertDialog
	            .Builder(getActivity())
	            .setTitle(R.string.title_bitcoin_dialog)
	            .setMessage(getString(R.string.text_bitcoin_dialog, address))
	            .setPositiveButton(android.R.string.ok, null)
	            .setNeutralButton(R.string.copy_clipboard, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						ClipboardManager cpm = (ClipboardManager) getActivity()
								.getSystemService(Context.CLIPBOARD_SERVICE);
						cpm.setText(address);

						Toast.makeText(getActivity(), R.string.bitcoin_clipboard_copied,
							Toast.LENGTH_LONG).show();
					}
				})
	            .show();
    }

    private void donatePaypal() {
        // just start Paypal donate button URL
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.paypal_url))));
    }

    private void setupGoogle(final ProgressDialog progress) {
    	if (mIabHelper == null) {
	        mIabHelper = new IabHelper(getActivity());
	        mIabHelper.enableDebugLogging(BuildConfig.DEBUG);

	        mIabHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
	            public void onIabSetupFinished(IabResult result) {

	                if (!result.isSuccess()) {
	                    alert(R.string.title_error, getString(R.string.iab_error_setup, result.getResponse()));
	                    mIabHelper = null;
	                    progress.dismiss();
	                    return;
	                }

	                queryInventory(progress);
	            }
	        });
    	}

    	else {
    		queryInventory(progress);
    	}
    }

    private void queryInventory(final ProgressDialog progress) {
        final String[] iabItems = getResources().getStringArray(R.array.iab_items);

        IabHelper.QueryInventoryFinishedListener gotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
                if (mIabHelper == null) return;

                // dismiss progress
                progress.dismiss();

                if (result.isFailure()) {
                    alert(R.string.title_error, getString(R.string.iab_error_query, result.getResponse()));
                }

                else {

                    // end async operation
                    mIabHelper.flagEndAsync();

                    // prepare items for the dialog
                    String[] dialogItems = new String[iabItems.length];

                    for (int i = 0; i < iabItems.length; i++) {
                        SkuDetails sku = inventory.getSkuDetails(iabItems[i]);
                        if (sku != null)
                            dialogItems[i] = sku.getDescription();
                        else
                            dialogItems[i] = iabItems[i];
                    }

                    // show dialog with choices
                    new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.title_donation)
                        .setItems(dialogItems, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                // start the purchase
                                String itemId = iabItems[which];
                                mIabHelper.launchPurchaseFlow(getActivity(), itemId,
                                    IAB_REQUEST_CODE, mPurchaseFinishedListener);
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                }
            }
        };


        if (mIabHelper != null)
            mIabHelper.queryInventoryAsync(true, Arrays
                .asList(iabItems), gotInventoryListener);
    }

    private void donateGoogle() {
        // progress dialog
        ProgressDialog dialog = ProgressDialog.show(getActivity(), getString(R.string.title_donation),
            getString(R.string.msg_connecting_iab), true, true,
            new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                	// FIXME this doesn't seem to work in some cases
                    if (mIabHelper != null) {
                    	mIabHelper.dispose();
                    	mIabHelper = null;
                    }
                }
            });

        setupGoogle(dialog);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mIabHelper != null) {
            mIabHelper.dispose();
            mIabHelper = null;
        }
    }

    private void alert(int title, String message) {
        new AlertDialog
            .Builder(getActivity())
            .setTitle(title)
            .setMessage(message)
            .setNeutralButton(android.R.string.ok, null)
            .show();
    }
}

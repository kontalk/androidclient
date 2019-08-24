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

import java.util.Arrays;

import org.kontalk.Log;
import org.kontalk.R;
import org.kontalk.billing.BillingResult;
import org.kontalk.billing.BillingServiceManager;
import org.kontalk.billing.IBillingService;
import org.kontalk.billing.IInventory;
import org.kontalk.billing.IProductDetails;
import org.kontalk.billing.IPurchase;
import org.kontalk.billing.OnBillingSetupFinishedListener;
import org.kontalk.billing.OnConsumeFinishedListener;
import org.kontalk.billing.OnPurchaseFinishedListener;
import org.kontalk.billing.QueryInventoryFinishedListener;
import org.kontalk.util.SystemUtils;

import android.app.Activity;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
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
    private IBillingService mBillingService;
    private static final int IAB_REQUEST_CODE = 10001;

    OnPurchaseFinishedListener mPurchaseFinishedListener = new OnPurchaseFinishedListener() {

        public void onPurchaseFinished(BillingResult result, IPurchase purchase) {
            if (mBillingService == null) return;

            if (result.isSuccess()) {
                // consume purchase in the background
                mBillingService.consumeAsync(purchase, mConsumeFinishedListener);
            }
        }
    };

    OnConsumeFinishedListener mConsumeFinishedListener = new OnConsumeFinishedListener() {
        public void onConsumeFinished(IPurchase purchase, BillingResult result) {
            int msg;
            if (result.isSuccess())
                msg = R.string.msg_iab_thankyou;
            else
                msg = R.string.msg_iab_thankyou_warning;
            Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
        }
    };

    private final class OnPreConsumeFinishedListener implements OnConsumeFinishedListener {
        private int mConsumedItems;
        private String[] mItems;

        public void onConsumeFinished(IPurchase purchase, BillingResult result) {
            mConsumedItems++;
            if (mConsumedItems >= mItems.length) {
                mConsumedItems = 0;
                showDonationSelector(mItems);
            }
        }

        public void setDonationItems(String[] items) {
            mItems = items;
            mConsumedItems = 0;
        }
    }

    OnPreConsumeFinishedListener mPreConsumeFinishedListener = new OnPreConsumeFinishedListener();

    public IBillingService getBillingService() {
        return mBillingService;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_donation, container, false);

        View button = view.findViewById(R.id.donate_google);
        if (BillingServiceManager.isEnabled())
            button.setOnClickListener(this);
        else
            button.setVisibility(View.GONE);

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
        SystemUtils.openURL(getContext(), getString(R.string.flattr_url));
    }

    private void donateBitcoin() {
        final String address = getString(R.string.bitcoin_address);
        Uri uri = Uri.parse("bitcoin:" + address);
        Intent intent = SystemUtils.externalIntent(Intent.ACTION_VIEW, Uri.parse(uri.toString()));

        Activity ctx = getActivity();
        final PackageManager pm = ctx.getPackageManager();
        if (pm.resolveActivity(intent, 0) != null)
            startActivity(intent);
        else
            new MaterialDialog.Builder(getActivity())
                .title(R.string.title_bitcoin_dialog)
                .content(getString(R.string.text_bitcoin_dialog, address))
                .positiveText(android.R.string.ok)
                .neutralText(R.string.copy_clipboard)
                .onNeutral(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
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
        SystemUtils.openURL(getContext(), getString(R.string.paypal_url));
    }

    private void setupGoogle(final Dialog progress) {
        if (mBillingService == null) {
            mBillingService = BillingServiceManager.getInstance(getActivity());
            mBillingService.enableDebugLogging(Log.isDebug());

            mBillingService.startSetup(new OnBillingSetupFinishedListener() {
                public void onSetupFinished(BillingResult result) {
                    if (!result.isSuccess()) {
                        alert(getString(R.string.iab_error_setup, result.getResponse()));
                        mBillingService = null;
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

    private void queryInventory(final Dialog progress) {
        final String[] iabItems = getResources().getStringArray(R.array.iab_items);

        QueryInventoryFinishedListener gotInventoryListener = new QueryInventoryFinishedListener() {
            public void onQueryInventoryFinished(BillingResult result, IInventory inventory) {
                if (mBillingService == null) return;

                // dismiss progress
                progress.dismiss();

                if (result.isFailure()) {
                    alert(getString(R.string.iab_error_query, result.getResponse()));
                }

                else {
                    // prepare items for the dialog
                    String[] dialogItems = new String[iabItems.length];

                    for (int i = 0; i < iabItems.length; i++) {
                        IProductDetails sku = inventory.getSkuDetails(iabItems[i]);
                        if (sku != null) {
                            dialogItems[i] = sku.getDescription();
                        }
                        else {
                            dialogItems[i] = iabItems[i];
                        }
                    }

                    // setup pre-consume listener
                    mPreConsumeFinishedListener.setDonationItems(dialogItems);
                    int purchases = 0;
                    // consume purchases just to be sure
                    for (String item : iabItems) {
                        IPurchase purchase = inventory.getPurchase(item);
                        if (purchase != null) {
                            purchases++;
                            mBillingService.consumeAsync(purchase,
                                mPreConsumeFinishedListener);
                        }
                    }

                    // no purchases to be consumed, show donation now
                    if (purchases == 0)
                        showDonationSelector(dialogItems);
                }
            }
        };


        if (mBillingService != null)
            mBillingService.queryInventoryAsync(true, Arrays
                .asList(iabItems), gotInventoryListener);
    }

    private void showDonationSelector(CharSequence[] dialogItems) {
        final String[] iabItems = getResources().getStringArray(R.array.iab_items);

        // show dialog with choices
        new MaterialDialog.Builder(getActivity())
            .items(dialogItems)
            .itemsCallback(new MaterialDialog.ListCallback() {
                @Override
                public void onSelection(MaterialDialog dialog, View itemView, int position, CharSequence text) {
                    // start the purchase
                    String itemId = iabItems[position];
                    mBillingService.launchPurchaseFlow(getActivity(), itemId,
                        IAB_REQUEST_CODE, mPurchaseFinishedListener);
                }
            })
            .negativeText(android.R.string.cancel)
            .show();
    }

    private void donateGoogle() {
        // progress dialog
        Dialog dialog = new MaterialDialog.Builder(getActivity())
            .content(R.string.msg_connecting_iab)
            .cancelable(true)
            .progress(true, 0)
            .cancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    // FIXME this doesn't seem to work in some cases
                    if (mBillingService != null) {
                        mBillingService.dispose();
                        mBillingService = null;
                    }
                }
            })
            .show();

        setupGoogle(dialog);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mBillingService != null) {
            mBillingService.dispose();
            mBillingService = null;
        }
    }

    private void alert(String message) {
        new MaterialDialog.Builder(getActivity())
            .content(message)
            .positiveText(android.R.string.ok)
            .show();
    }
}

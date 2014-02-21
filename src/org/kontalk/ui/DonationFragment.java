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

import java.util.LinkedList;
import java.util.List;

import org.kontalk.R;
import org.kontalk.util.BitcoinIntegration;
import org.kontalk.util.IabHelper;
import org.kontalk.util.IabResult;
import org.kontalk.util.Inventory;
import org.kontalk.util.Purchase;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;


/**
 * Donation fragment.
 * @author Daniele Ricci
 * @author Andrea Cappelli
 */
public class DonationFragment extends Fragment {
    private Button mDonateBtn;
    private Button mDonateBtn2;
    private Button mDonateBtn3;
    private Button mDonateBtn4;
    static final String TAG = "Kontalk Billing";
    IabHelper mHelper;
    private String[] mItems=new String[3];
    private final List additionalSkuList= new LinkedList();
    static final int RC_REQUEST = 10001;
    private static final int REQUEST_CODE = 0;

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    View view = inflater.inflate(R.layout.about_donation, container, false);
        mDonateBtn = (Button) view.findViewById(R.id.donate1);
        mDonateBtn2 = (Button) view.findViewById(R.id.donate2);
        mDonateBtn3 = (Button) view.findViewById(R.id.donate3);
        mDonateBtn4 = (Button) view.findViewById(R.id.donate4);
        Log.d(TAG, "Creating IAB helper.");
        mHelper = new IabHelper(getActivity(), getString(R.string.gwallet_key));
        mHelper.enableDebugLogging(true);
        Log.d(TAG, "Starting setup.");
        mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
           public void onIabSetupFinished(IabResult result) {
               Log.d(TAG, "Setup finished.");

               if (!result.isSuccess()) {
                   complain("Problem setting up in-app billing: " + result);
                   return;
               }
               if (mHelper == null) return;
               if (result.isSuccess()) init();
               Log.d(TAG, "Setup successful. Querying inventory.");
               additionalSkuList.add("donation_1");
               additionalSkuList.add("donation_2");
               additionalSkuList.add("donation_5");
               mHelper.queryInventoryAsync(true,additionalSkuList,mGotInventoryListener);
          }
        });
        return view;
	}

    void init() {
        mDonateBtn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder donationDialog = new AlertDialog.Builder(getActivity());
                donationDialog.setTitle("Donate");
                donationDialog.setItems(mItems, new DialogInterface.OnClickListener() {
                 public void onClick(DialogInterface dialog, int which) {
                    if (which==0) {
                        if (mHelper !=null) mHelper.flagEndAsync();
                        mHelper.launchPurchaseFlow(getActivity(), "donation_1", RC_REQUEST, mPurchaseFinishedListener);
                    }
                    if (which==1) {
                        if (mHelper !=null) mHelper.flagEndAsync();
                        mHelper.launchPurchaseFlow(getActivity(), "donation_2", RC_REQUEST, mPurchaseFinishedListener);
                    }
                    if (which==2) {
                        if (mHelper !=null) mHelper.flagEndAsync();
                        mHelper.launchPurchaseFlow(getActivity(), "donation_5", RC_REQUEST, mPurchaseFinishedListener);
                    }
                 }
                });
                donationDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                     dialog.dismiss();
                 }
                });
                AlertDialog alert = donationDialog.create();
                alert.show();
             }
        });

        mDonateBtn2.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                BitcoinIntegration.requestForResult(getActivity(), REQUEST_CODE, getString(R.string.bitcoin_address));
            }
        });

        mDonateBtn3.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=NVEF4PQ92HJ6N")));
            }
        });

        mDonateBtn4.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://flattr.com/thing/1194909/Kontalk-network")));
            }
        });

    }
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d(TAG, "Query inventory finished.");
            if (mHelper == null) return;
            if (result.isFailure()) {
                complain("Failed to query inventory: " + result);
                return;
            }
            else {
                Log.d(TAG, "Query inventory was successful.");
                if (inventory.hasPurchase("donation1"))
                {
                mHelper.consumeAsync(inventory.getPurchase("donation_1"), mConsumeFinishedListener);
                }
                if (inventory.hasPurchase("donation2"))
                {
                mHelper.consumeAsync(inventory.getPurchase("donation_2"), mConsumeFinishedListener);
                }
                if (inventory.hasPurchase("donation5"))
                {
                mHelper.consumeAsync(inventory.getPurchase("donation_5"), mConsumeFinishedListener);
                }
            }

            mItems[0]=inventory.getSkuDetails("donation_1").getDescription()+"   "+inventory.getSkuDetails("donation_1").getPrice();
            mItems[1]=inventory.getSkuDetails("donation_2").getDescription()+"   "+inventory.getSkuDetails("donation_2").getPrice();
            mItems[2]=inventory.getSkuDetails("donation_5").getDescription()+"   "+inventory.getSkuDetails("donation_5").getPrice();
        }
    };

    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {

        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            if (mHelper == null) return;

            if (result.isFailure()) {
                Log.d(TAG, "Error purchasing: " + result);
                return;
             }
             Log.d(TAG, "Purchase successful.");
        }
    };

    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            if (mHelper == null) return;

            if (result.isSuccess()) {
                Log.d(TAG, "Consumption successful. Provisioning.");
            }
            else {
                complain("Error while consuming: " + result);
            }
        }
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);
        if (!mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
        else {
            Log.i(TAG, "onActivityResult handled by IABUtil.");
        }
    }

	@Override
	public void onDestroy() {
	    super.onDestroy();
	    if (mHelper != null) mHelper.dispose();
	    mHelper = null;
	}

    void complain(String message) {
        Log.e(TAG, "Billing Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(getActivity());
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }
}

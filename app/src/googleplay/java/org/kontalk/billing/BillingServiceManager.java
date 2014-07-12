package org.kontalk.billing;

import android.content.Context;


/**
 * Billing service singleton container.
 * @author Daniele Ricci
 */
public class BillingServiceManager {

    public static final int BILLING_RESPONSE_RESULT_OK = 0;

    private static IBillingService sInstance;

    private BillingServiceManager() {
    }

    public static IBillingService getInstance(Context context) {
        if (sInstance == null)
            sInstance = new GoogleBillingService(context);

        return sInstance;
    }

    /** Returns true if the billing action should be visible in the UI. */
    public static boolean isEnabled() {
        return true;
    }

    public static String getResponseDesc(int code) {
        return GoogleBillingService.getResponseDesc(code);
    }

}

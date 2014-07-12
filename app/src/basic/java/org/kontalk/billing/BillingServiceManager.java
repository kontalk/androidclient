package org.kontalk.billing;


import android.content.Context;

/**
 * Billing service singleton container.
 * @author Daniele Ricci
 */
public class BillingServiceManager {

    public static final int BILLING_RESPONSE_RESULT_OK = 0;

    private BillingServiceManager() {
    }

    public static IBillingService getInstance(Context context) {
        return null;
    }

    /** Returns true if the billing action should be visible in the UI. */
    public static boolean isEnabled() {
        return false;
    }

    public static String getResponseDesc(int code) {
        return null;
    }

}

package org.kontalk.billing;


/**
 * Callback for setup process. This listener's {@link #onSetupFinished} method
 * is called when the setup process is complete.
 */
public interface OnBillingSetupFinishedListener {

    /**
     * Called to notify that setup is complete.
     *
     * @param result The result of the setup process.
     */
    public void onSetupFinished(BillingResult result);

}

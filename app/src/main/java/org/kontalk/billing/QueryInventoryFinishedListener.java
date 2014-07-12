package org.kontalk.billing;


/**
 * Listener that notifies when an inventory query operation completes.
 */
public interface QueryInventoryFinishedListener {
    /**
     * Called to notify that an inventory query operation completed.
     *
     * @param result The result of the operation.
     * @param inv The inventory.
     */
    public void onQueryInventoryFinished(BillingResult result, IInventory inv);
}

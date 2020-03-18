/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kontalk.billing;

import android.app.Activity;
import android.content.Intent;

import java.util.List;


/**
 * On-device billing service interface.
 * @author Daniele Ricci
 */
public interface IBillingService {

    /**
     * Enables or disable debug logging through LogCat.
     */
    void enableDebugLogging(boolean enable, String tag);

    void enableDebugLogging(boolean enable);

    /**
     * Starts the setup process. This will start up the setup process asynchronously.
     * You will be notified through the listener when the setup process is complete.
     * This method is safe to call from a UI thread.
     *
     * @param listener The listener to notify when the setup process is complete.
     */
    void startSetup(OnBillingSetupFinishedListener listener);

    void launchPurchaseFlow(Activity act, String sku, int requestCode, OnPurchaseFinishedListener listener);

    void launchPurchaseFlow(Activity act, String sku, int requestCode,
        OnPurchaseFinishedListener listener, String extraData);

    void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode,
        OnPurchaseFinishedListener listener);

    void launchSubscriptionPurchaseFlow(Activity act, String sku, int requestCode,
        OnPurchaseFinishedListener listener, String extraData);

    /**
     * Initiate the UI flow for an in-app purchase. Call this method to initiate an in-app purchase,
     * which will involve bringing up the Google Play screen. The calling activity will be paused while
     * the user interacts with Google Play, and the result will be delivered via the activity's
     * {@link android.app.Activity#onActivityResult} method, at which point you must call
     * this object's {@link #handleActivityResult} method to continue the purchase flow. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param act The calling activity.
     * @param sku The sku of the item to purchase.
     * @param itemType indicates if it's a product or a subscription (ITEM_TYPE_INAPP or ITEM_TYPE_SUBS)
     * @param requestCode A request code (to differentiate from other responses --
     *     as in {@link android.app.Activity#startActivityForResult}).
     * @param listener The listener to notify when the purchase process finishes
     * @param extraData Extra data (developer payload), which will be returned with the purchase data
     *     when the purchase completes. This extra data will be permanently bound to that purchase
     *     and will always be returned when the purchase is queried.
     */
    void launchPurchaseFlow(Activity act, String sku, String itemType, int requestCode,
        OnPurchaseFinishedListener listener, String extraData);

    /**
     * Handles an activity result that's part of the purchase flow in in-app billing. If you
     * are calling {@link #launchPurchaseFlow}, then you must call this method from your
     * Activity's {@link android.app.Activity@onActivityResult} method. This method
     * MUST be called from the UI thread of the Activity.
     *
     * @param requestCode The requestCode as you received it.
     * @param resultCode The resultCode as you received it.
     * @param data The data (Intent) as you received it.
     * @return Returns true if the result was related to a purchase flow and was handled;
     *     false if the result was not related to a purchase, in which case you should
     *     handle it normally.
     */
    boolean handleActivityResult(int requestCode, int resultCode, Intent data);

    IInventory queryInventory(boolean querySkuDetails, List<String> moreSkus)
        throws BillingException;

    /**
     * Queries the inventory. This will query all owned items from the server, as well as
     * information on additional skus, if specified. This method may block or take long to execute.
     * Do not call from a UI thread. For that, use the non-blocking version {@link #queryInventoryAsync}.
     *
     * @param querySkuDetails if true, SKU details (price, description, etc) will be queried as well
     *     as purchase information.
     * @param moreItemSkus additional PRODUCT skus to query information on, regardless of ownership.
     *     Ignored if null or if querySkuDetails is false.
     * @param moreSubsSkus additional SUBSCRIPTIONS skus to query information on, regardless of ownership.
     *     Ignored if null or if querySkuDetails is false.
     * @throws BillingException if a problem occurs while refreshing the inventory.
     */
    IInventory queryInventory(boolean querySkuDetails, List<String> moreItemSkus,
        List<String> moreSubsSkus) throws BillingException;

    /**
     * Asynchronous wrapper for inventory query. This will perform an inventory
     * query as described in {@link #queryInventory}, but will do so asynchronously
     * and call back the specified listener upon completion. This method is safe to
     * call from a UI thread.
     *
     * @param querySkuDetails as in {@link #queryInventory}
     * @param moreSkus as in {@link #queryInventory}
     * @param listener The listener to notify when the refresh operation completes.
     */
    void queryInventoryAsync(final boolean querySkuDetails,
        final List<String> moreSkus,
        final QueryInventoryFinishedListener listener);

    void queryInventoryAsync(QueryInventoryFinishedListener listener);

    void queryInventoryAsync(boolean querySkuDetails, QueryInventoryFinishedListener listener);

    void consumeAsync(IPurchase purchase, OnConsumeFinishedListener listener);

    void endAsyncOperation();

    /**
     * Dispose of object, releasing resources. It's very important to call this
     * method when you are done with this object. It will release any resources
     * used by it such as service connections. Naturally, once the object is
     * disposed of, it can't be used again.
     */
    void dispose();

    boolean isDisposed();

}

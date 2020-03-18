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


/**
 * Represents a block of information about in-app items.
 * An Inventory is returned by such methods as {@link IBillingService#queryInventory}.
 */
public interface IInventory {

    /** Returns the listing details for an in-app product. */
    IProductDetails getSkuDetails(String sku);

    /** Returns purchase information for a given product, or null if there is no purchase. */
    IPurchase getPurchase(String sku);

    /** Returns whether or not there exists a purchase of the given product. */
    boolean hasPurchase(String sku);

    /** Return whether or not details about the given product are available. */
    boolean hasDetails(String sku);

    /**
     * Erase a purchase (locally) from the inventory, given its product ID. This just
     * modifies the Inventory object locally and has no effect on the server! This is
     * useful when you have an existing Inventory object which you know to be up to date,
     * and you have just consumed an item successfully, which means that erasing its
     * purchase data from the Inventory you already have is quicker than querying for
     * a new Inventory.
     */
    void erasePurchase(String sku);

}

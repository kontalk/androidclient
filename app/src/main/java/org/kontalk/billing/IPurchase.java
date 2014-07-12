package org.kontalk.billing;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an in-app billing purchase.
 */
public interface IPurchase {

    public String getItemType();
    public String getOrderId();
    public String getPackageName();
    public String getProduct();
    public long getPurchaseTime();
    public int getPurchaseState();
    public String getDeveloperPayload();
    public String getToken();
    public String getOriginalJson();
    public String getSignature();

}

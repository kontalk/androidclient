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

package org.kontalk.billing;

import android.content.Context;

import org.kontalk.BuildConfig;


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
        if (sInstance == null || sInstance.isDisposed())
            sInstance = new GoogleBillingService(context);

        return sInstance;
    }

    /** Returns true if the billing action should be visible in the UI. */
    public static boolean isEnabled() {
        return !BuildConfig.DEBUG;
    }

    public static String getResponseDesc(int code) {
        return GoogleBillingService.getResponseDesc(code);
    }

}

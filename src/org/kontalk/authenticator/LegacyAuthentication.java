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

package org.kontalk.authenticator;

import org.kontalk.service.MessageCenterService;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;


/**
 *
 * @author Daniele Ricci
 * @deprecated This class was born to be deprecated.
 */
@Deprecated
public class LegacyAuthentication {

    // do not instantiate
    private LegacyAuthentication() {}

    /**
     * Upgrades the default account for XMPP.
     * Account password is moved to an account user data attribute.
     */
    public static void doUpgrade(Context context, String name) {
        AccountManager am = AccountManager.get(context);
        Account account = Authenticator.getDefaultAccount(am);

        if (account != null) {
            String token = am.getPassword(account);

            // save auth token for later use
            am.setUserData(account, Authenticator.DATA_AUTHTOKEN, token);

            // save uid name
            am.setUserData(account, Authenticator.DATA_NAME, name);

            // start key pair generation
            MessageCenterService.regenerateKeyPair(context);
        }
    }

    /**
     * Returns the auth token for logging in the first time after upgrading to
     * XMPP.
     */
    public static String getAuthToken(Context context) {
        AccountManager am = AccountManager.get(context);
        Account account = Authenticator.getDefaultAccount(am);

        if (account != null)
            return am.getUserData(account, Authenticator.DATA_AUTHTOKEN);

        return null;
    }

}

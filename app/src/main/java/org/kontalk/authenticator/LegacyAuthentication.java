/*
 * Kontalk Android client
 * Copyright (C) 2017 Kontalk Devteam <devteam@kontalk.org>

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

import org.jivesoftware.smack.util.StringUtils;

import org.kontalk.Kontalk;
import org.kontalk.client.EndpointServer;
import org.kontalk.client.ServerList;
import org.kontalk.service.ServerListUpdater;
import org.kontalk.service.msgcenter.MessageCenterService;

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

    private static boolean sUpgrading;

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
            // start upgrade process
            sUpgrading = true;

            boolean upgraded = (am.getUserData(account, Authenticator.DATA_AUTHTOKEN) != null);
            if (!upgraded) {
                String token = am.getPassword(account);

                // save auth token for later use
                am.setUserData(account, Authenticator.DATA_AUTHTOKEN, token);
            }

            // save uid name
            am.setUserData(account, Authenticator.DATA_NAME, name);

            // set server to first in built-in server list
            ServerList list = ServerListUpdater.getCurrentList(context);
            EndpointServer server = list.get(0);
            am.setUserData(account, Authenticator.DATA_SERVER_URI, server.toString());

            // setup a new passphrase for the upgrade
            String passphrase = StringUtils.randomString(40);
            am.setPassword(account, passphrase);

            // invalidate personal key and passphrase
            Kontalk.get().invalidatePersonalKey();

            // start key pair generation
            MessageCenterService.regenerateKeyPair(context, null);
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

    public static boolean isUpgrading() {
        return sUpgrading;
    }

    public static void endUpgrade() {
        sUpgrading = false;
    }

}

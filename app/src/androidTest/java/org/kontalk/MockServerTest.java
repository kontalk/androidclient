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

package org.kontalk;

import org.junit.Before;

import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.test.InstrumentationRegistry;
import android.util.Base64;

import org.kontalk.authenticator.Authenticator;
import org.kontalk.service.msgcenter.SQLiteRosterStore;
import org.kontalk.util.Preferences;


/**
 * A base test class for using a mock server with a fake account.
 */
public abstract class MockServerTest {
    public static final String MOCK_SERVER_URI = "prime.kontalk.net|__mock__";

    @Before
    public void setUp() throws Exception {
        Preferences.setServerURI(MOCK_SERVER_URI);
    }

    protected static void createFakeAccount(String displayName, String phoneNumber) {
        final android.accounts.Account account = new android.accounts
            .Account(phoneNumber, Authenticator.ACCOUNT_TYPE);

        AccountManager am = (AccountManager) InstrumentationRegistry.getContext()
            .getSystemService(Context.ACCOUNT_SERVICE);

        // account userdata
        Bundle data = new Bundle();
        data.putString(Authenticator.DATA_NAME, displayName);
        data.putString(Authenticator.DATA_SERVER_URI, MOCK_SERVER_URI);

        am.addAccountExplicitly(account, "", data);

        // put data once more (workaround for Android bug http://stackoverflow.com/a/11698139/1045199)
        am.setUserData(account, Authenticator.DATA_NAME, data.getString(Authenticator.DATA_NAME));
        am.setUserData(account, Authenticator.DATA_SERVER_URI, MOCK_SERVER_URI);

        // clear old roster information
        SQLiteRosterStore.purge(InstrumentationRegistry.getTargetContext());
    }

}

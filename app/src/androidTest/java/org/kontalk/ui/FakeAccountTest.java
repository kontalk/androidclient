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

package org.kontalk.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.Manifest;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.IdlingRegistry;
import android.support.test.espresso.IdlingResource;
import android.support.test.filters.LargeTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.GrantPermissionRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Base64;

import org.kontalk.Kontalk;
import org.kontalk.TestServerTest;
import org.kontalk.TestUtils;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.crypto.PersonalKey;
import org.kontalk.service.msgcenter.SQLiteRosterStore;
import org.kontalk.util.ByteArrayInOutStream;
import org.kontalk.util.MessageUtils;

import static org.junit.Assert.*;


/**
 * TODO will spawn become a base class that creates a local account with the built-in key
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class FakeAccountTest extends TestServerTest {
    private static final String TEST_USERNAME = "dev-5554";
    private static final String TEST_USERID = "+15555215554";

    private static final long MAX_KEY_SIZE = 102400; // 100 KB

    @Rule
    public ActivityTestRule<ConversationsActivity> mActivityTestRule =
        new ActivityTestRule<>(ConversationsActivity.class);

    @Rule
    public GrantPermissionRule mPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA);

    @BeforeClass
    public static void setUpAccount() throws Exception {
        createFakeAccount(TEST_USERNAME, TEST_USERID);
        Kontalk.setServicesEnabled(InstrumentationRegistry.getTargetContext(), true);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.removeDefaultAccount();
        Collection<IdlingResource> idlingResourceList = IdlingRegistry.getInstance().getResources();
        for (IdlingResource resource : idlingResourceList) {
            IdlingRegistry.getInstance().unregister(resource);
        }
    }

    @Test
    public void fakeAccountTest() throws Exception {
        // we should have an account now
        TestUtils.assertDefaultAccountExists();

        PersonalKey key = Kontalk.get().getPersonalKey();
        assertNotNull(key);
    }

    private static void createFakeAccount(String displayName, String phoneNumber) throws IOException, InterruptedException {
        TestUtils.removeDefaultAccount();

        InputStream privateInput = InstrumentationRegistry.getContext().getAssets().open("keys/private.key");
        InputStream publicInput = InstrumentationRegistry.getContext().getAssets().open("keys/public.key");
        InputStream bridgeCertInput = InstrumentationRegistry.getContext().getAssets().open("keys/login.crt");

        ByteArrayInOutStream privateKeyData = MessageUtils.readFully(privateInput, MAX_KEY_SIZE);
        ByteArrayInOutStream publicKeyData = MessageUtils.readFully(publicInput, MAX_KEY_SIZE);
        ByteArrayInOutStream bridgeCertData = MessageUtils.readFully(bridgeCertInput, MAX_KEY_SIZE);

        privateInput.close();
        publicInput.close();
        bridgeCertInput.close();

        final android.accounts.Account account = new android.accounts
            .Account(phoneNumber, Authenticator.ACCOUNT_TYPE);

        AccountManager am = (AccountManager) InstrumentationRegistry.getContext()
            .getSystemService(Context.ACCOUNT_SERVICE);

        // account userdata
        Bundle data = new Bundle();
        data.putString(Authenticator.DATA_PRIVATEKEY, Base64
            .encodeToString(privateKeyData.toByteArray(), Base64.NO_WRAP));
        data.putString(Authenticator.DATA_PUBLICKEY, Base64
            .encodeToString(publicKeyData.toByteArray(), Base64.NO_WRAP));
        data.putString(Authenticator.DATA_BRIDGECERT, Base64
            .encodeToString(bridgeCertData.toByteArray(), Base64.NO_WRAP));
        data.putString(Authenticator.DATA_NAME, displayName);
        data.putString(Authenticator.DATA_SERVER_URI, TEST_SERVER_URI);

        am.addAccountExplicitly(account, "integration", data);

        // put data once more (workaround for Android bug http://stackoverflow.com/a/11698139/1045199)
        am.setUserData(account, Authenticator.DATA_PRIVATEKEY, data.getString(Authenticator.DATA_PRIVATEKEY));
        am.setUserData(account, Authenticator.DATA_PUBLICKEY, data.getString(Authenticator.DATA_PUBLICKEY));
        am.setUserData(account, Authenticator.DATA_BRIDGECERT, data.getString(Authenticator.DATA_BRIDGECERT));
        am.setUserData(account, Authenticator.DATA_NAME, data.getString(Authenticator.DATA_NAME));
        am.setUserData(account, Authenticator.DATA_SERVER_URI, TEST_SERVER_URI);

        // clear old roster information
        SQLiteRosterStore.purge(InstrumentationRegistry.getTargetContext());
    }

}

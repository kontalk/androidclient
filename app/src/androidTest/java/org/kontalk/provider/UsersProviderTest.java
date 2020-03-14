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

package org.kontalk.provider;

import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jxmpp.util.XmppStringUtils;
import org.kontalk.util.XMPPUtils;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKeyRing;

import android.annotation.TargetApi;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.test.ProviderTestCase2;
import android.util.Base64;

import org.kontalk.crypto.PGP;


@RunWith(AndroidJUnit4.class)
public class UsersProviderTest extends ProviderTestCase2<UsersProvider> {

    private static final String TEST_USERID = XmppStringUtils
        .completeJidFrom(XMPPUtils.createLocalpart("+15555215554"), "prime.kontalk.net");
    private static final String TEST_KEYDATA =
        "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\n" +
        "eiy71AHnPDvE1l4=";

    public UsersProviderTest() {
        super(UsersProvider.class, UsersProvider.AUTHORITY);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        setContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSetup() {
        try {
            assertQuery(MyUsers.Users.CONTENT_URI);
        }
        catch (UnsupportedOperationException e) {
            // tried to start sync with no account. All good!
        }
        catch (NullPointerException e) {
            // tried to start sync with no account. All good! (for API 21)
        }
        assertQuery(MyUsers.Keys.CONTENT_URI);
    }

    @Test
    public void testAutotrustedLevel() throws IOException, PGPException {
        Keyring.setAutoTrustLevel(getMockContext(), TEST_USERID, MyUsers.Keys.TRUST_VERIFIED);
        assertQueryValues(MyUsers.Keys.getUri(TEST_USERID, Keyring.VALUE_AUTOTRUST),
            MyUsers.Keys.JID, TEST_USERID,
            MyUsers.Keys.FINGERPRINT, Keyring.VALUE_AUTOTRUST);

        byte[] keydata = Base64.decode(TEST_KEYDATA, Base64.DEFAULT);
        PGPPublicKeyRing originalKey = PGP.readPublicKeyring(keydata);
        Keyring.setKey(getMockContext(), TEST_USERID, keydata);
        PGPPublicKeyRing publicKey = Keyring.getPublicKey(getMockContext(), TEST_USERID, MyUsers.Keys.TRUST_VERIFIED);
        assertNotNull(publicKey);
        assertTrue(Arrays.equals(publicKey.getEncoded(), originalKey.getEncoded()));

        String testFingerprint = PGP.getFingerprint(originalKey.getPublicKey());
        getMockContentResolver().delete(MyUsers.Keys.getUri(TEST_USERID, testFingerprint), null, null);
    }

    private void assertQuery(Uri uri) {
        Cursor c = getMockContentResolver().query(uri, null, null, null, null);
        assertNotNull(c);
        c.close();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void assertQueryValues(Uri uri, String... columnsExpected) {
        String[] columns = new String[columnsExpected.length / 2];
        for (int i = 0; i < columns.length; i++)
            columns[i] = columnsExpected[i*2];

        Cursor c = getMockContentResolver().query(uri, columns, null, null, null);
        assertNotNull(c);
        assertTrue(c.moveToFirst());

        for (int i = 0; i < columns.length; i++) {
            String expected = columnsExpected[i*2+1];
            String actual;
            if (c.getType(i) == Cursor.FIELD_TYPE_BLOB) {
                actual = new String(c.getBlob(i));
            }
            else {
                actual = c.getString(i);
            }
            assertEquals(expected, actual);
        }

        c.close();
    }

}

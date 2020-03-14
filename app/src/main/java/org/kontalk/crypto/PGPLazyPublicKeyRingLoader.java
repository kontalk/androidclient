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

package org.kontalk.crypto;

import java.io.IOException;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;


/**
 * PGP public keyring that loads its data only when first requested.
 * @author Daniele
 */
public class PGPLazyPublicKeyRingLoader {

    private byte[] mData;

    private PGPPublicKeyRing mKeyRing;
    private String mFingerprint;

    public PGPLazyPublicKeyRingLoader(byte[] encoding) {
        mData = encoding;
    }

    public PGPPublicKeyRing getPublicKeyRing() throws PGPException, IOException {
        if (mKeyRing == null) {
            mKeyRing = PGP.readPublicKeyring(mData);
            // we don't need input data anymore
            mData = null;
        }
        return mKeyRing;
    }

    public String getFingerprint() throws PGPException, IOException {
        if (mFingerprint == null) {
            PGPPublicKeyRing key = getPublicKeyRing();
            if (key != null) {
                PGPPublicKey pk = PGP.getMasterKey(key);
                if (pk != null)
                    mFingerprint = PGP.getFingerprint(pk);
            }
        }
        return mFingerprint;
    }

}

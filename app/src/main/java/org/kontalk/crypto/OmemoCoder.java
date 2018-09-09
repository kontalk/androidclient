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

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.List;


/**
 * OMEMO coder implementation.
 * @author Daniele Ricci
 */
public class OmemoCoder extends Coder {

    public OmemoCoder() {
        // TODO
    }

    @Override
    public byte[] encryptText(CharSequence text) throws GeneralSecurityException {
        return new byte[0];
    }

    @Override
    public byte[] encryptStanza(CharSequence xml) throws GeneralSecurityException {
        return new byte[0];
    }

    @Override
    public DecryptOutput decryptText(byte[] encrypted, boolean verify) throws GeneralSecurityException {
        return null;
    }

    @Override
    public void encryptFile(InputStream input, OutputStream output) throws GeneralSecurityException {

    }

    @Override
    public void decryptFile(InputStream input, boolean verify, OutputStream output, List<DecryptException> errors) throws GeneralSecurityException {

    }

    @Override
    public VerifyOutput verifyText(byte[] signed, boolean verify) throws GeneralSecurityException {
        return null;
    }
}

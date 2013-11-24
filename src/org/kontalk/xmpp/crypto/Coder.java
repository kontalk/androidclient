/*
 * Kontalk Android client
 * Copyright (C) 2013 Kontalk Devteam <devteam@kontalk.org>

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
package org.kontalk.xmpp.crypto;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;


/**
 * Generic coder interface.
 * @author Daniele Ricci
 */
public interface Coder {

    public byte[] encrypt(byte[] unencrypted) throws GeneralSecurityException;

    public byte[] decrypt(byte[] encrypted) throws GeneralSecurityException;

    public InputStream wrapInputStream(InputStream inputStream) throws GeneralSecurityException;

    public OutputStream wrapOutputStream(OutputStream outputStream) throws GeneralSecurityException;

    public long getEncryptedLength(long decryptedLength);

}

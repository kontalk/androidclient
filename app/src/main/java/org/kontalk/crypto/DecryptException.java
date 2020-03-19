/*
 * Kontalk Android client
 * Copyright (C) 2020 Kontalk Devteam <devteam@kontalk.org>

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

import java.security.GeneralSecurityException;


/**
 * Exception during decryption/verification.
 * @author Daniele Ricci
 */
public class DecryptException extends GeneralSecurityException {

    private static final long serialVersionUID = -3210114563479741397L;

    /** Signature verification failed. */
    public static final int DECRYPT_EXCEPTION_VERIFICATION_FAILED = 1;
    /** Generic (unknown) decryption failure. */
    public static final int DECRYPT_EXCEPTION_DECRYPT_FAILED = 2;
    /** Private key needed for decryption was not found. */
    public static final int DECRYPT_EXCEPTION_PRIVATE_KEY_NOT_FOUND = 3;
    /** Message sender doesn't match signature. */
    public static final int DECRYPT_EXCEPTION_INVALID_SENDER = 4;
    /** Message recipient doesn't match encryption key. */
    public static final int DECRYPT_EXCEPTION_INVALID_RECIPIENT = 5;
    /** Invalid or unparsable timestamp. */
    public static final int DECRYPT_EXCEPTION_INVALID_TIMESTAMP = 6;
    /** Invalid packet data. */
    public static final int DECRYPT_EXCEPTION_INVALID_DATA = 7;
    /** Message integrity check failed. */
    public static final int DECRYPT_EXCEPTION_INTEGRITY_CHECK = 8;

    private final int mCode;

    public DecryptException(int code) {
        super();
        mCode = code;
    }

    public DecryptException(int code, String message) {
        super(message);
        mCode = code;
    }

    public DecryptException(int code, Throwable cause) {
        super(cause);
        mCode = code;
    }

    public DecryptException(int code, Throwable cause, String message) {
        super(message, cause);
        mCode = code;
    }

    public int getCode() {
        return mCode;
    }

}

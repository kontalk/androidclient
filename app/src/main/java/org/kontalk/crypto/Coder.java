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

import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.smack.packet.Message;


/**
 * Generic coder interface.
 * @author Daniele Ricci
 */
public abstract class Coder {

    /*
     * Security flags for encryption features.
     * These flags marks only features found in a message.
     */

    /** Cleartext messages. Not encrypted nor signed. */
    public static final int SECURITY_CLEARTEXT = 0;
    /** Legacy (2.x) encryption method. For compatibility with old messages. */
    @Deprecated
    public static final int SECURITY_LEGACY_ENCRYPTED = 1;
    /** Basic encryption (e.g. PGP encrypted). Safe enough. */
    public static final int SECURITY_BASIC_ENCRYPTED = 1 << 1;
    /** Basic signature found (e.g. PGP signature). Safe enough. */
    public static final int SECURITY_BASIC_SIGNED = 1 << 2;
    /** Advanced encryption (e.g. OTR). Very strong. */
    public static final int SECURITY_ADVANCED_ENCRYPTED = 1 << 3;
    /** Advanced signature found (e.g. OTR). Very strong. */
    public static final int SECURITY_ADVANCED_SIGNED = 1 << 4;

    /*
     * Security flags for encryption status.
     * These flags, together with the ones above, help clarifying whether the
     * security features found in a message are verified and safe.
     */

    /** Digital signature verification failed. */
    public static final int SECURITY_ERROR_INVALID_SIGNATURE = 1 << 16;
    /** Invalid sender. */
    public static final int SECURITY_ERROR_INVALID_SENDER = 1 << 17;
    /** Invalid recipient. */
    public static final int SECURITY_ERROR_INVALID_RECIPIENT = 1 << 18;
    /** Invalid timestamp. */
    public static final int SECURITY_ERROR_INVALID_TIMESTAMP = 1 << 19;
    /** Invalid packet data or message parsing failed. */
    public static final int SECURITY_ERROR_INVALID_DATA = 1 << 20;
    /** Decryption failed. */
    public static final int SECURITY_ERROR_DECRYPT_FAILED = 1 << 21;
    /** Data integrity check failed. */
    public static final int SECURITY_ERROR_INTEGRITY_CHECK = 1 << 22;
    /** User's public key not available (for encryption and/or verification). */
    public static final int SECURITY_ERROR_PUBLIC_KEY_UNAVAILABLE = 1 << 23;

    /* Quick flags combinations. */

    /** Basic encryption (e.g. PGP). */
    public static final int SECURITY_BASIC = SECURITY_BASIC_ENCRYPTED | SECURITY_BASIC_SIGNED;

    /** Advanced encryption (e.g. OTR). */
    public static final int SECURITY_ADVANCED = SECURITY_ADVANCED_ENCRYPTED | SECURITY_ADVANCED_SIGNED;

    /** How much time to consider a message timestamp drifted (and thus compromised). */
    public static final long TIMEDIFF_THRESHOLD = TimeUnit.DAYS.toMillis(1);

    /** Returns the supported security flags for the coder. */
    public abstract int getSupportedFlags();

    /** Encrypts a message stanza. */
    public abstract Message encryptMessage(Message message, String placeholder) throws GeneralSecurityException;

    /** Decrypts a message stanza. */
    public abstract DecryptOutput decryptMessage(Message message, boolean verify)
        throws GeneralSecurityException;

    /** Encrypts a file. */
    public abstract void encryptFile(InputStream input, OutputStream output) throws GeneralSecurityException;

    /** Decrypts a file. */
    public abstract void decryptFile(InputStream input, boolean verify,
        OutputStream output, List<DecryptException> errors) throws GeneralSecurityException;

    /** Verifies a byte array which should contain text. */
    public abstract Coder.VerifyOutput verifyText(byte[] signed, boolean verify) throws GeneralSecurityException;

    /** Returns true if the given security flags has some error bit on. */
    public static boolean isError(int securityFlags) {
        return (securityFlags & SECURITY_ERROR_INVALID_SIGNATURE) != 0 ||
            (securityFlags & SECURITY_ERROR_INVALID_SENDER) != 0 ||
            (securityFlags & SECURITY_ERROR_INVALID_RECIPIENT) != 0 ||
            (securityFlags & SECURITY_ERROR_INVALID_TIMESTAMP) != 0 ||
            (securityFlags & SECURITY_ERROR_INVALID_DATA) != 0 ||
            (securityFlags & SECURITY_ERROR_DECRYPT_FAILED) != 0 ||
            (securityFlags & SECURITY_ERROR_INTEGRITY_CHECK) != 0 ||
            (securityFlags & SECURITY_ERROR_PUBLIC_KEY_UNAVAILABLE) != 0;
    }

    /**
     * Calculate the common security flags supported by all given users flags.
     * @param requestedFlags what flags we would like
     * @param supportedFlags what flags users support
     * @return calculated flags
     */
    public static int getCompatibleSecurityFlags(int requestedFlags, int[] supportedFlags) {
        // FIXME not really checking flags, but I know what I'm doing here and also I'm lazy
        int finalFlags = -1;
        for (int flags : supportedFlags) {
            if (flags < 0) {
                // unknown security, just skip it
                continue;
            }
            int calculatedFlags = getCompatibleSecurityFlags(requestedFlags, flags);
            if (finalFlags >= 0) {
                finalFlags = Math.min(finalFlags, calculatedFlags);
            }
            else {
                finalFlags = calculatedFlags;
            }
        }
        return finalFlags < 0 ? requestedFlags : finalFlags;
    }

    /**
     * Calculate the security flags supported by the given users flags.
     * @param requestedFlags what flags we would like
     * @param supportedFlags what flags the user supports
     * @return calculated flags
     */
    public static int getCompatibleSecurityFlags(int requestedFlags, int supportedFlags) {
        // FIXME not really checking flags, but I know what I'm doing here and also I'm lazy
        return Math.min(requestedFlags, supportedFlags);
    }

    public static class DecryptOutput {
        public final String mime;
        public final Message cleartext;
        public final Date timestamp;
        public final int securityFlags;
        public final List<DecryptException> errors;

        DecryptOutput(Message cleartext, String mime, Date timestamp, int securityFlags, List<DecryptException> errors) {
            this.cleartext = cleartext;
            this.mime = mime;
            this.timestamp = timestamp;
            this.securityFlags = securityFlags;
            this.errors = errors;
        }
    }

    public static class VerifyOutput {
        public final String cleartext;
        public final Date timestamp;
        public final List<VerifyException> errors;

        VerifyOutput(String cleartext, Date timestamp, List<VerifyException> errors) {
            this.cleartext = cleartext;
            this.timestamp = timestamp;
            this.errors = errors;
        }

    }

}

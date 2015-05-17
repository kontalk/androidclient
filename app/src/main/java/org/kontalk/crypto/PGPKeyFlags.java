package org.kontalk.crypto;


/**
 * PGP key flags not supported by Bouncy Castle.
 * @author Daniele
 */
public class PGPKeyFlags implements org.spongycastle.openpgp.PGPKeyFlags {

    public static final int CAN_AUTHENTICATE = 0x20; // This key may be used for authentication.

}

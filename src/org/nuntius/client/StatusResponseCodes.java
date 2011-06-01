package org.nuntius.client;


/**
 * Contains all the status response codes.
 * @author Daniele Ricci
 * @version 1.0
 */
public interface StatusResponseCodes {

    /** Operation completed successfully. */
    public static final int STATUS_SUCCESS = 0;

    /** Generic error occured. */
    public static final int STATUS_ERROR = 1;

    /** Server is busy - try again later. */
    public static final int STATUS_BUSY = 2;

    /** Code verification failed. */
    public static final int STATUS_VERIFICATION_FAILED = 3;

    /** Invalid phone number. */
    public static final int STATUS_INVALID_PHONE_NUMBER = 4;

    /** Message TTL expired. */
    public static final int STATUS_TTL_EXPIRED = 5;
}
